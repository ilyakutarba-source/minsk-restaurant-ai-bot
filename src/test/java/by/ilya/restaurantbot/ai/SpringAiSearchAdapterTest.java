package by.ilya.restaurantbot.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import by.ilya.restaurantbot.search.RestaurantSearchService;
import by.ilya.restaurantbot.search.SearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import static by.ilya.restaurantbot.ai.AiFixtures.*;
import static by.ilya.restaurantbot.ai.AiSearchReply.Status.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SpringAiSearchAdapterTest {
    ChatModel model;
    RestaurantSearchService service;
    SpringAiSearchAdapter adapter;

    @BeforeEach
    void setup() {
        model = mock(ChatModel.class);
        service = mock(RestaurantSearchService.class);
        adapter = new SpringAiSearchAdapter(model, service, CLOCK, true);
        when(service.search(any())).thenReturn(result());
    }

    @Test
    void fullCriteriaWithoutMemoryReachesExistingServiceAndOnlyJavaRendersFacts() throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(selection(FULL), explanation(PLAN));
        String user = "Сегодня в 21:00 нас двое, бюджет 150 BYN, хочется итальянскую кухню и спокойно.";
        var reply = adapter.search(user);
        assertThat(reply.status()).isEqualTo(OK);
        assertThat(reply.modelCalls()).isEqualTo(2);
        assertThat(reply.toolExecutions()).isEqualTo(1);
        assertThat(reply.explanationFallback()).isFalse();
        assertThat(reply.text()).contains("Synthetic fixture restaurant", "ID: 42", "Synthetic fixture address",
                "65.40 BYN", "32.70 BYN", "2026-10-02", "21:00", "https://example.test/check", "Может подойти:")
                .doesNotContain("ignored model prose");
        var request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(service).search(request.capture());
        assertThat(request.getValue()).isEqualTo(AiJson.mapper().readValue(FULL, SearchRequest.class));
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().getFirst().getContents()).contains(user, "2026-10-02");
        var firstOptions = (OpenAiChatOptions) prompts.getAllValues().getFirst().getOptions();
        assertThat(firstOptions.getInternalToolExecutionEnabled()).isFalse();
        assertThat(firstOptions.getParallelToolCalls()).isFalse();
        assertThat(firstOptions.getToolCallbacks()).extracting(t -> t.getToolDefinition().name())
                .containsExactly("searchRestaurants", "getRestaurantDetails", "getRestaurantMenu");
        var secondOptions = (OpenAiChatOptions) prompts.getAllValues().getLast().getOptions();
        assertThat(secondOptions.getInternalToolExecutionEnabled()).isFalse();
        assertThat(secondOptions.getToolCallbacks()).isEmpty();
        assertThat(secondOptions.getToolNames()).isEmpty();
        assertThat(secondOptions.getTools()).isEmpty();
        assertThat(secondOptions.getToolChoice()).isEqualTo("none");
        assertThat(prompts.getAllValues().getLast().getContents())
                .contains("allowedReasonCodes", "QUIET_TAG_MATCH")
                .doesNotContain("Synthetic fixture", "https://", "65.40", "restaurantId", "ignored model prose", user);
    }

    @Test
    void multipleAndUnknownRequestsAreRejectedBeforeAnyServiceExecution() {
        for (var calls : List.of(List.of(tool("searchRestaurants", FULL), tool("searchRestaurants", FULL)),
                List.of(tool("searchRestaurants", FULL), tool("getRestaurantMenu", "{}")),
                List.of(tool("getRestaurantDetails", "{}")), List.of(tool("sql", "{}")))) {
            when(model.call(any(Prompt.class))).thenReturn(response("", "tool_calls", calls));
            var reply = adapter.search("Найди ресторан");
            assertThat(reply.status()).isEqualTo(INVALID_INPUT);
            assertThat(reply.modelCalls()).isEqualTo(1);
            assertThat(reply.toolExecutions()).isZero();
        }
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"broken json", "[]", "null", "{} {}",
        "{\"guests\":2.1}", "{\"guests\":\"2\"}", "{\"guests\":0}", "{\"guests\":7}",
        "{\"totalBudgetByn\":0}", "{\"totalBudgetByn\":-1}", "{\"totalBudgetByn\":1.001}",
        "{\"totalBudgetByn\":0.1000000000000000001}", "{\"totalBudgetByn\":150.000}",
        "{\"totalBudgetByn\":1e1000000000}",
        "{\"totalBudgetByn\":\"150\"}", "{\"cuisine\":\"JAPANESE\"}",
        "{\"date\":\"2026-10-01\"}", "{\"date\":\"2026-10-09\"}", "{\"date\":\"2026-02-30\"}",
        "{\"time\":\"9:00\"}", "{\"time\":\"25:00\"}", "{\"time\":900}",
        "{\"preferredTags\":[\"UNKNOWN\"]}", "{\"preferredTags\":[null]}",
        "{\"preferredTags\":[\"QUIET\",\"QUIET\",\"QUIET\",\"QUIET\",\"QUIET\",\"QUIET\",\"QUIET\"]}",
        "{\"restaurantId\":1}", "{\"requests\":[{},{}]}", "{\"guests\":2,\"guests\":3}"})
    void invalidArgumentsNeverReachBusinessLogic(String arguments) {
        when(model.call(any(Prompt.class))).thenReturn(selection(arguments));
        var reply = adapter.search("Запрос");
        assertThat(reply.status()).isEqualTo(INVALID_INPUT);
        assertThat(reply.toolExecutions()).isZero();
        assertThat(reply.modelCalls()).isEqualTo(1);
        verifyNoInteractions(service);
    }

    @Test
    void missingCriteriaNeedsClarificationAndDoesNotUsePriorTurns() {
        when(model.call(any(Prompt.class))).thenReturn(selection("{\"guests\":2,\"preferredTags\":[\"QUIET\"]}"));
        var reply = adapter.search("Нас двое, хочется спокойно");
        assertThat(reply.status()).isEqualTo(NEED_CLARIFICATION);
        assertThat(reply.missingFields()).containsExactly("totalBudgetByn", "date", "time");
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"broken", "", "null", "{}", "{\"items\":[]}",
        "{\"items\":[{\"position\":1,\"reasonCodes\":[\"RATING_MATCH\"],\"phrasing\":\"WARM\"}]}",
        "{\"items\":[{\"position\":2,\"reasonCodes\":[\"BUDGET_MATCH\"],\"phrasing\":\"WARM\"}]}",
        "{\"items\":[{\"position\":1,\"reasonCodes\":[\"COZY_TAG_MATCH\"],\"phrasing\":\"WARM\"}]}",
        "{\"items\":[{\"position\":1,\"reasonCodes\":[\"BUDGET_MATCH\"],\"phrasing\":\"WARM\",\"name\":\"invented\"}]}",
        "{\"items\":[{\"position\":1,\"reasonCodes\":[\"BUDGET_MATCH\",\"BUDGET_MATCH\"],\"phrasing\":\"WARM\"}]}",
        "{\"items\":[{\"position\":1.1,\"reasonCodes\":[\"BUDGET_MATCH\"],\"phrasing\":\"WARM\"}]}",
        "{\"items\":[{\"position\":1,\"reasonCodes\":[null],\"phrasing\":\"WARM\"}]}",
        "{\"items\":[{\"position\":1,\"reasonCodes\":[],\"phrasing\":null}]}"})
    void invalidExplanationKeepsExactSearchResultAndJavaFallback(String plan) {
        when(model.call(any(Prompt.class))).thenReturn(selection(FULL), explanation(plan));
        var reply = adapter.search("Полный запрос");
        assertThat(reply.status()).isEqualTo(OK);
        assertThat(reply.searchResult()).isEqualTo(result());
        assertThat(reply.explanationFallback()).isTrue();
        assertThat(reply.text()).isEqualTo(new SearchFactualRenderer().render(result(), null));
        verify(service, times(1)).search(any());
        verify(model, times(2)).call(any(Prompt.class));
    }

    @Test
    void explanationToolRequestIsNotExecutedAndFallsBack() {
        when(model.call(any(Prompt.class))).thenReturn(selection(FULL), selection(FULL));
        var reply = adapter.search("Полный запрос");
        assertThat(reply.explanationFallback()).isTrue();
        assertThat(reply.modelCalls()).isEqualTo(2);
        assertThat(reply.toolExecutions()).isEqualTo(1);
        verify(service, times(1)).search(any());
    }

    @Test
    void failedExplanationDoesNotRetryOrBreakSearch() {
        when(model.call(any(Prompt.class))).thenReturn(selection(FULL)).thenThrow(new RuntimeException("private payload"));
        var reply = adapter.search("Полный запрос");
        assertThat(reply.status()).isEqualTo(OK);
        assertThat(reply.explanationFallback()).isTrue();
        assertThat(reply.text()).doesNotContain("private payload");
        verify(model, times(2)).call(any(Prompt.class));
        verify(service).search(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"getRestaurantMenu", "getRestaurantDetails"})
    void secondModelResponseCannotExecuteEitherNewTool(String name) {
        var resolver = mock(by.ilya.restaurantbot.conversation.ReferenceResolver.class);
        var menus = mock(by.ilya.restaurantbot.catalog.MenuService.class);
        var catalog = mock(by.ilya.restaurantbot.catalog.RestaurantService.class);
        var complete = new SpringAiSearchAdapter(model, service, CLOCK, true, resolver, menus, catalog);
        when(model.call(any(Prompt.class))).thenReturn(selection(FULL),
                response("invented facts", "TOOL_CALLS", List.of(tool(name, "{\"reference\":{\"ordinal\":1}}"))));
        var reply = complete.turn(81L, "Полный запрос", List.of(), java.util.function.UnaryOperator.identity(), List.of());
        assertThat(reply.status()).isEqualTo(OK);
        assertThat(reply.modelCalls()).isEqualTo(2);
        assertThat(reply.toolExecutions()).isEqualTo(1);
        assertThat(reply.followUpResult()).isNull();
        assertThat(reply.explanationFallback()).isTrue();
        assertThat(reply.text()).isEqualTo(new SearchFactualRenderer().render(result(), null));
        verifyNoInteractions(resolver, menus, catalog);
        verify(service).search(any());
        verify(model, times(2)).call(any(Prompt.class));
    }

    @Test
    void firstProviderAndServiceFailuresAreControlledAndConsumeAttempts() {
        when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("private payload"));
        var failedModel = adapter.search("Запрос");
        assertThat(failedModel.status()).isEqualTo(TEMPORARILY_UNAVAILABLE);
        assertThat(failedModel.modelCalls()).isEqualTo(1);
        assertThat(failedModel.toolExecutions()).isZero();
        verifyNoInteractions(service);
        when(model.call(any(Prompt.class))).thenReturn(selection(FULL));
        when(service.search(any())).thenThrow(new RuntimeException("SQL private payload"));
        var failedService = adapter.search("Запрос");
        assertThat(failedService.status()).isEqualTo(TEMPORARILY_UNAVAILABLE);
        assertThat(failedService.toolExecutions()).isEqualTo(1);
        assertThat(failedService.modelCalls()).isEqualTo(1);
        assertThat(failedService.text()).doesNotContain("private", "SQL");
    }

    @Test
    void refusalAndTruncationAreControlledAtBothStages() {
        for (var invalid : List.of(response(PLAN, "length", List.of()),
                response(PLAN, "stop", List.of(), "refused"), new org.springframework.ai.chat.model.ChatResponse(List.of()))) {
            when(model.call(any(Prompt.class))).thenReturn(invalid);
            assertThat(adapter.search("Запрос").toolExecutions()).isZero();
            when(model.call(any(Prompt.class))).thenReturn(selection(FULL), invalid);
            assertThat(adapter.search("Запрос").explanationFallback()).isTrue();
        }
    }

    @Test
    void noResultsDoesNotRelaxConstraintsOrCallExplanation() {
        when(model.call(any(Prompt.class))).thenReturn(selection(FULL));
        var empty = new by.ilya.restaurantbot.search.SearchResult(result().normalizedCriteria(), "BYN", List.of(), result().warnings());
        when(service.search(any())).thenReturn(empty);
        var reply = adapter.search("Полный запрос");
        assertThat(reply.status()).isEqualTo(NO_RESULTS);
        assertThat(reply.text()).contains("нет ресторанов");
        assertThat(reply.modelCalls()).isEqualTo(1);
        verify(service).search(any());
        verify(model).call(any(Prompt.class));
    }

    @Test
    void explanationCanBeDisabledAndToolCannotBeExecutedTwice() {
        when(model.call(any(Prompt.class))).thenReturn(selection(FULL));
        var reply = new SpringAiSearchAdapter(model, service, CLOCK, false).search("Полный запрос");
        assertThat(reply.modelCalls()).isEqualTo(1);
        assertThat(reply.explanationFallback()).isTrue();
        var tool = new SearchRestaurantsTool(service, CLOCK);
        tool.call(FULL);
        tool.call(FULL);
        assertThat(tool.executions()).isEqualTo(1);
        verify(service, times(2)).search(any()); // One in the adapter turn, one in the separate tool fixture.
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void invalidUserInputDoesNotCallTheModel(String text) {
        assertThat(adapter.search(text).modelCalls()).isZero();
        assertThat(adapter.search("x".repeat(2001)).modelCalls()).isZero();
        verify(model, never()).call(any(Prompt.class));
        verifyNoInteractions(service);
    }

    @Test
    void aiPackageHasNoRepositoryEntitySqlOrMemoryDependencies() throws Exception {
        try (var files = Files.list(Path.of("src/main/java/by/ilya/restaurantbot/ai"))) {
            for (var file : files.toList()) {
                assertThat(Files.readString(file)).doesNotContain("RestaurantRepository", "MenuItemRepository",
                        "EntityManager", "jakarta.persistence", "java.sql", "JdbcTemplate", "ChatMemory",
                        "ConversationState", "SelectionItem");
            }
        }
    }

    @Test
    void rendererKeepsCandidateOrderEvenWhenExplanationItemsAreReversed() {
        var first = result().candidates().getFirst();
        var original = first.restaurant();
        var second = new by.ilya.restaurantbot.catalog.RestaurantDetails(99L, "Second synthetic restaurant",
                "Second synthetic address", true, original.cuisines(), original.tags(),
                original.catalogSource(), original.catalogVerifiedAt(), original.estimatedCheckPerGuest(),
                original.checkEstimationType(), original.checkSource(), original.checkVerifiedAt(),
                original.hoursSource(), original.hoursVerifiedAt(), original.openingIntervals());
        var search = new by.ilya.restaurantbot.search.SearchResult(result().normalizedCriteria(), "BYN",
                List.of(first, new by.ilya.restaurantbot.search.SearchResult.Candidate(second,
                    first.estimatedTotalByn(), first.matchCount(), first.allowedReasonCodes())), result().warnings());
        var item1 = new ExplanationPlan.Item(1, List.of(by.ilya.restaurantbot.search.SearchResult.ReasonCode.BUDGET_MATCH),
                ExplanationPlan.Phrasing.NEUTRAL);
        var item2 = new ExplanationPlan.Item(2, List.of(by.ilya.restaurantbot.search.SearchResult.ReasonCode.HOURS_MATCH),
                ExplanationPlan.Phrasing.COMPACT);
        var reversed = new ExplanationPlan(List.of(item2, item1));
        assertThat(reversed.isValidFor(search)).isTrue();
        var text = new SearchFactualRenderer().render(search, reversed);
        assertThat(text.indexOf("ID: 42")).isLessThan(text.indexOf("ID: 99"));
        assertThat(new ExplanationPlan(List.of(item1, item1)).isValidFor(search)).isFalse();
        assertThat(new ExplanationPlan(List.of(item1)).isValidFor(search)).isFalse();
        assertThat(new SearchFactualRenderer().render(search, new ExplanationPlan(List.of(item1, item1))))
                .isEqualTo(new SearchFactualRenderer().render(search, null));
    }
}

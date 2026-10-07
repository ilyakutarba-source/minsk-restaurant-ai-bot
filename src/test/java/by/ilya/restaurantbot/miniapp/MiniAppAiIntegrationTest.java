package by.ilya.restaurantbot.miniapp;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import by.ilya.restaurantbot.ai.SpringAiSearchAdapter;
import by.ilya.restaurantbot.catalog.Cuisine;
import by.ilya.restaurantbot.catalog.MenuService;
import by.ilya.restaurantbot.catalog.RestaurantService;
import by.ilya.restaurantbot.catalog.RestaurantTag;
import by.ilya.restaurantbot.conversation.ReferenceResolver;
import by.ilya.restaurantbot.search.RestaurantSearchService;
import by.ilya.restaurantbot.search.SearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real auth -> adapter -> tool -> Java search/DB -> UI DTO, model boundary is offline. */
@SpringBootTest(properties = "restaurant-bot.telegram.bot-token=" + InitDataFixture.TOKEN)
@AutoConfigureMockMvc @ActiveProfiles("test") @Import(MiniAppAiIntegrationTest.Config.class)
class MiniAppAiIntegrationTest {
    @TestConfiguration
    static class Config {
        @Bean @Primary Clock miniAppAiClock() {
            return Clock.fixed(Instant.ofEpochSecond(InitDataFixture.NOW), ZoneOffset.UTC);
        }
        @Bean ChatModel model() { return mock(ChatModel.class); }
        @Bean SpringAiSearchAdapter adapter(ChatModel model, RestaurantSearchService search, Clock clock,
                ReferenceResolver resolver, MenuService menus, RestaurantService restaurants) {
            return new SpringAiSearchAdapter(model, search, clock, true, resolver, menus, restaurants);
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ChatModel model;
    @Autowired RestaurantSearchService search;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    private static final String FULL = """
            {"guests":2,"totalBudgetByn":150,"date":"TODAY","time":"21:00",
             "cuisine":"ITALIAN","preferredTags":["QUIET"]}
            """;
    private final String auth = InitDataFixture.signed(InitDataFixture.NOW);

    @BeforeEach void resetModel() { reset(model); }
    static ChatResponse response(String text, String finish, List<AssistantMessage.ToolCall> calls) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content(text).toolCalls(calls).build(),
                ChatGenerationMetadata.builder().finishReason(finish).build())));
    }
    static ChatResponse tool(String name, String args) {
        return response("PRIVATE_MODEL_PROSE", "TOOL_CALLS",
                List.of(new AssistantMessage.ToolCall("offline-call", "function", name, args)));
    }
    String request(String query) throws Exception { return json.writeValueAsString(Map.of("query", query)); }
    org.springframework.test.web.servlet.ResultActions natural(String query) throws Exception {
        return mvc.perform(post("/api/miniapp/v1/search/natural").header("X-Telegram-Init-Data", auth)
                .contentType("application/json;charset=UTF-8").content(request(query)));
    }
    List<Integer> stateCounts() {
        return List.of("conversation_state", "selection_items", "spring_ai_chat_memory").stream()
                .map(t -> jdbc.queryForObject("SELECT COUNT(*) FROM " + t, Integer.class)).toList();
    }
    @Test void trustedCardsMatchJavaSearchAndLimitsWithoutProtocolOrState() throws Exception {
        var before = stateCounts();
        when(model.call(any(Prompt.class))).thenReturn(tool("searchRestaurants", FULL),
                response("{\"PRIVATE_MODEL_PROSE\":true}", "STOP", List.of()));
        var body = natural("Сегодня в 21:00 нас двое, до 150 BYN, итальянская кухня, спокойно")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.cards.length()").value(3))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        var expected = search.search(new SearchRequest(2, new java.math.BigDecimal("150"), "TODAY", "21:00",
                Cuisine.ITALIAN, Set.of(RestaurantTag.QUIET)));
        var cards = json.readTree(body).get("cards");
        for (int i = 0; i < cards.size(); i++) {
            assertThat(cards.get(i).get("id").asLong()).isEqualTo(expected.candidates().get(i).restaurant().id());
            assertThat(cards.get(i).get("estimatedTotalByn").decimalValue())
                    .isEqualByComparingTo(expected.candidates().get(i).estimatedTotalByn());
            assertThat(cards.get(i)).isEqualTo(json.readTree(json.writeValueAsString(
                    MiniAppMapper.card(expected.candidates().get(i), expected))));
        }
        assertThat(body).doesNotContain("PRIVATE_MODEL_PROSE", "tool_calls", "modelCalls",
                "toolExecutions", "searchResult", "allowedReasonCodes", "normalizedCriteria", InitDataFixture.TOKEN);
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        var first = (OpenAiChatOptions) prompts.getAllValues().getFirst().getOptions();
        assertThat(first.getToolCallbacks()).extracting(t -> t.getToolDefinition().name())
                .containsExactly("searchRestaurants", "getRestaurantDetails", "getRestaurantMenu");
        assertThat(first.getInternalToolExecutionEnabled()).isFalse();
        assertThat(first.getParallelToolCalls()).isFalse();
        var second = (OpenAiChatOptions) prompts.getAllValues().getLast().getOptions();
        assertThat(second.getToolCallbacks()).isEmpty();
        assertThat(second.getToolChoice()).isEqualTo("none");
        assertThat(stateCounts()).isEqualTo(before);
    }
    @Test void confirmedPreferenceReasonsAreMappedToCards() throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(tool("searchRestaurants", FULL.replace("ITALIAN", "GEORGIAN")
                .replace("QUIET", "COZY")), response("{}", "STOP", List.of()));
        natural("Сегодня 21:00 двое 150 BYN грузинская кухня уютно").andExpect(status().isOk())
                .andExpect(jsonPath("$.cards[0].reasons").value(org.hamcrest.Matchers.hasItem(
                        "Совпадает пожелание «уютно» по тегу каталога")));
    }
    @Test void clarificationDoesNotCarryCriteriaBetweenIndependentQueries() throws Exception {
        var before = stateCounts();
        when(model.call(any(Prompt.class))).thenReturn(tool("searchRestaurants", FULL),
                response("{}", "STOP", List.of()), tool("searchRestaurants", "{\"guests\":3}"));
        natural("Сегодня 21:00 двое 150 BYN итальянская кухня").andExpect(jsonPath("$.status").value("OK"));
        natural("А если нас трое?").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEED_CLARIFICATION"))
                .andExpect(jsonPath("$.cards").isEmpty())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("общий бюджет")));
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(3)).call(prompts.capture());
        assertThat(prompts.getAllValues().getLast().getInstructions()).hasSize(2);
        assertThat(stateCounts()).isEqualTo(before);
    }
    @Test void noResultsExecutesOneSearchWithoutExplanationOrRelaxation() throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(tool("searchRestaurants", FULL.replace("150", "0.01")));
        natural("Сегодня 21:00 двое 0.01 BYN").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_RESULTS")).andExpect(jsonPath("$.cards").isEmpty());
        verify(model).call(any(Prompt.class));
    }
    @Test void ambiguousTimeAndCurrencyCannotBeGuessedEvenByAFullModelToolRequest() throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(tool("searchRestaurants", FULL));
        natural("Завтра вечером нас трое, бюджет 180 рублей").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEED_CLARIFICATION"))
                .andExpect(jsonPath("$.cards").isEmpty())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("точное время")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("BYN")));
        verify(model).call(any(Prompt.class));
    }
    @Test void providerFailureIsControlledAndStructuredDetailsMenuStillWork() throws Exception {
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("PRIVATE_PROVIDER_PAYLOAD"));
        natural("Сегодня 21:00 двое 150 BYN").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TEMPORARY_ERROR"))
                .andExpect(jsonPath("$.message").value("Сервис временно недоступен. Попробуйте позже."));
        mvc.perform(post("/api/miniapp/v1/search").header("X-Telegram-Init-Data", auth).contentType("application/json")
                .content("{\"guests\":2,\"totalBudgetByn\":150,\"date\":\"TODAY\",\"time\":\"21:00\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cards.length()").value(3));
        for (var path : List.of("/restaurants/1", "/restaurants/1/menu")) {
            mvc.perform(get("/api/miniapp/v1" + path).header("X-Telegram-Init-Data", auth)).andExpect(status().isOk());
        }
        verify(model).call(any(Prompt.class));
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "null", "{", "{\"query\":null}", "{\"query\":\"\"}", "{\"query\":\"  \"}"})
    void invalidBodiesDoNotReachModel(String body) throws Exception {
        mvc.perform(post("/api/miniapp/v1/search/natural").header("X-Telegram-Init-Data", auth)
                .contentType("application/json").content(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verifyNoInteractions(model);
    }
    @Test void inputLengthBoundaryAndInvalidToolArgumentsAreControlled() throws Exception {
        natural("я".repeat(2001)).andExpect(status().isBadRequest());
        verifyNoInteractions(model);
        when(model.call(any(Prompt.class))).thenReturn(tool("searchRestaurants", FULL.replace("\"guests\":2", "\"guests\":7")));
        natural("я".repeat(2000)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verify(model).call(any(Prompt.class));
    }
    @Test void missingTamperedAndStaleAuthRejectBeforeModel() throws Exception {
        for (var value : List.of("", auth.replace("synthetic-query", "tampered"),
                InitDataFixture.signed(InitDataFixture.NOW - 3601))) {
            mvc.perform(post("/api/miniapp/v1/search/natural").header("X-Telegram-Init-Data", value)
                    .contentType("application/json").content(request("Сегодня 21:00 двое 150 BYN")))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_FAILED"));
        }
        verifyNoInteractions(model);
    }
    @Test void modelProseAndConversationalToolsCannotBecomeMiniAppFacts() throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(response("PRIVATE_MODEL_PROSE", "STOP", List.of()),
                tool("getRestaurantMenu", "{\"reference\":{\"ordinal\":2}}"));
        for (var query : List.of("Рестораны", "Меню второго")) {
            natural(query).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEED_CLARIFICATION"))
                    .andExpect(jsonPath("$.cards").isEmpty())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE_MODEL_PROSE"))));
        }
        verify(model, times(2)).call(any(Prompt.class));
    }
}

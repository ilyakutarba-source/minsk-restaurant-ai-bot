package by.ilya.restaurantbot.ai;

import java.time.Clock;
import java.util.List;

import by.ilya.restaurantbot.catalog.*;
import by.ilya.restaurantbot.conversation.*;
import by.ilya.restaurantbot.search.RestaurantSearchService;
import by.ilya.restaurantbot.telegram.TelegramUpdateHandler;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.pengrad.telegrambot.utility.BotUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.mockito.ArgumentCaptor;

import static by.ilya.restaurantbot.ai.AiFixtures.*;
import static by.ilya.restaurantbot.ai.AiSearchReply.Status.*;
import static by.ilya.restaurantbot.ai.RestaurantFollowUpTool.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** Real catalog/menu/resolver/conversation persistence on H2 and PostgreSQL; offline model choices. */
@SpringBootTest
@ActiveProfiles("test")
@Import(SpringAiSearchIntegrationTest.FixedClock.class)
class RestaurantFollowUpIntegrationTest {
    static final long A = 9000000091L;
    static final long B = 9000000092L;
    @Autowired ConversationStore store;
    @Autowired SelectionService selections;
    @Autowired ReferenceResolver resolver;
    @Autowired RestaurantService catalog;
    @Autowired MenuService menus;
    @Autowired RestaurantSearchService realSearch;
    @Autowired ChatMemory memory;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;
    ChatModel model;
    RestaurantSearchService search;
    ConversationService conversation;

    @BeforeEach void setup() {
        clean();
        model = mock(ChatModel.class);
        search = mock(RestaurantSearchService.class);
        conversation = conversation(new SpringAiSearchAdapter(model, search, clock, true, resolver, menus, catalog));
        selections.replace(A, List.of(2L, 1L, 3L));
        clearInvocations(model);
    }
    ConversationService conversation(SpringAiSearchAdapter adapter) {
        return new ConversationService(store, memory, adapter, clock, selections);
    }
    @AfterEach void clean() {
        for (long chat : new long[]{A, B}) {
            store.reset(store.load(chat));
            selections.afterReset(chat);
        }
    }
    AiSearchReply turn(long chat, String text, String tool, String args, boolean sent) {
        when(model.call(any(Prompt.class))).thenReturn(response("invented address price hours", "TOOL_CALLS", List.of(tool(tool, args))));
        return conversation.handle(chat, text, reply -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return sent;
        });
    }
    AiSearchReply turn(String text, String tool, String args) { return turn(A, text, tool, args, true); }

    @Test void menuSecondReadsExactDatasetWithoutSearchOrSelectionCriteriaChanges() {
        var state = store.load(A);
        var rows = selections.current(A);
        var expected = menus.getMenuByRestaurantId(1, null, null).orElseThrow();
        var reply = turn("Меню второго", MENU, "{\"reference\":{\"ordinal\":2}}");
        assertThat(reply.status()).isEqualTo(OK);
        assertThat(reply.followUpResult().menu()).isEqualTo(expected);
        assertThat(reply.followUpResult().menu().items()).hasSize(6);
        assertThat(reply.text()).contains("Драники с мачанкой по-белорусски", "22.90 BYN", "PARTIAL", "2026-10-02")
                .doesNotContain("invented address price hours");
        assertThat(store.load(A)).isEqualTo(state);
        assertThat(selections.current(A)).isEqualTo(rows);
        assertOneFollowUp(reply);
    }

    @Test void deliveredSearchThenMenuFollowUpUsesShownOrderAndKeepsSelection() {
        when(search.search(any())).thenAnswer(call -> realSearch.search(call.getArgument(0)));
        when(model.call(any(Prompt.class))).thenReturn(selection(FULL.replace("\"ITALIAN\"", "null")), explanation("{}"));
        var found = conversation.handle(A, "Сегодня в 21:00 двое, общий бюджет 150 BYN", sent -> true);
        assertThat(found.status()).isEqualTo(OK);
        assertThat(found.searchResult().candidates()).hasSize(3);
        verify(search).search(any());
        var state = store.load(A);
        var rows = selections.current(A);
        clearInvocations(model, search);
        var menu = turn("Что по меню у второго?", MENU, "{\"reference\":{\"ordinal\":2}}");
        assertThat(menu.followUpResult().menu()).isEqualTo(menus.getMenuByRestaurantId(rows.get(1).restaurantId(), null, null).orElseThrow());
        assertThat(store.load(A)).isEqualTo(state);
        assertThat(selections.current(A)).isEqualTo(rows);
        assertOneFollowUp(menu);
    }

    @Test void ownHoursRenderOvernightBoundaryRatherThanInventingSameDayClose() {
        var reply = turn("До скольки второй?", DETAILS, "{\"reference\":{\"ordinal\":2},\"focus\":\"HOURS\"}");
        assertThat(reply.text()).contains("Пт: 08:00–01:00 следующего дня", "Сб: 08:00–01:00 следующего дня",
                "Недельное расписание не гарантирует праздничных исключений");
        assertOneFollowUp(reply);
    }

    @Test void pastaFirstUsesSupportedDishTypeAndPriceIsPerItem() {
        var reply = turn("Есть паста у первого до 20 BYN?", MENU,
                "{\"reference\":{\"ordinal\":1},\"dishType\":\"PASTA\",\"maxItemPriceByn\":20}");
        assertThat(reply.followUpResult().menu()).isEqualTo(menus.getMenuByRestaurantId(2, DishType.PASTA, new java.math.BigDecimal("20")).orElseThrow());
        assertThat(reply.followUpResult().menu().items()).isNotEmpty().allSatisfy(item -> {
            assertThat(item.dishType()).isEqualTo(DishType.PASTA);
            assertThat(item.priceByn()).isLessThanOrEqualTo(new java.math.BigDecimal("20"));
        });
        assertThat(reply.text()).contains("PARTIAL", "Наличие блюд и актуальность цен не гарантируются.");
        assertOneFollowUp(reply);
    }

    @Test void absentPastaIsUnknownInPartialMenuRatherThanAbsentInRestaurant() {
        var reply = turn("Есть паста у второго?", MENU, "{\"reference\":{\"ordinal\":2},\"dishType\":\"PASTA\"}");
        assertThat(reply.status()).isEqualTo(NO_RESULTS);
        assertThat(reply.followUpResult().menu().coverage()).isEqualTo(MenuCoverage.PARTIAL);
        assertThat(reply.text()).contains("Не найдено в сохранённой части меню", "PARTIAL", "Источник меню", "2026-10-02")
                .doesNotContain("Такого блюда нет", "Пасты нет");
        assertOneFollowUp(reply);
    }

    @Test void expandedVenueWithoutMenuUsesTelegramUnavailableReplyWithoutInventedItems() {
        long cafe=jdbc.queryForObject("SELECT id FROM restaurants WHERE seed_key='embassy-mira-1'",Long.class);
        selections.replace(A,List.of(cafe));
        var reply=turn("Меню первого",MENU,"{\"reference\":{\"ordinal\":1}}");
        assertThat(reply.status()).isEqualTo(DATA_UNAVAILABLE);
        assertThat(reply.followUpResult().menu().items()).isEmpty();
        assertThat(reply.followUpResult().menu().coverage()).isNull();
        assertThat(reply.text()).contains("Сохранённое меню недоступно")
                .doesNotContain("invented address price hours", "Источник меню", "null");
        assertOneFollowUp(reply);
        var bot=mock(TelegramBot.class);
        var sent=mock(SendResponse.class); when(sent.isOk()).thenReturn(true);
        when(bot.execute(any(SendMessage.class))).thenReturn(sent);
        var update=BotUtils.parseUpdate("{\"update_id\":1,\"message\":{\"message_id\":1,\"chat\":{\"id\":"+A
                +",\"type\":\"private\"},\"text\":\"Меню первого\"}}");
        new TelegramUpdateHandler(bot,conversation).process(List.of(update));
        var message=ArgumentCaptor.forClass(SendMessage.class); verify(bot).execute(message.capture());
        assertThat(message.getValue().getParameters().get("text")).isEqualTo(reply.text());
    }

    @Test void missingSavedMenuIsDataUnavailableAndKeepsMetadata() {
        var prices = jdbc.queryForList("SELECT id FROM menu_items WHERE restaurant_id=3 AND active=true", Long.class);
        try {
            jdbc.update("UPDATE menu_items SET active=false WHERE restaurant_id=3");
            var reply = turn("Меню третьего", MENU, "{\"reference\":{\"ordinal\":3}}");
            assertThat(reply.status()).isEqualTo(DATA_UNAVAILABLE);
            assertThat(reply.text()).contains("Сохранённое меню недоступно", "PARTIAL", "Источник меню");
            assertOneFollowUp(reply);
        } finally {
            for (Long id : prices) jdbc.update("UPDATE menu_items SET active=true WHERE id=?", id);
        }
    }

    @Test void hoursThirdAndAddressSecondComeFromFreshCatalogDespitePoisonedMemory() {
        store.remember(store.load(A), "Предыдущий вопрос", "Адрес второго: выдуманный. Третий работает до 99:99. Цена 0 BYN.");
        var expected = catalog.getRestaurant(3).orElseThrow();
        var hours = turn("До скольки третий?", DETAILS, "{\"reference\":{\"ordinal\":3},\"focus\":\"HOURS\"}");
        assertThat(hours.followUpResult().details()).isEqualTo(expected);
        assertThat(hours.text()).contains("Хинкальня", "Собственное недельное расписание", expected.hoursSource())
                .doesNotContain("99:99", "Цена 0 BYN", "invented address");
        assertOneFollowUp(hours);
        var old = catalog.getRestaurant(1).orElseThrow().address();
        try {
            jdbc.update("UPDATE restaurants SET address=? WHERE id=1", "Свежий адрес из каталога");
            clearInvocations(model);
            var address = turn("Где находится второй?", DETAILS, "{\"reference\":{\"ordinal\":2}}");
            assertThat(address.text()).contains("Свежий адрес из каталога").doesNotContain(old, "выдуманный");
            assertOneFollowUp(address);
        } finally { jdbc.update("UPDATE restaurants SET address=? WHERE id=1", old); }
    }

    @Test void exactNameWithoutSelectionUsesCatalogAndMissingContactsAndRatingAreExplicit() {
        for (String focus : List.of("ALL", "CONTACTS", "RATING")) {
            clearInvocations(model);
            var reply = turn(B, "Расскажи подробнее про Хинкальня", DETAILS,
                    "{\"reference\":{\"name\":\"  ХИНКАЛЬНЯ  \"},\"focus\":\"" + focus + "\"}", true);
            assertThat(reply.status()).isEqualTo(OK);
            assertThat(reply.text()).contains("Хинкальня", "Дзержинского, 104");
            if (!focus.equals("RATING")) assertThat(reply.text()).contains("Телефон и сайт не сохранены");
            else assertThat(reply.text()).contains("Рейтинг недоступен");
            assertThat(selections.current(B)).isEmpty();
            assertOneFollowUp(reply);
        }
    }

    @ParameterizedTest
    @CsvSource({"getRestaurantMenu,Меню", "getRestaurantDetails,Подробнее"})
    void referencesWithoutSelectionUnknownNamesAndMissingPositionsRemainControlled(String tool, String text) {
        for (String ref : List.of("{\"ordinal\":2}", "{\"name\":\"Unknown\"}")) {
            clearInvocations(model);
            var reply = turn(B, text, tool, "{\"reference\":" + ref + "}", true);
            assertThat(reply.status()).isEqualTo(ref.contains("ordinal") ? NEED_CLARIFICATION : NOT_FOUND);
            assertThat(reply.text()).doesNotContain("бюджет", "дату посещения");
            assertOneFollowUp(reply);
        }
        selections.replace(B, List.of(3L));
        clearInvocations(model);
        assertThat(turn(B, text, tool, "{\"reference\":{\"ordinal\":2}}", true).status()).isEqualTo(NEED_CLARIFICATION);
        clearInvocations(model);
        var last = turn(B, text, tool, "{\"reference\":{\"last\":true}}", true);
        assertThat(last.status()).isEqualTo(OK);
        if (last.followUpResult().menu() != null) {
            assertThat(last.followUpResult().menu().restaurantId()).isEqualTo(3);
            assertThat(last.text()).contains("Шашлык из свинины").doesNotContain("ID:");
        } else {
            assertThat(last.followUpResult().details().id()).isEqualTo(3);
            assertThat(last.text()).contains("Хинкальня", "Дзержинского, 104");
        }
        assertOneFollowUp(last);
    }

    @Test void ambiguousExactNameStaysControlledForBothTools() {
        var name = catalog.getRestaurant(1).orElseThrow().name();
        try {
            jdbc.update("UPDATE restaurants SET name='Pizza Tempo' WHERE id=1");
            for (String tool : List.of(MENU, DETAILS)) {
                clearInvocations(model);
                var reply = turn("Подробнее про Pizza Tempo", tool, "{\"reference\":{\"name\":\"Pizza Tempo\"}}");
                assertThat(reply.status()).isEqualTo(NEED_CLARIFICATION);
                assertThat(reply.text()).contains("нескольких филиалах").doesNotContain("бюджет");
                assertOneFollowUp(reply);
            }
        } finally { jdbc.update("UPDATE restaurants SET name=? WHERE id=1", name); }
    }

    @Test void selectionScopeRejectsCatalogNameOutsideShownIds() {
        selections.replace(A, List.of(3L));
        assertThat(turn("Подробнее про Pizza Tempo", DETAILS, "{\"reference\":{\"name\":\"Pizza Tempo\"}}").status()).isEqualTo(NOT_FOUND);
        verifyNoInteractions(search);
    }

    @Test void newClearsReferencesWithZeroCallsAndSubsequentOrdinalClarifies() {
        var reset = conversation.handle(A, "/new", reply -> true);
        assertThat(reset.modelCalls()).isZero();
        assertThat(reset.toolExecutions()).isZero();
        verifyNoInteractions(model, search);
        assertThat(turn("Меню второго", MENU, "{\"reference\":{\"ordinal\":2}}").status()).isEqualTo(NEED_CLARIFICATION);
    }

    @Test void safeMemoryContainsOnlyUserAndDeliveredJavaTextAndNoToolDtoProtocolOrModelProse() {
        var reply = turn("Меню второго", MENU, "{\"reference\":{\"ordinal\":2}}");
        var messages = memory.get(store.load(A).conversationId());
        assertThat(messages).hasSize(2);
        assertThat(messages.getFirst().getMessageType()).isEqualTo(org.springframework.ai.chat.messages.MessageType.USER);
        assertThat(messages.getLast().getMessageType()).isEqualTo(org.springframework.ai.chat.messages.MessageType.ASSISTANT);
        assertThat(messages.getLast().getText()).isEqualTo(reply.text())
                .doesNotContain("tool_calls", "getRestaurantMenu", "restaurantId", "invented address", "menu_items");
    }

    @ParameterizedTest
    @ValueSource(strings = {"getRestaurantMenu", "getRestaurantDetails"})
    void telegramFailedSendNeverWritesAssistantOrChangesSelection(String tool) {
        var state = store.load(A);
        var rows = selections.current(A);
        var bot = mock(TelegramBot.class);
        when(bot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));
        when(model.call(any(Prompt.class))).thenReturn(response("ignored", "TOOL_CALLS", List.of(tool(tool, "{\"reference\":{\"ordinal\":1}}"))));
        var update = BotUtils.parseUpdate("{\"update_id\":1,\"message\":{\"message_id\":1,\"chat\":{\"id\":" + A
                + ",\"type\":\"private\"},\"text\":\"Меню или details первого\"}}");
        new TelegramUpdateHandler(bot, conversation).process(List.of(update));
        verify(bot).execute(any(SendMessage.class));
        assertThat(memory.get(state.conversationId())).isEmpty();
        assertThat(store.load(A)).isEqualTo(state);
        assertThat(selections.current(A)).isEqualTo(rows);
        verifyNoInteractions(search);
    }

    @Test void menuAndDetailsBatchIsRejectedBeforeAnyExecution() {
        when(model.call(any(Prompt.class))).thenReturn(response("ignored", "TOOL_CALLS", List.of(
                tool(MENU, "{\"reference\":{\"ordinal\":1}}"), tool(DETAILS, "{\"reference\":{\"ordinal\":1}}"))));
        var reply = conversation.handle(A, "Меню и адрес первого", sent -> true);
        assertThat(reply.status()).isEqualTo(INVALID_INPUT);
        assertThat(reply.toolExecutions()).isZero();
        assertThat(reply.followUpResult()).isNull();
        verify(model).call(any(Prompt.class));
        verifyNoInteractions(search);
    }

    @ParameterizedTest
    @CsvSource({"getRestaurantMenu,Меню второго", "getRestaurantDetails,Где находится второй?"})
    void productionSdkRoutesThreeToolsAndRendersOwnFactsWithoutSecondCall(String tool, String text) throws Exception {
        try (var fixture = new ProviderFixture(ProviderFixture.tool(tool, "{\"reference\":{\"ordinal\":2}}"));
             var http = new AiConfiguration().aiHttpClient()) {
            var adapter = new SpringAiSearchAdapter(AiConfiguration.createModel(http, "offline-fixture", fixture.origin()),
                    search, clock, true, resolver, menus, catalog);
            var reply = conversation(adapter).handle(A, text, sent -> true);
            assertThat(reply.status()).isEqualTo(OK);
            assertThat(reply.modelCalls()).isEqualTo(1);
            assertThat(reply.toolExecutions()).isEqualTo(1);
            assertThat(fixture.requests()).hasSize(1);
            assertThat(fixture.requests().getFirst().path("tools")).extracting(t -> t.path("function").path("name").asText())
                    .containsExactly("searchRestaurants", "getRestaurantDetails", "getRestaurantMenu");
            assertThat(fixture.requests().getFirst().path("parallel_tool_calls").asBoolean()).isFalse();
            assertThat(fixture.requests().getFirst().toString()).doesNotContain(Long.toString(A), "telegram:", "selection_version");
            if (reply.followUpResult().menu() != null) {
                assertThat(reply.followUpResult().menu().restaurantId()).isEqualTo(1);
                assertThat(reply.text()).contains("Драники с мачанкой по-белорусски").doesNotContain("ID:");
            } else assertThat(reply.followUpResult().details().id()).isEqualTo(1);
            verifyNoInteractions(search);
        }
    }

    private void assertOneFollowUp(AiSearchReply reply) {
        assertThat(reply.searchResult()).isNull();
        assertThat(reply.modelCalls()).isEqualTo(1);
        assertThat(reply.toolExecutions()).isEqualTo(1);
        verify(model).call(any(Prompt.class));
        verifyNoInteractions(search);
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(((OpenAiChatOptions) prompt.getValue().getOptions()).getToolCallbacks())
                .extracting(t -> t.getToolDefinition().name()).containsExactly("searchRestaurants", "getRestaurantDetails", "getRestaurantMenu");
    }
}

package by.ilya.restaurantbot.ai;

import java.time.Clock;
import java.util.List;

import by.ilya.restaurantbot.catalog.Cuisine;
import by.ilya.restaurantbot.catalog.RestaurantTag;
import by.ilya.restaurantbot.conversation.ConversationService;
import by.ilya.restaurantbot.conversation.ConversationStore;
import by.ilya.restaurantbot.search.RestaurantSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static by.ilya.restaurantbot.ai.AiFixtures.*;
import static by.ilya.restaurantbot.ai.AiSearchReply.Status.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(SpringAiSearchIntegrationTest.FixedClock.class)
class ConversationIntegrationTest {
    static final long A = 9000000001L;
    static final long B = 9000000002L;
    @Autowired ConversationStore store;
    @Autowired by.ilya.restaurantbot.conversation.SelectionService selections;
    @Autowired ChatMemory memory;
    @Autowired RestaurantSearchService search;
    @Autowired Clock clock;
    ChatModel model;
    ConversationService conversation;

    @BeforeEach
    void setup() {
        store.reset(store.load(A));
        store.reset(store.load(B));
        model = mock(ChatModel.class);
        conversation = new ConversationService(store, memory, new SpringAiSearchAdapter(model, search, clock, false), clock, selections);
    }

    AiSearchReply turn(long chat, String text, String args) {
        when(model.call(any(Prompt.class))).thenReturn(selection(args));
        return conversation.handle(chat, text, reply -> true);
    }

    void full(long chat) {
        assertThat(turn(chat, "Сегодня в 21:00 двое, общий бюджет 150 BYN, итальянская кухня.", FULL).status()).isEqualTo(OK);
    }

    @Test
    void partialRepliesPersistAndClarifyOnlyStillMissingFields() {
        var first = turn(A, "Нас двое", "{\"guests\":2}");
        assertThat(first.status()).isEqualTo(NEED_CLARIFICATION);
        assertThat(first.missingFields()).containsExactly("totalBudgetByn", "date", "time");
        assertThat(first.text()).doesNotContain("число гостей");
        assertThat(store.load(A).currentCriteria().guests()).isEqualTo(2);
        var second = turn(A, "150 BYN", "{\"totalBudgetByn\":150}");
        assertThat(second.missingFields()).containsExactly("date", "time");
        assertThat(second.text()).doesNotContain("бюджет", "число гостей");
        var completed = turn(A, "Сегодня в 21:00", "{\"date\":\"TODAY\",\"time\":\"21:00\"}");
        assertThat(completed.status()).isEqualTo(OK);
        assertThat(completed.searchResult().normalizedCriteria().guests()).isEqualTo(2);
        assertThat(completed.modelCalls()).isEqualTo(1);
        assertThat(completed.toolExecutions()).isEqualTo(1);
        assertThat(memory.get(store.load(A).conversationId())).hasSize(6);
    }

    @Test
    void guestsOnlyPreservesAllOtherFieldsAndRereadsFacts() {
        full(A);
        var prior = store.load(A).currentCriteria();
        var reply = turn(A, "А если нас четверо?", "{\"guests\":4}");
        var updated = store.load(A).currentCriteria();
        assertThat(updated.guests()).isEqualTo(4);
        assertThat(updated.totalBudgetByn()).isEqualByComparingTo(prior.totalBudgetByn());
        assertThat(updated.date()).isEqualTo(prior.date()).isEqualTo("2026-10-02");
        assertThat(updated.time()).isEqualTo(prior.time());
        assertThat(updated.cuisine()).isEqualTo(prior.cuisine());
        assertThat(updated.preferredTags()).isEqualTo(prior.preferredTags());
        assertThat(reply.text()).contains("130.80 BYN");
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().getLast().getContents()).contains("Pizza Tempo", "150 BYN", "А если нас четверо?");
    }

    @Test
    void cuisineAndTagsReplaceWhileNullPreservesAndEmptyClears() {
        full(A);
        turn(A, "Теперь грузинская кухня и уютно", "{\"cuisine\":\"GEORGIAN\",\"preferredTags\":[\"COZY\"]}");
        assertThat(store.load(A).currentCriteria().cuisine()).isEqualTo(Cuisine.GEORGIAN);
        assertThat(store.load(A).currentCriteria().preferredTags()).containsExactly(RestaurantTag.COZY);
        turn(A, "Нас трое", "{\"guests\":3,\"cuisine\":null,\"preferredTags\":null}");
        assertThat(store.load(A).currentCriteria().preferredTags()).containsExactly(RestaurantTag.COZY);
        assertThat(store.load(A).currentCriteria().cuisine()).isEqualTo(Cuisine.GEORGIAN);
        turn(A, "Без пожеланий к обстановке", "{\"preferredTags\":[]}");
        assertThat(store.load(A).currentCriteria().preferredTags()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"guests\":0}", "{\"totalBudgetByn\":-1}", "{\"time\":\"25:00\"}",
            "{\"date\":\"2025-01-01\"}", "{\"guests\":2.5}", "{\"preferredTags\":[\"UNKNOWN\"]}",
            "{\"totalBudgetByn\":1e3000}", "{\"chatId\":123}", "{\"generation\":2}"})
    void invalidNewValuesNeverReuseOldStateAsIfValid(String args) {
        full(A);
        var prior = store.load(A).currentCriteria();
        var reply = turn(A, "Изменить критерии", args);
        assertThat(reply.status()).isEqualTo(INVALID_INPUT);
        assertThat(reply.toolExecutions()).isZero();
        assertThat(reply.modelCalls()).isEqualTo(1);
        assertThat(store.load(A).currentCriteria()).isEqualTo(prior);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Бюджет как обычно|totalBudgetByn", "150|totalBudgetByn", "150 BYN на человека|totalBudgetByn",
            "Завтра вечером|time", "В семь|time", "В 7|time", "К девяти|time", "В девять|time", "На выходных в 21:00|date", "05/10 в 21:00|date"})
    void ambiguousNewCriteriaClarifyEvenWhenOldStateIsCompleteAndModelGuesses(String text, String field) {
        full(A);
        var prior = store.load(A).currentCriteria();
        // A fabricated full request cannot override a Java ambiguity guard.
        var reply = turn(A, text, FULL);
        assertThat(reply.status()).isEqualTo(NEED_CLARIFICATION);
        assertThat(reply.missingFields()).contains(field);
        assertThat(reply.toolExecutions()).isZero();
        assertThat(reply.modelCalls()).isEqualTo(1);
        assertThat(store.load(A).currentCriteria()).isEqualTo(prior);
    }

    @Test
    void validFieldsPersistAlongsideAmbiguousFieldWithoutSearch() {
        full(A);
        var reply = turn(A, "Нас четверо, но вечером", "{\"guests\":4,\"time\":null}");
        assertThat(reply.status()).isEqualTo(NEED_CLARIFICATION);
        assertThat(reply.missingFields()).containsExactly("time");
        assertThat(store.load(A).currentCriteria().guests()).isEqualTo(4);
        assertThat(store.load(A).currentCriteria().time()).isEqualTo("21:00");
    }

    @Test
    void chatsAndGenerationsAreIndependentAndResetUsesNoModel() {
        full(A);
        turn(B, "Нас четверо, общий бюджет 300 BYN, завтра в 20:00", "{\"guests\":4,\"totalBudgetByn\":300,\"date\":\"TOMORROW\",\"time\":\"20:00\"}");
        var a = store.load(A);
        var b = store.load(B);
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().getLast().getContents()).doesNotContain("Pizza Tempo", "150 BYN", "итальянская кухня");
        assertThat(a.currentCriteria()).isNotEqualTo(b.currentCriteria());
        assertThat(a.conversationId()).isEqualTo("telegram:" + A + ":" + a.generation());
        clearInvocations(model);
        var reset = conversation.handle(A, "/new", reply -> true);
        assertThat(reset.modelCalls()).isZero();
        assertThat(reset.toolExecutions()).isZero();
        verifyNoInteractions(model);
        assertThat(store.load(A).generation()).isEqualTo(a.generation() + 1);
        assertThat(memory.get(a.conversationId())).isEmpty();
        assertThat(memory.get(store.load(A).conversationId())).isEmpty();
        assertThat(store.load(A).currentCriteria()).isEqualTo(by.ilya.restaurantbot.conversation.CriteriaMerge.empty());
        assertThat(store.load(B)).isEqualTo(b);
        assertThat(memory.get(b.conversationId())).hasSize(2);
        var followup = turn(A, "Нас трое", "{\"guests\":3}");
        assertThat(followup.missingFields()).containsExactly("totalBudgetByn", "date", "time");
        assertThat(followup.toolExecutions()).isZero();
    }

    @Test
    void boundedWindowEvictsOldTurnsAndPersistsOnlySafeMessagesOnce() {
        for (int i = 0; i < 13; i++) turn(A, "Реплика " + i, "{\"guests\":2}");
        var transcript = memory.get(store.load(A).conversationId());
        assertThat(transcript).hasSize(20);
        assertThat(transcript.getFirst().getText()).isEqualTo("Реплика 3");
        assertThat(transcript.get(18).getText()).isEqualTo("Реплика 12");
        assertThat(transcript.stream().filter(m -> m.getMessageType() == MessageType.USER)).hasSize(10);
        assertThat(transcript.stream().filter(m -> m.getMessageType() == MessageType.ASSISTANT)).hasSize(10);
        assertThat(transcript).allSatisfy(message -> {
            assertThat(message.getMessageType()).isIn(MessageType.USER, MessageType.ASSISTANT);
            assertThat(message.getMetadata()).containsOnlyKeys("messageType");
            assertThat(message.getText()).doesNotContain("ignored model prose", "tool_calls", "searchRestaurants", "normalizedCriteria", "Google", "SELECT ");
        });
    }

    @Test
    void failedSendDoesNotStoreUnsentAssistantOrUserAndHttpCallsHaveNoTransaction() {
        when(model.call(any(Prompt.class))).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return selection("{\"guests\":2}");
        });
        conversation.handle(A, "Нас двое", reply -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return false;
        });
        assertThat(memory.get(store.load(A).conversationId())).isEmpty();
        assertThat(store.load(A).currentCriteria().guests()).isEqualTo(2);
    }

    @Test
    void invalidExplanationPersistsExactlyTheJavaFallbackSentToUserAndSecondCallHasNoTools() {
        var adapter = new SpringAiSearchAdapter(model, search, clock, true);
        conversation = new ConversationService(store, memory, adapter, clock, selections);
        when(model.call(any(Prompt.class))).thenReturn(selection(FULL), explanation("{\"items\":[]}"));
        var reply = conversation.handle(A, "Общий бюджет 150 BYN, двое, сегодня в 21:00", sent -> true);
        assertThat(reply.explanationFallback()).isTrue();
        assertThat(reply.modelCalls()).isEqualTo(2);
        assertThat(reply.toolExecutions()).isEqualTo(1);
        assertThat(memory.get(store.load(A).conversationId())).hasSize(2);
        assertThat(memory.get(store.load(A).conversationId()).getLast().getText()).isEqualTo(reply.text());
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        var options = (org.springframework.ai.openai.OpenAiChatOptions) prompts.getAllValues().getLast().getOptions();
        assertThat(options.getTools()).isEmpty();
        assertThat(options.getToolCallbacks()).isEmpty();
        assertThat(options.getToolNames()).isEmpty();
        assertThat(options.getToolChoice()).isEqualTo("none");
    }

    @Test
    void noResultsDoesNotRelaxOrRepeatSearchAndRetainsRequestedCriteria() {
        var reply = turn(A, "Двое, общий бюджет 1 BYN, сегодня в 21:00", FULL.replace("150", "1"));
        assertThat(reply.status()).isEqualTo(NO_RESULTS);
        assertThat(reply.modelCalls()).isEqualTo(1);
        assertThat(reply.toolExecutions()).isEqualTo(1);
        assertThat(store.load(A).currentCriteria().totalBudgetByn()).isEqualByComparingTo("1");
        verify(model).call(any(Prompt.class));
    }

    @Test
    void modelClarificationUsesJavaMissingFieldsAndNeverPersistsProviderProse() {
        turn(A, "Нас двое", "{\"guests\":2}");
        when(model.call(any(Prompt.class))).thenReturn(response("raw provider payload", "STOP", List.of()));
        var reply = conversation.handle(A, "Продолжим", sent -> true);
        assertThat(reply.status()).isEqualTo(NEED_CLARIFICATION);
        assertThat(reply.missingFields()).containsExactly("totalBudgetByn", "date", "time");
        assertThat(reply.text()).doesNotContain("число гостей", "raw provider payload");
        var transcript = memory.get(store.load(A).conversationId());
        assertThat(transcript).hasSize(4);
        assertThat(transcript.getLast().getText()).isEqualTo(reply.text());
    }

    @Test
    void maximumDerivedIdFitsOfficialMemorySchemaAdaptation() {
        var state = new by.ilya.restaurantbot.conversation.ConversationState(Long.MIN_VALUE, Long.MAX_VALUE,
                by.ilya.restaurantbot.conversation.CriteriaMerge.empty(), 0, clock.instant());
        assertThat(state.conversationId().length()).isGreaterThan(36).isLessThanOrEqualTo(64);
        store.remember(state, "Synthetic user", "Safe assistant");
        assertThat(memory.get(state.conversationId())).hasSize(2);
        memory.clear(state.conversationId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/start", "/help", "/start@fixture_bot", "/help примеры", "/unknown"})
    void helpCommandsAreJavaOnlyAndPreserveTheEntireConversation(String command) {
        full(A);
        var before = store.load(A);
        var transcript = memory.get(before.conversationId());
        var selected = selections.current(A);
        clearInvocations(model);
        var reply = conversation.handle(A, command, sent -> true);
        assertThat(reply.text()).containsAnyOf("Минск", "Минска", "Неизвестная команда");
        assertThat(reply.modelCalls()).isZero();
        assertThat(reply.toolExecutions()).isZero();
        verifyNoInteractions(model);
        assertThat(store.load(A)).isEqualTo(before);
        assertThat(memory.get(before.conversationId())).isEqualTo(transcript);
        assertThat(selections.current(A)).isEqualTo(selected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Забронируй столик", "Забронируй второй", "Сделай бронь", "Ресторан в Москве",
            "Ресторан в Варшаве", "Ресторан в Гродно", "Ресторан в городе Париж", "Бюджет 150 USD",
            "Общий бюджет 150 евро", "150 PLN", "100 $"})
    void unsupportedRequestsCannotExecuteEvenWhenTheModelWouldGuessValidCriteria(String text) {
        full(A);
        var before = store.load(A);
        var selected = selections.current(A);
        clearInvocations(model);
        var reply = conversation.handle(A, text, sent -> true);
        assertThat(reply.status()).isEqualTo(INVALID_INPUT);
        assertThat(reply.text()).containsAnyOf("Бронирование", "Минск", "BYN");
        assertThat(reply.text()).doesNotContain("Pizza Tempo", "65.40");
        assertThat(reply.modelCalls()).isZero();
        assertThat(reply.toolExecutions()).isZero();
        verifyNoInteractions(model);
        assertThat(store.load(A)).isEqualTo(before);
        assertThat(selections.current(A)).isEqualTo(selected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"Ресторан в Лиссабоне|UNSUPPORTED_CITY|Минск",
            "Arrange a table for us|UNSUPPORTED_BOOKING|Бронирование", "Бюджет 500 JPY|UNSUPPORTED_CURRENCY|BYN"})
    void modelScopeAbstentionUsesOnlyAnAllowedCodeAndJavaText(String text, String code, String expected) {
        full(A);
        var prior = store.load(A);
        var selected = selections.current(A);
        clearInvocations(model);
        when(model.call(any(Prompt.class))).thenReturn(response(code, "STOP", List.of()));
        var reply = conversation.handle(A, text, sent -> true);
        assertThat(reply.status()).isEqualTo(INVALID_INPUT);
        assertThat(reply.text()).contains(expected).doesNotContain(code);
        assertThat(reply.modelCalls()).isEqualTo(1);
        assertThat(reply.toolExecutions()).isZero();
        assertThat(store.load(A)).isEqualTo(prior);
        assertThat(selections.current(A)).isEqualTo(selected);
        verify(model).call(any(Prompt.class));
    }

    @Test
    void simultaneousTelegramUpdatesSerializeTheWholeTurnWhileAnotherChatCanProceed() throws Exception {
        var enteredSend = new java.util.concurrent.CountDownLatch(1);
        var releaseSend = new java.util.concurrent.CountDownLatch(1);
        var bot = mock(com.pengrad.telegrambot.TelegramBot.class);
        var success = mock(com.pengrad.telegrambot.response.SendResponse.class);
        when(success.isOk()).thenReturn(true);
        var firstSend = new java.util.concurrent.atomic.AtomicBoolean(true);
        when(bot.execute(any(com.pengrad.telegrambot.request.SendMessage.class))).thenAnswer(call -> {
            var request = (com.pengrad.telegrambot.request.SendMessage) call.getArgument(0);
            if (request.getParameters().get("chat_id").equals(A) && firstSend.getAndSet(false)) {
                enteredSend.countDown();
                assertThat(releaseSend.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
            return success;
        });
        when(model.call(any(Prompt.class))).thenAnswer(call -> {
            var prompt = (Prompt) call.getArgument(0);
            return selection(prompt.getUserMessage().getText().equals("А если нас четверо?") ? "{\"guests\":4}" : FULL);
        });
        var handler = new by.ilya.restaurantbot.telegram.TelegramUpdateHandler(bot, conversation);
        long version = store.load(A).selectionVersion();
        try (var threads = java.util.concurrent.Executors.newFixedThreadPool(3)) {
            var first = threads.submit(() -> handler.process(List.of(update(A, 1, "Сегодня в 21:00 двое, общий бюджет 150 BYN"))));
            try {
                assertThat(enteredSend.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var nextStarted = new java.util.concurrent.CountDownLatch(1);
                var next = threads.submit(() -> {
                    nextStarted.countDown();
                    return handler.process(List.of(update(A, 2, "А если нас четверо?")));
                });
                assertThat(nextStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> next.get(200, java.util.concurrent.TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
                // B completes while A is blocked in transport, with its own empty memory/context.
                threads.submit(() -> handler.process(List.of(update(B, 3, "Сегодня в 21:00 двое, общий бюджет 150 BYN"))))
                        .get(5, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(memory.get(store.load(A).conversationId())).isEmpty();
                assertThat(memory.get(store.load(B).conversationId())).hasSize(2);
                releaseSend.countDown();
                first.get(5, java.util.concurrent.TimeUnit.SECONDS);
                next.get(5, java.util.concurrent.TimeUnit.SECONDS);
            } finally { releaseSend.countDown(); }
        }
        assertThat(store.load(A).currentCriteria().guests()).isEqualTo(4);
        assertThat(store.load(B).currentCriteria().guests()).isEqualTo(2);
        assertThat(store.load(A).selectionVersion()).isEqualTo(version + 2);
        assertThat(selections.current(A)).extracting(by.ilya.restaurantbot.conversation.SelectionItem::restaurantId)
                .containsExactlyElementsOf(search.search(AiJson.mapper().readValue(FULL, by.ilya.restaurantbot.search.SearchRequest.class)).candidates().stream().map(c -> c.restaurant().id()).toList());
        var transcript = memory.get(store.load(A).conversationId());
        assertThat(transcript).hasSize(4);
        assertThat(transcript.get(0).getText()).contains("Сегодня");
        assertThat(transcript.get(1).getText()).contains("65.40 BYN");
        assertThat(transcript.get(2).getText()).isEqualTo("А если нас четверо?");
        assertThat(transcript.get(3).getText()).contains("130.80 BYN");
        verify(model, times(3)).call(any(Prompt.class));
    }

    private static com.pengrad.telegrambot.model.Update update(long chat, int id, String text) {
        return com.pengrad.telegrambot.utility.BotUtils.parseUpdate("""
                {"update_id":%d,"message":{"message_id":%d,"chat":{"id":%d,"type":"private"},"text":"%s"}}
                """.formatted(id, id, chat, text));
    }
}

package by.ilya.restaurantbot.ai;

import java.time.Clock;
import java.util.List;

import by.ilya.restaurantbot.catalog.RestaurantService;
import by.ilya.restaurantbot.conversation.*;
import by.ilya.restaurantbot.search.RestaurantSearchService;
import by.ilya.restaurantbot.telegram.TelegramUpdateHandler;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.pengrad.telegrambot.utility.BotUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static by.ilya.restaurantbot.ai.AiFixtures.*;
import static by.ilya.restaurantbot.conversation.ReferenceResolver.Status.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Runs unchanged against H2 or the externally selected, separate PostgreSQL test database. */
@SpringBootTest
@ActiveProfiles("test")
@Import(SpringAiSearchIntegrationTest.FixedClock.class)
class SelectionIntegrationTest {
    static final long A = 9000000081L;
    static final long B = 9000000082L;
    @Autowired ConversationStore store;
    @Autowired SelectionService selections;
    @Autowired SelectionItemRepository repository;
    @Autowired ReferenceResolver resolver;
    @Autowired RestaurantService catalog;
    @Autowired RestaurantSearchService search;
    @Autowired ChatMemory memory;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired jakarta.persistence.EntityManager entityManager;
    ChatModel model;
    ConversationService conversation;

    @BeforeEach
    void setup() {
        clean();
        model = mock(ChatModel.class);
        conversation = new ConversationService(store, memory, new SpringAiSearchAdapter(model, search, clock, false), clock, selections);
    }

    @AfterEach
    void clean() {
        for (long chat : new long[]{A, B}) {
            store.reset(store.load(chat));
            selections.afterReset(chat);
        }
    }

    private void oldSelection() {
        selections.replace(A, List.of(3L, 1L));
    }

    private AiSearchReply deliver(String arguments, java.util.function.Function<AiSearchReply, Boolean> send) {
        when(model.call(any(Prompt.class))).thenReturn(selection(arguments));
        return conversation.handle(A, "Сегодня в 21:00 двое, общий бюджет 150 BYN", send);
    }

    @Test
    void successBoundaryReplacesExactlyShownOrderAndAdvancesOneVersion() {
        oldSelection();
        long version = store.load(A).selectionVersion();
        var reply = deliver(FULL.replace("\"ITALIAN\"", "null"), sent -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(repository.findCurrent(A)).extracting(SelectionItem::restaurantId).containsExactly(3L, 1L);
            assertThat(store.load(A).selectionVersion()).isEqualTo(version);
            return true;
        });
        var shown = reply.searchResult().candidates().stream().map(c -> c.restaurant().id()).toList();
        assertThat(shown).hasSize(3);
        assertThat(repository.findCurrent(A)).extracting(SelectionItem::restaurantId).containsExactlyElementsOf(shown);
        assertThat(repository.findCurrent(A)).extracting(SelectionItem::position).containsExactly(1, 2, 3);
        assertThat(repository.findCurrent(A)).allSatisfy(item -> assertThat(item.selectionVersion()).isEqualTo(version + 1));
        assertThat(store.load(A).selectionVersion()).isEqualTo(version + 1);
        for (int i = 0; i < shown.size(); i++) {
            var restaurant = catalog.getRestaurant(shown.get(i)).orElseThrow();
            assertThat(reply.text()).contains((i + 1) + ". 🍽 " + restaurant.name() + "\n📍 " + restaurant.address());
            assertThat(resolver.resolve(A, new RestaurantReference(i + 1, null, null)).restaurantId()).isEqualTo(shown.get(i));
        }
        // A subsequent criteria save cannot restore the previous selection version.
        conversation.handle(A, "Продолжим", sent -> false);
        assertThat(store.load(A).selectionVersion()).isEqualTo(version + 1);
    }

    @Test
    void replacementDeletesPreviousRowsRatherThanKeepingHistory() {
        oldSelection();
        long version = store.load(A).selectionVersion();
        selections.replace(A, List.of(2L, 3L));
        assertThat(repository.findCurrent(A)).extracting(SelectionItem::restaurantId).containsExactly(2L, 3L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM selection_items WHERE chat_id=?", Integer.class, A)).isEqualTo(2);
        assertThat(store.load(A).selectionVersion()).isEqualTo(version + 1);
        assertThat(repository.findCurrent(A)).allSatisfy(item -> assertThat(item.selectionVersion()).isEqualTo(version + 1));
    }

    @Test
    void successfulEmptySearchClearsRowsAndAdvancesVersion() {
        oldSelection();
        long version = store.load(A).selectionVersion();
        var reply = deliver(FULL.replace("150", "1"), sent -> true);
        assertThat(reply.status()).isEqualTo(AiSearchReply.Status.NO_RESULTS);
        assertThat(repository.findCurrent(A)).isEmpty();
        assertThat(store.load(A).selectionVersion()).isEqualTo(version + 1);
        assertThat(resolver.resolve(A, new RestaurantReference(1, null, null)).status()).isEqualTo(NEED_CLARIFICATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"rejected", "null", "exception", "empty-rejected"})
    void actualTelegramSendFailurePreservesOldSelectionVersionAndMemory(String failure) {
        oldSelection();
        var previous = repository.findCurrent(A);
        long version = store.load(A).selectionVersion();
        var bot = mock(TelegramBot.class);
        if (failure.equals("exception")) when(bot.execute(any(SendMessage.class))).thenThrow(new IllegalStateException("Offline failure"));
        else if (!failure.equals("null")) when(bot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));
        when(model.call(any(Prompt.class))).thenReturn(selection(failure.equals("empty-rejected") ? FULL.replace("150", "1") : FULL));
        var update = BotUtils.parseUpdate("""
                {"update_id":1,"message":{"message_id":1,"chat":{"id":%d,"type":"private"},
                "text":"Сегодня в 21:00 двое, общий бюджет 150 BYN"}}
                """.formatted(A));
        new TelegramUpdateHandler(bot, conversation).process(List.of(update));
        verify(bot).execute(any(SendMessage.class));
        assertThat(repository.findCurrent(A)).isEqualTo(previous);
        assertThat(store.load(A).selectionVersion()).isEqualTo(version);
        assertThat(memory.get(store.load(A).conversationId())).isEmpty();
        assertThat(resolver.resolve(A, new RestaurantReference(2, null, null)).restaurantId()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void splitDeliveryCommitsOnlyAfterEveryPartSucceeded(boolean allSent) throws Exception {
        oldSelection();
        var old = repository.findCurrent(A);
        long version = store.load(A).selectionVersion();
        var result = search.search(AiJson.mapper().readValue(FULL, by.ilya.restaurantbot.search.SearchRequest.class));
        // Exercise a future longer own card without truncating any trusted factual text.
        var text = new SearchFactualRenderer().render(result, null) + "\n" + "Источник: ".repeat(900);
        var adapter = mock(SpringAiSearchAdapter.class);
        when(adapter.turn(eq(A), anyString(), anyList(), any(), anyList())).thenReturn(
                new AiSearchReply(AiSearchReply.Status.OK, result, text, 1, 1, true, List.of()));
        var service = new ConversationService(store, memory, adapter, clock, selections);
        var bot = mock(TelegramBot.class);
        var requests = new java.util.ArrayList<String>();
        when(bot.execute(any(SendMessage.class))).thenAnswer(call -> {
            assertThat(repository.findCurrent(A)).isEqualTo(old);
            assertThat(memory.get(store.load(A).conversationId())).isEmpty();
            requests.add(((SendMessage) call.getArgument(0)).getText());
            var response = mock(SendResponse.class);
            when(response.isOk()).thenReturn(allSent || requests.size() == 1);
            return response;
        });
        var update = BotUtils.parseUpdate("""
                {"update_id":1,"message":{"message_id":1,"chat":{"id":%d,"type":"private"},"text":"Запрос"}}
                """.formatted(A));
        new TelegramUpdateHandler(bot, service).process(List.of(update));
        assertThat(requests).allSatisfy(part -> assertThat(part.length()).isLessThanOrEqualTo(4096));
        if (allSent) {
            assertThat(String.join("", requests)).isEqualTo(text);
            assertThat(repository.findCurrent(A)).extracting(SelectionItem::restaurantId).containsExactlyElementsOf(result.candidates().stream().map(c -> c.restaurant().id()).toList());
            assertThat(store.load(A).selectionVersion()).isEqualTo(version + 1);
            assertThat(memory.get(store.load(A).conversationId())).hasSize(2);
            assertThat(memory.get(store.load(A).conversationId()).getLast().getText()).isEqualTo(text);
        } else {
            assertThat(requests).hasSize(2);
            assertThat(repository.findCurrent(A)).isEqualTo(old);
            assertThat(store.load(A).selectionVersion()).isEqualTo(version);
            assertThat(memory.get(store.load(A).conversationId())).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"time\":\"25:00\"}", "{}", "abstain", "provider-failure"})
    void invalidClarificationAndNoSearchLeaveSelectionUnchanged(String args) {
        oldSelection();
        var previous = repository.findCurrent(A);
        if (args.equals("abstain")) when(model.call(any(Prompt.class))).thenReturn(response("ignored", "STOP", List.of()));
        else if (args.equals("provider-failure")) when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("Offline provider failure"));
        else when(model.call(any(Prompt.class))).thenReturn(selection(args));
        var reply = conversation.handle(A, "Продолжим", sent -> true);
        assertThat(reply.searchResult()).isNull();
        assertThat(repository.findCurrent(A)).isEqualTo(previous);
        assertThat(store.load(A).selectionVersion()).isEqualTo(previous.getFirst().selectionVersion());
    }

    @Test
    void fallbackExplanationStillPersistsTrustedIdsWithoutMoreModelCalls() {
        conversation = new ConversationService(store, memory, new SpringAiSearchAdapter(model, search, clock, true), clock, selections);
        when(model.call(any(Prompt.class))).thenReturn(selection(FULL), explanation("{\"items\":[]}"));
        var reply = conversation.handle(A, "Двое, общий бюджет 150 BYN, сегодня в 21:00", sent -> true);
        assertThat(reply.explanationFallback()).isTrue();
        assertThat(reply.modelCalls()).isEqualTo(2);
        assertThat(reply.toolExecutions()).isEqualTo(1);
        assertThat(repository.findCurrent(A)).extracting(SelectionItem::restaurantId).containsExactlyElementsOf(reply.searchResult().candidates().stream().map(c -> c.restaurant().id()).toList());
        assertThat(memory.get(store.load(A).conversationId()).getLast().getText()).isEqualTo(reply.text());
    }

    @Test
    void twoChatsReplaceClearAndResetIndependently() {
        oldSelection();
        selections.replace(B, List.of(2L));
        var b = repository.findCurrent(B);
        var bState = store.load(B);
        assertThat(resolver.resolve(A, new RestaurantReference(2, null, null)).restaurantId()).isEqualTo(1L);
        assertThat(resolver.resolve(B, new RestaurantReference(2, null, null)).status()).isEqualTo(NEED_CLARIFICATION);
        selections.replace(A, List.of(1L, 3L));
        assertThat(repository.findCurrent(B)).isEqualTo(b);
        selections.replace(A, List.of());
        assertThat(repository.findCurrent(B)).isEqualTo(b);
        oldSelection();
        var aState = store.load(A);
        store.remember(aState, "User", "Assistant");
        store.saveCriteria(aState, new by.ilya.restaurantbot.search.SearchRequest(2, null, null, null, null, null));
        clearInvocations(model);
        var reset = conversation.handle(A, "/new", sent -> true);
        assertThat(reset.modelCalls()).isZero();
        assertThat(reset.toolExecutions()).isZero();
        verifyNoInteractions(model);
        assertThat(repository.findCurrent(A)).isEmpty();
        assertThat(store.load(A).generation()).isEqualTo(aState.generation() + 1);
        assertThat(store.load(A).currentCriteria()).isEqualTo(CriteriaMerge.empty());
        assertThat(memory.get(aState.conversationId())).isEmpty();
        assertThat(memory.get(store.load(A).conversationId())).isEmpty();
        assertThat(resolver.resolve(A, new RestaurantReference(1, null, null)).status()).isEqualTo(NEED_CLARIFICATION);
        assertThat(repository.findCurrent(B)).isEqualTo(b);
        assertThat(store.load(B)).isEqualTo(bState);
    }

    @Test
    void failedReplacementRollsBackVersionDeletionAndEarlierInsertAndDisablesStalePositions() {
        oldSelection();
        var previous = repository.findCurrent(A);
        long version = store.load(A).selectionVersion();
        assertThatThrownBy(() -> selections.replace(A, List.of(2L, Long.MAX_VALUE)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.findCurrent(A)).isEqualTo(previous);
        assertThat(store.load(A).selectionVersion()).isEqualTo(version);
        assertThat(selections.current(A)).isEmpty();
        assertThat(resolver.resolve(A, new RestaurantReference(1, null, null)).status()).isEqualTo(NEED_CLARIFICATION);
        assertThat(resolver.resolve(A, new RestaurantReference(null, null, "Pizza Tempo")).status()).isEqualTo(NEED_CLARIFICATION);
        selections.replace(A, List.of(2L));
        assertThat(resolver.resolve(A, new RestaurantReference(1, null, null)).restaurantId()).isEqualTo(2L);
        assertThat(store.load(A).selectionVersion()).isEqualTo(version + 1);
    }

    @Test
    void postSendDatabaseFailurePromptsForNameWithoutNewModelOrToolExecution() {
        oldSelection();
        var previous = repository.findCurrent(A);
        var failingRepository = spy(new SelectionItemRepository(jdbc));
        doThrow(new DataIntegrityViolationException("Offline DB failure")).when(failingRepository).insert(any());
        var failingSelections = new SelectionService(failingRepository, jdbc, clock, manager);
        conversation = new ConversationService(store, memory, new SpringAiSearchAdapter(model, search, clock, false), clock, failingSelections);
        var sends = new java.util.ArrayList<AiSearchReply>();
        var reply = deliver(FULL, sent -> { sends.add(sent); return true; });
        assertThat(sends).hasSize(2);
        assertThat(sends.getFirst().searchResult()).isNotNull();
        assertThat(reply.text()).contains("укажите название ресторана");
        assertThat(reply.status()).isEqualTo(AiSearchReply.Status.NEED_CLARIFICATION);
        assertThat(reply.modelCalls()).isEqualTo(1);
        assertThat(reply.toolExecutions()).isEqualTo(1);
        assertThat(repository.findCurrent(A)).isEqualTo(previous);
        assertThat(memory.get(store.load(A).conversationId())).hasSize(2);
        assertThat(memory.get(store.load(A).conversationId()).getLast().getText()).isEqualTo(sends.getFirst().text());
        assertThat(new ReferenceResolver(failingSelections, catalog).resolve(A, new RestaurantReference(1, null, null)).status())
                .isEqualTo(NEED_CLARIFICATION);
        verify(model).call(any(Prompt.class));
        conversation.handle(A, "/new", sent -> true);
        assertThat(repository.findCurrent(A)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"0,1,1", "4,1,1", "1,0,1", "1,-1,1", "1,1,9223372036854775807"})
    void databaseRejectsInvalidPositionVersionOrRestaurantFk(int position, long version, long id) {
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(tx ->
                repository.insert(new SelectionItem(A, position, version, id))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM selection_items WHERE chat_id=?", Integer.class, A)).isZero();
    }

    @Test
    void databaseRejectsUnknownChatAndDuplicatePosition() {
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(tx ->
                repository.insert(new SelectionItem(Long.MIN_VALUE, 1, 1, 2))))
                .isInstanceOf(DataIntegrityViolationException.class);
        oldSelection();
        var previous = repository.findCurrent(A);
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(tx ->
                repository.insert(new SelectionItem(A, 1, previous.getFirst().selectionVersion(), 2))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.findCurrent(A)).isEqualTo(previous);
    }

    @Test
    void selectionSchemaHasOnlyPointersAndRestaurantIndex() {
        var columns = jdbc.query("SELECT * FROM selection_items WHERE 1=0", rs -> {
            var names = new java.util.ArrayList<String>();
            var metadata = rs.getMetaData();
            for (int i = 1; i <= metadata.getColumnCount(); i++) names.add(metadata.getColumnName(i).toLowerCase(java.util.Locale.ROOT));
            return names;
        });
        assertThat(columns).containsExactly("chat_id", "position", "selection_version", "restaurant_id");
        var indexes = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<List<String>>) connection -> {
            var names = new java.util.ArrayList<String>();
            var metadata = connection.getMetaData();
            String table = metadata.storesUpperCaseIdentifiers() ? "SELECTION_ITEMS" : "selection_items";
            try (var rs = metadata.getIndexInfo(null, null, table, false, false)) {
                while (rs.next()) if (rs.getString("INDEX_NAME") != null) names.add(rs.getString("INDEX_NAME").toLowerCase(java.util.Locale.ROOT));
            }
            return names;
        });
        assertThat(indexes).contains("idx_selection_restaurant");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void namesAreRereadFromCatalogAndAmbiguousBranchesAreNeverGuessed() {
        selections.replace(A, List.of(2L));
        jdbc.update("UPDATE restaurants SET name=' New   Name ' WHERE id=2");
        entityManager.clear();
        assertThat(resolver.resolve(A, new RestaurantReference(null, null, "new name")).restaurantId()).isEqualTo(2L);
        assertThat(resolver.resolve(A, new RestaurantReference(null, null, "Pizza Tempo")).status()).isEqualTo(NOT_FOUND);
        jdbc.update("UPDATE restaurants SET name='ВАСИЛЬКИ' WHERE id=2");
        entityManager.clear();
        selections.replace(A, List.of(1L, 2L));
        var name = new RestaurantReference(null, null, "васильки");
        assertThat(resolver.resolve(A, name).status()).isEqualTo(NEED_CLARIFICATION);
        selections.replace(A, List.of());
        assertThat(resolver.resolve(A, name).status()).isEqualTo(NEED_CLARIFICATION);
    }

    @Test
    void oversizedAndDuplicatePendingSelectionsAreRejectedBeforeChangingState() {
        oldSelection();
        var previous = repository.findCurrent(A);
        for (var ids : List.of(List.of(1L, 2L, 3L, 4L), List.of(1L, 1L), List.of(0L))) {
            assertThatThrownBy(() -> selections.replace(A, ids)).isInstanceOf(IllegalArgumentException.class);
            assertThat(repository.findCurrent(A)).isEqualTo(previous);
        }
    }
}

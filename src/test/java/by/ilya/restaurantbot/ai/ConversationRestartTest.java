package by.ilya.restaurantbot.ai;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;

import by.ilya.restaurantbot.RestaurantBotApplication;
import by.ilya.restaurantbot.catalog.Cuisine;
import by.ilya.restaurantbot.catalog.RestaurantTag;
import by.ilya.restaurantbot.conversation.ConversationService;
import by.ilya.restaurantbot.conversation.ConversationState;
import by.ilya.restaurantbot.conversation.ConversationStore;
import by.ilya.restaurantbot.conversation.CriteriaMerge;
import by.ilya.restaurantbot.search.RestaurantSearchService;
import by.ilya.restaurantbot.search.SearchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import javax.sql.DataSource;

import static by.ilya.restaurantbot.ai.AiFixtures.selection;
import static by.ilya.restaurantbot.ai.AiSearchReply.Status.OK;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Closes and recreates the full application context and datasource against the same storage. */
class ConversationRestartTest {
    @Test
    void fullApplicationRestartRestoresBoundedMemoryCriteriaAndGenerationThenSupportsFollowup() throws Exception {
        String url = System.getProperty("spring.datasource.url", "jdbc:h2:mem:restart_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        boolean postgres = url.startsWith("jdbc:postgresql:");
        String username = System.getProperty("spring.datasource.username", postgres ? "postgres" : "sa");
        String driver = postgres ? "org.postgresql.Driver" : "org.h2.Driver";
        String[] args = {"--spring.datasource.url=" + url, "--spring.datasource.username=" + username,
                "--spring.datasource.driver-class-name=" + driver, "--restaurant-bot.ai.enabled=false",
                "--restaurant-bot.telegram.bot-token=", "--spring.main.web-application-type=none"};
        long chat = -9000000007L;
        ConversationState saved;
        Object oldRepository;
        Object oldDataSource;
        try (var first = start(args)) {
            assertVendor(first, postgres);
            var store = first.getBean(ConversationStore.class);
            store.reset(store.load(chat));
            store.reset(store.load(chat));
            var partial = new SearchRequest(2, new BigDecimal("150.00"), "TODAY", "21:00", Cuisine.ITALIAN, Set.of(RestaurantTag.QUIET));
            var criteria = CriteriaMerge.merge(CriteriaMerge.empty(), partial, first.getBean(Clock.class));
            store.saveCriteria(store.load(chat), criteria);
            first.getBean(by.ilya.restaurantbot.conversation.SelectionService.class).replace(chat, java.util.List.of(3L, 1L, 2L));
            saved = store.load(chat);
            for (int i = 0; i < 12; i++) store.remember(saved, "User turn " + i, "Safe reply " + i);
            assertThat(first.getBean(ChatMemory.class).get(saved.conversationId())).hasSize(20);
            oldRepository = first.getBean(JdbcChatMemoryRepository.class);
            oldDataSource = first.getBean(DataSource.class);
        }
        try (var second = start(args)) {
            assertVendor(second, postgres);
            assertThat(second.getBean(JdbcChatMemoryRepository.class)).isNotSameAs(oldRepository);
            assertThat(second.getBean(DataSource.class)).isNotSameAs(oldDataSource);
            var store = second.getBean(ConversationStore.class);
            var restored = store.load(chat);
            assertThat(restored.currentCriteria()).isEqualTo(saved.currentCriteria());
            assertThat(restored.generation()).isEqualTo(saved.generation()).isGreaterThanOrEqualTo(2);
            assertThat(restored.selectionVersion()).isEqualTo(saved.selectionVersion()).isPositive();
            assertThat(second.getBean(by.ilya.restaurantbot.conversation.SelectionService.class).current(chat))
                    .extracting(by.ilya.restaurantbot.conversation.SelectionItem::restaurantId).containsExactly(3L, 1L, 2L);
            assertThat(second.getBean(by.ilya.restaurantbot.conversation.ReferenceResolver.class).resolve(chat,
                    new by.ilya.restaurantbot.conversation.RestaurantReference(2, null, null)).restaurantId()).isEqualTo(1L);
            assertThat(restored.conversationId()).isEqualTo(saved.conversationId());
            var memory = second.getBean(ChatMemory.class);
            assertThat(memory.get(restored.conversationId())).hasSize(20);
            assertThat(memory.get(restored.conversationId()).getFirst().getText()).isEqualTo("User turn 2");
            assertThat(memory.get(restored.conversationId()).getLast().getText()).isEqualTo("Safe reply 11");
            var model = mock(ChatModel.class);
            when(model.call(any(Prompt.class))).thenReturn(selection("{\"guests\":4}"));
            var clock = second.getBean(Clock.class);
            var adapter = new SpringAiSearchAdapter(model, second.getBean(RestaurantSearchService.class), clock, false);
            var conversation = new ConversationService(store, memory, adapter, clock, second.getBean(by.ilya.restaurantbot.conversation.SelectionService.class));
            var reply = conversation.handle(chat, "А если нас четверо?", sent -> true);
            assertThat(reply.status()).isEqualTo(OK);
            assertThat(reply.text()).contains("130.80 BYN");
            assertThat(store.load(chat).currentCriteria().guests()).isEqualTo(4);
            conversation.handle(chat, "/new", sent -> true);
            assertThat(memory.get(restored.conversationId())).isEmpty();
            assertThat(store.load(chat).generation()).isEqualTo(saved.generation() + 1);
            assertThat(store.load(chat).currentCriteria()).isEqualTo(CriteriaMerge.empty());
            assertThat(second.getBean(by.ilya.restaurantbot.conversation.SelectionService.class).current(chat)).isEmpty();
        }
    }

    private ConfigurableApplicationContext start(String[] args) {
        return new SpringApplicationBuilder(RestaurantBotApplication.class).profiles("test").run(args);
    }

    private void assertVendor(ConfigurableApplicationContext context, boolean postgres) throws Exception {
        try (var connection = context.getBean(DataSource.class).getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo(postgres ? "PostgreSQL" : "H2");
        }
    }
}

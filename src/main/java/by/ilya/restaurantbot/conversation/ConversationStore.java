package by.ilya.restaurantbot.conversation;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;

import by.ilya.restaurantbot.search.SearchRequest;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Short database transactions only; neither the model nor Telegram is called here. */
@Repository
public class ConversationStore {
    private final JdbcTemplate jdbc;
    private final ChatMemory memory;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public ConversationStore(JdbcTemplate jdbc, ChatMemory memory, Clock clock, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.memory = memory;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
    }

    public ConversationState load(long chatId) {
        var rows = jdbc.query("SELECT * FROM conversation_state WHERE chat_id = ?", (rs, row) ->
                new ConversationState(rs.getLong("chat_id"), rs.getLong("generation"),
                        decode(rs.getString("current_criteria")), rs.getLong("selection_version"),
                        rs.getTimestamp("updated_at").toInstant()), chatId);
        return rows.isEmpty() ? new ConversationState(chatId, 0, CriteriaMerge.empty(), 0, clock.instant()) : rows.getFirst();
    }

    public void saveCriteria(ConversationState state, SearchRequest criteria) {
        transactions.executeWithoutResult(tx -> save(new ConversationState(state.chatId(), state.generation(),
                criteria, state.selectionVersion(), clock.instant())));
    }

    public void remember(ConversationState state, String userText, String sentText) {
        transactions.executeWithoutResult(tx -> memory.add(state.conversationId(),
                List.of(new UserMessage(userText), new AssistantMessage(sentText))));
    }

    public void reset(ConversationState state) {
        transactions.executeWithoutResult(tx -> {
            memory.clear(state.conversationId());
            save(new ConversationState(state.chatId(), Math.incrementExact(state.generation()),
                    CriteriaMerge.empty(), state.selectionVersion(), clock.instant()));
        });
    }

    private void save(ConversationState state) {
        String json;
        try { json = mapper.writeValueAsString(state.currentCriteria()); }
        catch (Exception invalid) { throw new IllegalStateException("Cannot encode conversation criteria"); }
        var timestamp = Timestamp.from(state.updatedAt());
        int changed = jdbc.update("UPDATE conversation_state SET generation=?, current_criteria=?, selection_version=?, updated_at=? WHERE chat_id=?",
                state.generation(), json, state.selectionVersion(), timestamp, state.chatId());
        if (changed == 0) jdbc.update("INSERT INTO conversation_state(chat_id,generation,current_criteria,selection_version,updated_at) VALUES (?,?,?,?,?)",
                state.chatId(), state.generation(), json, state.selectionVersion(), timestamp);
    }

    private SearchRequest decode(String json) {
        try { return mapper.readValue(json, SearchRequest.class); }
        catch (Exception invalid) { throw new IllegalStateException("Cannot decode conversation criteria"); }
    }
}

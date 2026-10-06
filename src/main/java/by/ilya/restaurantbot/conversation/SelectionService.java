package by.ilya.restaurantbot.conversation;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SelectionService {
    private final SelectionItemRepository repository;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate transactions;
    // Only failed post-send commits enter this set. Never resolve their stale persisted positions.
    private final Set<Long> uncertainChats = ConcurrentHashMap.newKeySet();

    public SelectionService(SelectionItemRepository repository, JdbcTemplate jdbc, Clock clock,
                            PlatformTransactionManager manager) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
    }

    public List<SelectionItem> current(long chatId) {
        return uncertainChats.contains(chatId) ? List.of() : repository.findCurrent(chatId);
    }

    /** Called only after delivery. Empty IDs represent a successfully delivered empty search. */
    public void replace(long chatId, List<Long> shownRestaurantIds) {
        var ids = List.copyOf(shownRestaurantIds);
        if (ids.size() > 3 || ids.stream().anyMatch(id -> id <= 0) || ids.stream().distinct().count() != ids.size()) {
            throw new IllegalArgumentException("Selection must contain at most three distinct catalog IDs");
        }
        try {
            transactions.executeWithoutResult(tx -> {
                long previous = jdbc.queryForObject(
                        "SELECT selection_version FROM conversation_state WHERE chat_id = ? FOR UPDATE", Long.class, chatId);
                long version = Math.incrementExact(previous);
                jdbc.update("UPDATE conversation_state SET selection_version = ?, updated_at = ? WHERE chat_id = ?",
                        version, Timestamp.from(clock.instant()), chatId);
                repository.delete(chatId);
                for (int i = 0; i < ids.size(); i++) {
                    repository.insert(new SelectionItem(chatId, i + 1, version, ids.get(i)));
                }
            });
            uncertainChats.remove(chatId);
        } catch (RuntimeException failure) {
            uncertainChats.add(chatId);
            throw failure;
        }
    }

    public void afterReset(long chatId) {
        uncertainChats.remove(chatId);
    }
}

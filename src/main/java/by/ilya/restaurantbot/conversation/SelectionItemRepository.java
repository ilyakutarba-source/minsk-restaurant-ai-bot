package by.ilya.restaurantbot.conversation;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SelectionItemRepository {
    private final JdbcTemplate jdbc;

    public SelectionItemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SelectionItem> findCurrent(long chatId) {
        return jdbc.query("""
                SELECT s.* FROM selection_items s
                JOIN conversation_state c ON c.chat_id = s.chat_id AND c.selection_version = s.selection_version
                WHERE s.chat_id = ? ORDER BY s.position
                """, (rs, row) -> new SelectionItem(rs.getLong("chat_id"), rs.getInt("position"),
                rs.getLong("selection_version"), rs.getLong("restaurant_id")), chatId);
    }

    public void delete(long chatId) {
        jdbc.update("DELETE FROM selection_items WHERE chat_id = ?", chatId);
    }

    public void insert(SelectionItem item) {
        jdbc.update("INSERT INTO selection_items(chat_id,position,selection_version,restaurant_id) VALUES (?,?,?,?)",
                item.chatId(), item.position(), item.selectionVersion(), item.restaurantId());
    }
}

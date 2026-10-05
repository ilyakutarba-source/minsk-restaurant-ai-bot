package by.ilya.restaurantbot.conversation;

import java.time.Instant;
import by.ilya.restaurantbot.search.SearchRequest;

public record ConversationState(long chatId, long generation, SearchRequest currentCriteria,
                                long selectionVersion, Instant updatedAt) {
    public String conversationId() { return "telegram:" + chatId + ":" + generation; }
}

package by.ilya.restaurantbot.ai;

import java.util.List;

import by.ilya.restaurantbot.search.SearchResult;

/** Java-owned result for application transports; never a provider response. */
public record AiSearchReply(Status status, SearchResult searchResult, String text,
                            int modelCalls, int toolExecutions, boolean explanationFallback,
                            List<String> missingFields, RestaurantFollowUpResult followUpResult) {
    public AiSearchReply(Status status, SearchResult searchResult, String text, int modelCalls,
                         int toolExecutions, boolean explanationFallback, List<String> missingFields) {
        this(status, searchResult, text, modelCalls, toolExecutions, explanationFallback, missingFields, null);
    }
    public enum Status {
        OK, NO_RESULTS, NEED_CLARIFICATION, NOT_FOUND, DATA_UNAVAILABLE, INVALID_INPUT, TEMPORARILY_UNAVAILABLE
    }
}

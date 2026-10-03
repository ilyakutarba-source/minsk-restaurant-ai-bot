package by.ilya.restaurantbot.search;

import java.math.BigDecimal;
import java.util.List;

import by.ilya.restaurantbot.catalog.RestaurantDetails;

public record SearchResult(SearchCriteria normalizedCriteria, String currency,
                           List<Candidate> candidates, List<String> warnings) {
    public record Candidate(RestaurantDetails restaurant, BigDecimal estimatedTotalByn,
                            int matchCount, List<ReasonCode> allowedReasonCodes) {
    }

    public enum ReasonCode {
        CUISINE_MATCH, BUDGET_MATCH, HOURS_MATCH,
        COZY_TAG_MATCH, QUIET_TAG_MATCH, ROMANTIC_TAG_MATCH,
        CASUAL_TAG_MATCH, FRIENDS_TAG_MATCH, PREMIUM_TAG_MATCH
    }
}

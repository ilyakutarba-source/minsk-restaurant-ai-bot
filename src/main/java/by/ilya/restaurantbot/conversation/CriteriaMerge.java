package by.ilya.restaurantbot.conversation;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import by.ilya.restaurantbot.search.SearchCriteria;
import by.ilya.restaurantbot.search.SearchRequest;

/** Uses the search contract for supplied values, normalization and complete validation. */
public final class CriteriaMerge {
    private CriteriaMerge() { }

    public static SearchRequest empty() {
        return new SearchRequest(null, null, null, null, null, Set.of());
    }

    public static SearchRequest merge(SearchRequest previous, SearchRequest supplied, Clock clock) {
        var normalized = SearchCriteria.normalize(new SearchRequest(
                supplied.guests() == null ? 1 : supplied.guests(),
                supplied.totalBudgetByn() == null ? BigDecimal.ONE : supplied.totalBudgetByn(),
                supplied.date() == null ? "TODAY" : supplied.date(),
                supplied.time() == null ? "12:00" : supplied.time(), supplied.cuisine(), supplied.preferredTags()), clock);
        var merged = new SearchRequest(
                supplied.guests() == null ? previous.guests() : normalized.guests(),
                supplied.totalBudgetByn() == null ? previous.totalBudgetByn() : normalized.totalBudgetByn(),
                supplied.date() == null ? previous.date() : normalized.date().toString(),
                supplied.time() == null ? previous.time() : normalized.time().toString(),
                supplied.cuisine() == null ? previous.cuisine() : supplied.cuisine(),
                supplied.preferredTags() == null ? previous.preferredTags() : normalized.preferredTags());
        if (missing(merged).isEmpty()) SearchCriteria.normalize(merged, clock);
        return merged;
    }

    public static List<String> missing(SearchRequest criteria) {
        var missing = new ArrayList<String>();
        if (criteria.guests() == null) missing.add("guests");
        if (criteria.totalBudgetByn() == null) missing.add("totalBudgetByn");
        if (criteria.date() == null) missing.add("date");
        if (criteria.time() == null) missing.add("time");
        return List.copyOf(missing);
    }
}

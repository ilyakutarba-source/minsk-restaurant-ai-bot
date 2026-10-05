package by.ilya.restaurantbot.ai;

import java.util.HashSet;
import java.util.List;

import by.ilya.restaurantbot.search.SearchResult;
import by.ilya.restaurantbot.search.SearchResult.ReasonCode;

public record ExplanationPlan(List<Item> items) {
    public record Item(Integer position, List<ReasonCode> reasonCodes, Phrasing phrasing) { }
    public enum Phrasing { NEUTRAL, WARM, COMPACT }

    boolean isValidFor(SearchResult result) {
        if (items == null || items.size() != result.candidates().size() || items.isEmpty()) return false;
        var seen = new HashSet<Integer>();
        for (var item : items) {
            if (item == null || item.position() == null || item.position() < 1
                    || item.position() > result.candidates().size() || !seen.add(item.position())
                    || item.phrasing() == null || item.reasonCodes() == null
                    || item.reasonCodes().isEmpty() || item.reasonCodes().size() > 2
                    || item.reasonCodes().stream().anyMatch(java.util.Objects::isNull)
                    || new HashSet<>(item.reasonCodes()).size() != item.reasonCodes().size()
                    || !result.candidates().get(item.position() - 1).allowedReasonCodes()
                        .containsAll(item.reasonCodes())) return false;
        }
        return true;
    }
}

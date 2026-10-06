package by.ilya.restaurantbot.conversation;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import by.ilya.restaurantbot.catalog.RestaurantService;
import org.springframework.stereotype.Service;

/** Deterministic Java resolution. Text memory, LLM and transport are not data sources. */
@Service
public class ReferenceResolver {
    public enum Status { OK, NEED_CLARIFICATION, NOT_FOUND, INVALID_INPUT }
    public record Resolution(Status status, Long restaurantId) {
    }

    private final SelectionService selections;
    private final RestaurantService catalog;

    public ReferenceResolver(SelectionService selections, RestaurantService catalog) {
        this.selections = selections;
        this.catalog = catalog;
    }

    public Resolution resolve(long chatId, RestaurantReference reference) {
        if (reference == null || !reference.isValid()) return unresolved(Status.INVALID_INPUT);
        var current = selections.current(chatId);
        if (reference.ordinal() != null || reference.last() != null) {
            if (current.isEmpty()) return unresolved(Status.NEED_CLARIFICATION);
            int position = reference.ordinal() != null ? reference.ordinal() : current.getLast().position();
            return current.stream().filter(item -> item.position() == position)
                    .map(item -> new Resolution(Status.OK, item.restaurantId())).findFirst()
                    .orElseGet(() -> unresolved(Status.NEED_CLARIFICATION));
        }
        Set<Long> selectedIds = current.stream().map(SelectionItem::restaurantId).collect(Collectors.toSet());
        String name = normalize(reference.name());
        var matches = catalog.getRestaurantNames().stream()
                .filter(item -> current.isEmpty() || selectedIds.contains(item.id()))
                .filter(item -> normalize(item.name()).equals(name)).toList();
        if (matches.isEmpty()) return unresolved(Status.NOT_FOUND);
        if (matches.size() > 1) return unresolved(Status.NEED_CLARIFICATION);
        return new Resolution(Status.OK, matches.getFirst().id());
    }

    private static String normalize(String name) {
        return name.strip().replaceAll("(?U)\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static Resolution unresolved(Status status) {
        return new Resolution(status, null);
    }
}

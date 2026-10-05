package by.ilya.restaurantbot.ai;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import by.ilya.restaurantbot.search.SearchResult;
import by.ilya.restaurantbot.search.SearchResult.ReasonCode;

/** All visible text, including explanation phrases, is owned by Java. */
public final class SearchFactualRenderer {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public String render(SearchResult result, ExplanationPlan plan) {
        if (result.candidates().isEmpty()) {
            return "В сохранённом каталоге нет ресторанов, подходящих под все заданные условия. "
                    + "Можно изменить критерии и выполнить новый поиск.";
        }
        boolean valid = plan != null && plan.isValidFor(result);
        var text = new StringBuilder();
        for (int i = 0; i < result.candidates().size(); i++) {
            var candidate = result.candidates().get(i);
            var restaurant = candidate.restaurant();
            int position = i + 1;
            var item = valid ? plan.items().stream().filter(p -> p.position() == position).findFirst().orElseThrow()
                    : new ExplanationPlan.Item(position,
                        candidate.allowedReasonCodes().stream().limit(2).toList(), ExplanationPlan.Phrasing.NEUTRAL);
            text.append(position).append(". ").append(restaurant.name()).append(" (ID: ")
                    .append(restaurant.id()).append(")\nАдрес: ").append(restaurant.address())
                    .append("\nОриентировочный чек на ").append(result.normalizedCriteria().guests())
                    .append(" гостей: ").append(candidate.estimatedTotalByn().toPlainString()).append(" BYN")
                    .append("; на гостя: ").append(restaurant.estimatedCheckPerGuest().toPlainString()).append(" BYN.")
                    .append("\nТип оценки: ").append(restaurant.checkEstimationType())
                    .append("; проверено: ").append(restaurant.checkVerifiedAt())
                    .append("\nИсточник чека: ").append(restaurant.checkSource())
                    .append("\nПо сохранённому расписанию открыт ").append(result.normalizedCriteria().date())
                    .append(" в ").append(TIME.format(result.normalizedCriteria().time())).append(" (Europe/Minsk).")
                    .append("\nРасписание: ").append(restaurant.openingIntervals().stream()
                        .map(h -> h.weekday() + " " + TIME.format(h.opensAt()) + "–" + TIME.format(h.closesAt())
                            + (h.closesNextDay() ? " следующего дня" : ""))
                        .collect(Collectors.joining("; ")))
                    .append("\nИсточник часов: ").append(restaurant.hoursSource())
                    .append("; проверено: ").append(restaurant.hoursVerifiedAt())
                    .append("\n").append(explanation(item)).append("\n\n");
        }
        text.append(String.join("\n", result.warnings()));
        return text.toString().strip();
    }

    private static String explanation(ExplanationPlan.Item item) {
        String prefix = switch (item.phrasing()) {
            case NEUTRAL -> "Причины: ";
            case WARM -> "Может подойти: ";
            case COMPACT -> "Соответствие: ";
        };
        return prefix + item.reasonCodes().stream().map(SearchFactualRenderer::reason).collect(Collectors.joining("; ")) + ".";
    }

    private static String reason(ReasonCode code) {
        return switch (code) {
            case CUISINE_MATCH -> "выбранная кухня";
            case BUDGET_MATCH -> "ориентировочная сумма в пределах общего бюджета";
            case HOURS_MATCH -> "время прибытия входит в сохранённые часы работы";
            case COZY_TAG_MATCH -> "совпадает запрошенный тег «уютно»";
            case QUIET_TAG_MATCH -> "совпадает запрошенный тег «спокойно»";
            case ROMANTIC_TAG_MATCH -> "совпадает запрошенный тег «романтично»";
            case CASUAL_TAG_MATCH -> "совпадает запрошенный тег «неформально»";
            case FRIENDS_TAG_MATCH -> "совпадает запрошенный тег «с друзьями»";
            case PREMIUM_TAG_MATCH -> "совпадает запрошенный тег «премиум»";
        };
    }
}

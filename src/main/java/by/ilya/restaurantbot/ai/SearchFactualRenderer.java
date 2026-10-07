package by.ilya.restaurantbot.ai;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import by.ilya.restaurantbot.catalog.Cuisine;
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
        var text = new StringBuilder("Подборка на ").append(result.normalizedCriteria().date())
                .append(" в ").append(TIME.format(result.normalizedCriteria().time()))
                .append(" · гостей: ").append(result.normalizedCriteria().guests()).append("\n\n");
        for (int i = 0; i < result.candidates().size(); i++) {
            var candidate = result.candidates().get(i);
            var restaurant = candidate.restaurant();
            int position = i + 1;
            var item = valid ? plan.items().stream().filter(p -> p.position() == position).findFirst().orElseThrow()
                    : new ExplanationPlan.Item(position,
                        candidate.allowedReasonCodes().stream().limit(2).toList(), ExplanationPlan.Phrasing.NEUTRAL);
            text.append(position).append(". 🍽 ").append(restaurant.name())
                    .append("\n📍 ").append(restaurant.address())
                    .append("\n💰 ≈").append(candidate.estimatedTotalByn().toPlainString())
                    .append(" BYN на ").append(result.normalizedCriteria().guests()).append(" гостей")
                    .append("\n🟢 Открыто по расписанию к ")
                    .append(TIME.format(result.normalizedCriteria().time()))
                    .append("\n\n").append(explanation(item, result.normalizedCriteria().cuisine())).append("\n\n");
        }
        text.append(String.join("\n", result.warnings()));
        text.append("\n\nПодробнее: «Подробности первого» или «Меню первого» — по номеру варианта.");
        return text.toString().strip();
    }

    private static String explanation(ExplanationPlan.Item item, Cuisine cuisine) {
        String prefix = switch (item.phrasing()) {
            case NEUTRAL -> "Почему подходит:";
            case WARM -> "Может подойти:";
            case COMPACT -> "Соответствие:";
        };
        return prefix + item.reasonCodes().stream().map(code -> "\n• " + reason(code, cuisine))
                .collect(Collectors.joining());
    }

    private static String reason(ReasonCode code, Cuisine cuisine) {
        return switch (code) {
            case CUISINE_MATCH -> switch (cuisine) {
                case BELARUSIAN -> "белорусская кухня";
                case ITALIAN -> "итальянская кухня";
                case GEORGIAN -> "грузинская кухня";
                case EUROPEAN -> "европейская кухня";
                case ASIAN -> "азиатская кухня";
            };
            case BUDGET_MATCH -> "входит в бюджет по ориентировочному чеку";
            case HOURS_MATCH -> "работает в выбранное время по расписанию";
            case COZY_TAG_MATCH -> "совпадает запрошенный тег «уютно»";
            case QUIET_TAG_MATCH -> "совпадает запрошенный тег «спокойно»";
            case ROMANTIC_TAG_MATCH -> "совпадает запрошенный тег «романтично»";
            case CASUAL_TAG_MATCH -> "совпадает запрошенный тег «неформально»";
            case FRIENDS_TAG_MATCH -> "совпадает запрошенный тег «с друзьями»";
            case PREMIUM_TAG_MATCH -> "совпадает запрошенный тег «премиум»";
        };
    }
}

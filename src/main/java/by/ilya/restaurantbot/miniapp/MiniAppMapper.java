package by.ilya.restaurantbot.miniapp;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import by.ilya.restaurantbot.catalog.Cuisine;
import by.ilya.restaurantbot.catalog.MenuDetails;
import by.ilya.restaurantbot.catalog.RestaurantDetails;
import by.ilya.restaurantbot.search.SearchResult;

/** Presentation mapping only; eligibility and ranking belong to RestaurantSearchService. */
final class MiniAppMapper {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<String> DAYS = List.of("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс");

    static MiniAppRestaurantCard card(SearchResult.Candidate candidate, SearchResult result) {
        var r = candidate.restaurant();
        var reasons = candidate.allowedReasonCodes().stream().map(reason -> switch (reason) {
            case CUISINE_MATCH -> "Подходит выбранная кухня";
            case BUDGET_MATCH -> "Входит в бюджет по ориентировочному чеку";
            case HOURS_MATCH -> "Работает в выбранное время по расписанию";
            case COZY_TAG_MATCH -> "Совпадает пожелание «уютно» по тегу каталога";
            case QUIET_TAG_MATCH -> "Совпадает пожелание «спокойно» по тегу каталога";
            case ROMANTIC_TAG_MATCH -> "Совпадает пожелание романтической обстановки по тегу каталога";
            case CASUAL_TAG_MATCH -> "Совпадает пожелание повседневной обстановки по тегу каталога";
            case FRIENDS_TAG_MATCH -> "Совпадает пожелание «с друзьями» по тегу каталога";
            case PREMIUM_TAG_MATCH -> "Совпадает пожелание премиальной обстановки по тегу каталога";
        }).toList();
        return new MiniAppRestaurantCard(r.id(), r.name(), r.address(), cuisines(r.cuisines()),
                candidate.estimatedTotalByn(), "Открыто по расписанию к " + TIME.format(result.normalizedCriteria().time()),
                reasons);
    }

    static MiniAppRestaurantDetails details(RestaurantDetails r) {
        var hours = r.openingIntervals().stream().map(h -> DAYS.get(h.weekday().getValue() - 1)
                + ": " + TIME.format(h.opensAt()) + "–" + TIME.format(h.closesAt())
                + (h.closesNextDay() ? " следующего дня" : "")).toList();
        return new MiniAppRestaurantDetails(r.id(), r.name(), r.address(), cuisines(r.cuisines()),
                r.estimatedCheckPerGuest(), hours, List.of(
                    new MiniAppRestaurantDetails.Evidence("Сведения о заведении", sourceUrl(r.catalogSource()), r.catalogVerifiedAt()),
                    new MiniAppRestaurantDetails.Evidence("Ориентир чека", sourceUrl(r.checkSource()), r.checkVerifiedAt()),
                    new MiniAppRestaurantDetails.Evidence("Расписание", sourceUrl(r.hoursSource()), r.hoursVerifiedAt())));
    }

    static MiniAppMenuResponse menu(MenuDetails menu) {
        boolean available = menu.status() == MenuDetails.Status.AVAILABLE;
        return new MiniAppMenuResponse(available, available
                ? "Сохранена только часть меню. Наличие блюд и актуальность цен не гарантируются."
                : "Для этого заведения сохранённое меню пока недоступно.",
                menu.items().stream().map(i -> new MiniAppMenuResponse.Item(i.name(), i.priceByn(), i.portion())).toList());
    }

    private static List<String> cuisines(Set<Cuisine> cuisines) {
        return cuisines.stream().sorted().map(c -> switch (c) {
            case BELARUSIAN -> "Белорусская";
            case ITALIAN -> "Итальянская";
            case GEORGIAN -> "Грузинская";
            case EUROPEAN -> "Европейская";
            case ASIAN -> "Азиатская";
        }).toList();
    }

    private static String sourceUrl(String provenance) {
        if (provenance == null) return null;
        // Stored check provenance may append curator methodology after the source URL.
        var match = java.util.regex.Pattern.compile("https?://[^\\s;]+").matcher(provenance);
        return match.find() ? match.group() : null;
    }
}

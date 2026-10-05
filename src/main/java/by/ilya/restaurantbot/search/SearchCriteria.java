package by.ilya.restaurantbot.search;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import by.ilya.restaurantbot.catalog.Cuisine;
import by.ilya.restaurantbot.catalog.RestaurantTag;

public record SearchCriteria(int guests, BigDecimal totalBudgetByn, LocalDate date, LocalTime time,
                             Cuisine cuisine, Set<RestaurantTag> preferredTags) {
    public static final ZoneId MINSK = ZoneId.of("Europe/Minsk");

    public static SearchCriteria normalize(SearchRequest input, Clock clock) {
        if (input == null || input.guests() == null || input.guests() < 1 || input.guests() > 6) {
            throw new IllegalArgumentException("Guests must be an integer between 1 and 6");
        }
        var budget = input.totalBudgetByn();
        if (budget == null || budget.signum() <= 0 || budget.scale() > 2) {
            throw new IllegalArgumentException("A positive total BYN budget with up to two decimal places is required");
        }
        if (input.date() == null || input.time() == null) {
            throw new IllegalArgumentException("Date and time are required");
        }
        var today = LocalDate.now(clock.withZone(MINSK));
        LocalDate date;
        LocalTime time;
        try {
            date = switch (input.date().trim().toUpperCase(Locale.ROOT)) {
                case "TODAY" -> today;
                case "TOMORROW" -> today.plusDays(1);
                default -> LocalDate.parse(input.date().trim());
            };
            var timeText = input.time().trim();
            if (!timeText.matches("[0-9]{2}:[0-9]{2}")) {
                throw new IllegalArgumentException("Arrival time must use HH:mm");
            }
            time = LocalTime.parse(timeText);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date or arrival time");
        }
        if (date.isBefore(today) || date.isAfter(today.plusDays(6))) {
            throw new IllegalArgumentException("Date must be today or within the following six days in Minsk");
        }
        var tags = EnumSet.noneOf(RestaurantTag.class);
        if (input.preferredTags() != null) {
            if (input.preferredTags().stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("Preferred tags must be supported values");
            }
            tags.addAll(input.preferredTags());
        }
        return new SearchCriteria(input.guests(), budget.setScale(2), date, time, input.cuisine(),
                Collections.unmodifiableSet(tags));
    }
}

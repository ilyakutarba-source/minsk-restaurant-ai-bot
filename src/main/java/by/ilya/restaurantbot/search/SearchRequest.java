package by.ilya.restaurantbot.search;

import java.math.BigDecimal;
import java.util.Set;

import by.ilya.restaurantbot.catalog.Cuisine;
import by.ilya.restaurantbot.catalog.RestaurantTag;
import io.swagger.v3.oas.annotations.media.Schema;

/** A complete visit request; conversation merging belongs to the caller. */
public record SearchRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "6") Integer guests,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Positive total budget for all guests in BYN, up to two decimal places") BigDecimal totalBudgetByn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ISO date, TODAY or TOMORROW in Europe/Minsk") String date,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Minsk local arrival time HH:mm", example = "21:00") String time,
        Cuisine cuisine,
        Set<RestaurantTag> preferredTags) {
}

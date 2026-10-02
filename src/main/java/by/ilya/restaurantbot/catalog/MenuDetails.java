package by.ilya.restaurantbot.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MenuDetails(long restaurantId, Status status, MenuCoverage coverage, String source,
                          LocalDate verifiedAt, String currency, String notice, List<Item> items) {
    public enum Status { AVAILABLE, NO_RESULTS, DATA_UNAVAILABLE }

    public record Item(long id, String name, MenuCategory category, DishType dishType, BigDecimal priceByn,
                       String portion, String description, String source) {
    }
}

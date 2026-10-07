package by.ilya.restaurantbot.ingestion.firecrawl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Unapproved source material. EXTRACTED means complete extraction, never curator approval. */
public record RestaurantImportCandidate(String name, String address, List<String> cuisines,
        String openingHours, String menuUrl, List<MenuItem> menuItems, String sourceUrl,
        Instant fetchedAt, ExtractionStatus extractionStatus, List<String> missingFields) {
    public RestaurantImportCandidate {
        cuisines = List.copyOf(cuisines);
        menuItems = List.copyOf(menuItems);
        missingFields = List.copyOf(missingFields);
    }

    public enum ExtractionStatus { EXTRACTED, PARTIAL, INVALID }

    public record MenuItem(String name, BigDecimal priceByn) { }
}

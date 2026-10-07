package by.ilya.restaurantbot.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Manually reviewed, add-only catalog input. Never consumes Firecrawl candidate JSON. */
public record CuratedCatalogDataset(List<Venue> venues) {
    public static CuratedCatalogDataset read(InputStream input) throws IOException {
        var mapper = new ObjectMapper().findAndRegisterModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        var dataset = mapper.readValue(input, CuratedCatalogDataset.class);
        if (dataset == null) throw new IllegalArgumentException("Catalog dataset is required");
        dataset.validate();
        return dataset;
    }

    public void validate() {
        if (venues == null || venues.isEmpty()) throw new IllegalArgumentException("Venues are required");
        var keys = new HashSet<String>();
        var identities = new HashSet<Identity>();
        for (var venue : venues) {
            if (venue == null) throw new IllegalArgumentException("Null venue");
            text(venue.seedKey(), 100);
            text(venue.name(), 200);
            text(venue.address(), 300);
            if (!keys.add(venue.seedKey()) || !identities.add(identity(venue.name(), venue.address()))) {
                throw new IllegalArgumentException("Duplicate venue key or normalized name/address");
            }
            if (!"MANUALLY_VERIFIED".equals(venue.reviewStatus()) || !Boolean.TRUE.equals(venue.active())
                    || !venue.address().startsWith("Минск, ") || venue.cuisines() == null
                    || venue.cuisines().isEmpty() || venue.cuisines().stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Only reviewed active Minsk venues with cuisine may be imported");
            }
            MenuDataset.requireSource(venue.catalogSource());
            date(venue.catalogVerifiedAt());
            var check = venue.check();
            if (check == null || check.type() == null) throw new IllegalArgumentException("Check is required");
            MenuDataset.requirePrice(check.amount());
            MenuDataset.requireSource(check.source());
            date(check.verifiedAt());
            text(check.explanation(), 1500);
            text(check.provenance(), 2000);
            MenuDataset.requireSource(venue.hoursSource());
            date(venue.hoursVerifiedAt());
            if (venue.openingIntervals() == null || venue.openingIntervals().isEmpty()) {
                throw new IllegalArgumentException("Opening hours are required");
            }
            var starts = new HashSet<String>();
            for (var hours : venue.openingIntervals()) {
                if (hours == null || hours.closesNextDay() == null) throw new IllegalArgumentException("Null hours");
                new OpeningInterval(hours.weekday(), hours.opensAt(), hours.closesAt(), hours.closesNextDay());
                if (!starts.add(hours.weekday() + "/" + hours.opensAt())) {
                    throw new IllegalArgumentException("Duplicate opening interval");
                }
            }
            if (venue.menu() != null) {
                if (!venue.seedKey().equals(venue.menu().restaurantSeedKey())) {
                    throw new IllegalArgumentException("Menu belongs to a different venue");
                }
                new MenuDataset(List.of(venue.menu())).validate();
            }
        }
    }

    private static void text(String value, int max) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || value.length() > max) {
            throw new IllegalArgumentException("Missing, untrimmed or oversized catalog field");
        }
    }

    private static void date(LocalDate date) {
        // Dataset verification is explicit; neither the importer nor migration assigns today's date.
        if (date == null) throw new IllegalArgumentException("Verification date is required");
    }

    public static Identity identity(String name, String address) {
        return new Identity(normalized(name), normalized(address));
    }

    private static String normalized(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replace('ё', 'е').replaceAll("\\p{P}+", " ").replaceAll("[\\p{Z}\\s]+", " ").strip();
    }

    public record Identity(String name, String address) { }
    public record Check(BigDecimal amount, CheckEstimationType type, String source,
                        LocalDate verifiedAt, String explanation) {
        public String provenance() { return source + " — " + explanation; }
    }
    public record Hours(DayOfWeek weekday, LocalTime opensAt, LocalTime closesAt, Boolean closesNextDay) { }
    public record Venue(String seedKey, String name, String address, Boolean active, String reviewStatus,
                        Set<Cuisine> cuisines, String catalogSource, LocalDate catalogVerifiedAt,
                        Check check, String hoursSource, LocalDate hoursVerifiedAt,
                        List<Hours> openingIntervals, MenuDataset.RestaurantMenu menu) { }
}

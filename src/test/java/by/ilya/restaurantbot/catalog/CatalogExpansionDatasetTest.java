package by.ilya.restaurantbot.catalog;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CatalogExpansionDatasetTest {
    @Test void reviewedDatasetHasOnlySixApprovedVenuesAndTwelveExactPartialItems() throws Exception {
        var data = CatalogExpansionMigrationTest.dataset();
        assertThat(data.venues()).hasSize(6);
        assertThat(data.venues().stream().map(CuratedCatalogDataset.Venue::seedKey))
                .containsExactlyInAnyOrderElementsOf(CatalogExpansionMigrationTest.BRANDS.keySet());
        var checks = Map.of("la-scala-nemiga-36", "76.00", "mziuri-pritytskogo-97b", "41.00",
                "pekaridze-platonova-45", "39.00", "sorrento-internatsionalnaya-38", "65.00",
                "da-claudio-karla-marksa-25", "60.00", "yangtze-svobody-8", "43.00");
        var prices = Map.ofEntries(Map.entry("la-scala-linguine-turiddu", "52.00"), Map.entry("la-scala-tomato-cream-soup", "24.00"),
                Map.entry("mziuri-odjahuri", "24.00"), Map.entry("mziuri-kharcho", "17.00"),
                Map.entry("pekaridze-ostri-beef", "24.00"), Map.entry("pekaridze-kharcho", "15.00"),
                Map.entry("sorrento-spaghetti-carbonara", "45.00"), Map.entry("sorrento-chicken-noodle-soup", "20.00"),
                Map.entry("da-claudio-tagliatelle-sausage-mushroom", "39.00"), Map.entry("da-claudio-pea-shrimp-soup", "21.00"),
                Map.entry("yangtze-gong-bao-chicken", "30.00"), Map.entry("yangtze-chicken-shiitake-soup", "10.00"));
        var keys = new HashSet<String>();
        for (var v : data.venues()) {
            assertThat(v.check().amount()).isEqualByComparingTo(checks.get(v.seedKey()));
            assertThat(v.check().type()).isEqualTo(CheckEstimationType.DERIVED);
            assertThat(v.openingIntervals()).hasSize(7);
            assertThat(v.openingIntervals().stream().map(CuratedCatalogDataset.Hours::weekday)).doesNotHaveDuplicates();
            assertThat(v.menu().coverage()).isEqualTo(MenuCoverage.PARTIAL);
            assertThat(v.menu().items()).hasSize(2);
            for (var item : v.menu().items()) {
                assertThat(keys.add(item.seedKey())).isTrue();
                assertThat(item.priceByn()).isEqualByComparingTo(prices.get(item.seedKey()));
                assertThat(item.active()).isTrue();
                assertThat(item.source()).startsWith("https://");
                if (v.seedKey().startsWith("la-scala") || v.seedKey().startsWith("sorrento") || v.seedKey().startsWith("pekaridze"))
                    assertThat(item.portion()).isNull();
            }
        }
        assertThat(keys).containsExactlyInAnyOrderElementsOf(prices.keySet());
        var yangtze = data.venues().getLast();
        assertThat(yangtze.check().provenance()).contains("30.00", "3.00", "10.00", "/garnir/", "/supy/");
        assertThat(yangtze.menu().items().getLast().source()).isEqualTo("https://yangtze.by/nashe-menyu/supy/");
    }
    @Test void unknownJsonFieldEnumAndInvalidLatePriceAreRejected() throws Exception {
        String original;
        try (var input = getClass().getResourceAsStream("/db/seed/curated-catalog-v13.json")) {
            original = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String invalid : List.of(original.replaceFirst("\\\"venues\\\"", "\"unknown\": true, \"venues\""),
                original.replace("\"ASIAN\"", "\"UNKNOWN\""), original.replace("\"priceByn\": 10.00", "\"priceByn\": -1.00"))) {
            assertThatThrownBy(() -> CuratedCatalogDataset.read(new ByteArrayInputStream(invalid.getBytes(StandardCharsets.UTF_8))))
                    .isInstanceOfAny(java.io.IOException.class, IllegalArgumentException.class);
        }
    }
}

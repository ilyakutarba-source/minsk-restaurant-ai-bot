package by.ilya.restaurantbot.catalog;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class CuratedCatalogBrandImportTest {
    @Autowired javax.sql.DataSource source;
    @Autowired JdbcTemplate jdbc;

    CuratedCatalogDataset.Venue venue(String key) throws Exception {
        try (var input = getClass().getResourceAsStream("/db/seed/curated-catalog-v11.json")) {
            var v = CuratedCatalogDataset.read(input).venues().getFirst();
            return new CuratedCatalogDataset.Venue(key, "Synthetic " + key, "Минск, Test, " + key, true,
                    v.reviewStatus(), v.cuisines(), v.catalogSource(), v.catalogVerifiedAt(), v.check(),
                    v.hoursSource(), v.hoursVerifiedAt(), v.openingIntervals(), null);
        }
    }
    int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM restaurants", Integer.class); }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {" ", "Pizza", " pizza", "pizza ", "pizza_tempo", "pizza--tempo"})
    void entityRequiresExplicitCanonicalKey(String brand) {
        assertThatThrownBy(() -> new Restaurant("test", brand, "Test", "Минск, Test", true,
                "https://example.test", LocalDate.of(2026, 10, 8), Set.of(Cuisine.ITALIAN)))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void explicitSharedAndIndependentBrandsArePersisted() throws Exception {
        var dataset = new CuratedCatalogDataset(List.of(venue("brand-test-a"), venue("brand-test-b"), venue("brand-test-c")));
        CuratedCatalogImport.apply(DataSourceUtils.getConnection(source), dataset,
                Map.of("brand-test-a", "reviewed-brand", "brand-test-b", "reviewed-brand", "brand-test-c", "other-brand"));
        assertThat(count()).isEqualTo(39);
        assertThat(jdbc.queryForList("SELECT seed_key FROM restaurants WHERE brand_key='reviewed-brand'", String.class))
                .containsExactlyInAnyOrder("brand-test-a", "brand-test-b");
        assertThat(jdbc.queryForObject("SELECT brand_key FROM restaurants WHERE seed_key='brand-test-c'", String.class))
                .isEqualTo("other-brand");
    }
    @Test void missingInvalidAndExtraAssignmentsRejectEntireBatchBeforeWriting() throws Exception {
        var dataset = new CuratedCatalogDataset(List.of(venue("brand-test-a"), venue("brand-test-b")));
        for (var assignments : List.of(Map.of("brand-test-a", "valid"),
                Map.of("brand-test-a", "valid", "brand-test-b", "Invalid"),
                Map.of("brand-test-a", "valid", "brand-test-b", "valid", "unknown", "valid"))) {
            assertThatThrownBy(() -> CuratedCatalogImport.apply(DataSourceUtils.getConnection(source), dataset, assignments))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(count()).isEqualTo(36);
        }
        assertThatThrownBy(() -> CuratedCatalogImport.apply(DataSourceUtils.getConnection(source), dataset, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(count()).isEqualTo(36);
    }
    @Test void historicalImporterCannotAdmitAnUnbrandedVenueAfterV12() throws Exception {
        var dataset = new CuratedCatalogDataset(List.of(venue("brand-test-legacy")));
        assertThatThrownBy(() -> CuratedCatalogImport.apply(DataSourceUtils.getConnection(source), dataset))
                .isInstanceOf(SQLException.class);
    }
}

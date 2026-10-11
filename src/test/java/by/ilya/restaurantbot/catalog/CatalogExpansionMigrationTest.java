package by.ilya.restaurantbot.catalog;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.*;

/** Own generated schemas, H2 by default or the dedicated loopback PostgreSQL 17 cluster. */
class CatalogExpansionMigrationTest {
    static final Map<String, String> BRANDS = Map.of(
            "la-scala-nemiga-36", "la-scala", "mziuri-pritytskogo-97b", "mziuri",
            "pekaridze-platonova-45", "pekaridze", "sorrento-internatsionalnaya-38", "sorrento",
            "da-claudio-karla-marksa-25", "da-claudio", "yangtze-svobody-8", "yangtze");
    private static final List<String> TABLES = List.of("restaurants", "restaurant_cuisines", "restaurant_tags",
            "opening_intervals", "menu_items", "conversation_state", "selection_items", "spring_ai_chat_memory");

    static CuratedCatalogDataset dataset() throws Exception {
        try (var input = CatalogExpansionMigrationTest.class.getResourceAsStream("/db/seed/curated-catalog-v13.json")) {
            return CuratedCatalogDataset.read(input);
        }
    }
    Connection connection() throws SQLException {
        String url = System.getProperty("catalog.acceptance.url", "jdbc:h2:mem:v13_" + UUID.randomUUID());
        if (!url.startsWith("jdbc:h2:mem:") && !url.equals("jdbc:postgresql://127.0.0.1:55440/post_v2_02c_acceptance")) {
            throw new IllegalArgumentException("Use only the dedicated V13 acceptance database");
        }
        return DriverManager.getConnection(url, url.startsWith("jdbc:h2:") ? "sa" : "postgres", "");
    }
    FluentConfiguration config(Connection c, String schema) throws SQLException {
        String vendor = c.getMetaData().getDatabaseProductName().equals("PostgreSQL") ? "postgresql" : "h2";
        return Flyway.configure().dataSource(new SingleConnectionDataSource(c, true))
                .schemas(schema).defaultSchema(schema).createSchemas(true)
                .locations("classpath:db/migration", "classpath:db/memory/" + vendor);
    }
    void hibernate(Connection c, String schema) {
        var factory = new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(new SingleConnectionDataSource(c, true));
        factory.setPackagesToScan("by.ilya.restaurantbot.catalog", "by.ilya.restaurantbot.conversation");
        factory.setJpaVendorAdapter(new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.default_schema", "\"" + schema + "\""));
        try {
            factory.afterPropertiesSet();
            assertThat(factory.getObject()).isNotNull();
        } finally { factory.destroy(); }
    }
    Map<String, List<Map<String, Object>>> snapshot(JdbcTemplate jdbc, boolean originals) {
        var snapshot = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (var table : TABLES) {
            String filter = !originals ? "" : switch (table) {
                case "restaurants" -> " WHERE id <= 30";
                case "restaurant_cuisines", "restaurant_tags", "opening_intervals", "menu_items" -> " WHERE restaurant_id <= 30";
                default -> "";
            };
            var rows = jdbc.queryForList("SELECT * FROM " + table + filter);
            rows.sort(Comparator.comparing(Object::toString));
            snapshot.put(table, rows);
        }
        return snapshot;
    }
    void assertCatalog(JdbcTemplate jdbc) throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM restaurants", Integer.class)).isEqualTo(36);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT brand_key) FROM restaurants", Integer.class)).isEqualTo(23);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM menu_items", Integer.class)).isEqualTo(101);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM opening_intervals", Integer.class)).isEqualTo(247);
        var expected = Map.of(Cuisine.ITALIAN, List.of(8, 4), Cuisine.GEORGIAN, List.of(4, 4),
                Cuisine.ASIAN, List.of(6, 4), Cuisine.EUROPEAN, List.of(9, 7), Cuisine.BELARUSIAN, List.of(13, 8));
        expected.forEach((cuisine, counts) -> {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM restaurant_cuisines WHERE cuisine=?", Integer.class, cuisine.name()))
                    .isEqualTo(counts.getFirst());
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT r.brand_key) FROM restaurants r JOIN restaurant_cuisines c ON r.id=c.restaurant_id WHERE c.cuisine=?",
                    Integer.class, cuisine.name())).isEqualTo(counts.getLast());
        });
        var identities = jdbc.query("SELECT name,address FROM restaurants", (r, n) -> CuratedCatalogDataset.identity(r.getString(1), r.getString(2)));
        assertThat(identities).doesNotHaveDuplicates();
        for (var v : dataset().venues()) {
            var row = jdbc.queryForMap("SELECT * FROM restaurants WHERE seed_key=?", v.seedKey());
            assertThat(row.get("brand_key")).isEqualTo(BRANDS.get(v.seedKey()));
            assertThat(row.get("name")).isEqualTo(v.name());
            assertThat(row.get("address")).isEqualTo(v.address());
            assertThat(row.get("check_source")).isEqualTo(v.check().provenance());
            assertThat(row.get("check_estimation_type")).isEqualTo("DERIVED");
            assertThat(row.get("menu_coverage")).isEqualTo("PARTIAL");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM opening_intervals WHERE restaurant_id=?", Integer.class, row.get("id"))).isEqualTo(7);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM menu_items WHERE restaurant_id=?", Integer.class, row.get("id"))).isEqualTo(2);
        }
    }
    @Test void freshThroughV13ValidateHibernateAndReapply() throws Exception { migrate(false); }
    @Test void upgradeV12PreservesAllExistingIdsFactsAndPrivateState() throws Exception { migrate(true); }
    void migrate(boolean upgrade) throws Exception {
        String schema = "v13_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = connection()) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(c, true));
            try {
                Map<String, List<Map<String, Object>>> before = null;
                if (upgrade) {
                    assertThat(config(c, schema).target("12").load().migrate().migrationsExecuted).isEqualTo(12);
                    c.setSchema(schema);
                    jdbc.update("INSERT INTO conversation_state VALUES (901,2,'{}',3,TIMESTAMP '2026-10-08 12:00:00')");
                    jdbc.update("INSERT INTO selection_items VALUES (901,1,3,2)");
                    jdbc.update("INSERT INTO spring_ai_chat_memory VALUES ('v13-test','synthetic private state','USER',TIMESTAMP '2026-10-08 12:00:00')");
                    jdbc.update("UPDATE restaurants SET active=FALSE WHERE seed_key='ember-pobediteley-9'");
                    before = snapshot(jdbc, true);
                }
                var flyway = config(c, schema).load();
                assertThat(flyway.migrate().migrationsExecuted).isEqualTo(upgrade ? 1 : 13);
                c.setSchema(schema);
                assertCatalog(jdbc);
                if (upgrade) assertThat(snapshot(jdbc, true)).isEqualTo(before);
                else assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM restaurants WHERE active", Integer.class)).isEqualTo(36);
                hibernate(c, schema);
                assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
                var after = snapshot(jdbc, false);
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                c.setSchema(schema);
                assertThat(snapshot(jdbc, false)).isEqualTo(after);
            } finally { drop(c, jdbc, schema); }
        }
    }

    @Test void invalidLastVenueDuplicateSeedIdentityAndMissingBrandRejectBeforeAnyWrite() throws Exception {
        String schema = "v13_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = connection()) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(c, true));
            try {
                config(c, schema).target("12").load().migrate(); c.setSchema(schema);
                jdbc.update("UPDATE restaurants SET active=FALSE WHERE id=2");
                var before = snapshot(jdbc, false);
                var data = dataset(); var last = data.venues().getLast();
                var invalid = new CuratedCatalogDataset.Venue(last.seedKey(), last.name(), last.address(), true,
                        "NEEDS_VERIFICATION", last.cuisines(), last.catalogSource(), last.catalogVerifiedAt(), last.check(),
                        last.hoursSource(), last.hoursVerifiedAt(), last.openingIntervals(), last.menu());
                var batch = new ArrayList<>(data.venues()); batch.set(batch.size() - 1, invalid);
                assertThatThrownBy(() -> CuratedCatalogImport.apply(c, new CuratedCatalogDataset(batch), BRANDS))
                        .isInstanceOf(IllegalArgumentException.class);
                var duplicate = new ArrayList<>(data.venues()); duplicate.add(data.venues().getFirst());
                assertThatThrownBy(() -> CuratedCatalogImport.apply(c, new CuratedCatalogDataset(duplicate), BRANDS))
                        .isInstanceOf(IllegalArgumentException.class);
                var brands = new HashMap<>(BRANDS); brands.remove(last.seedKey());
                assertThatThrownBy(() -> CuratedCatalogImport.apply(c, data, brands)).isInstanceOf(IllegalArgumentException.class);
                brands.put(last.seedKey(), "Unknown brand!");
                assertThatThrownBy(() -> CuratedCatalogImport.apply(c, data, brands)).isInstanceOf(IllegalArgumentException.class);
                // Duplicate existing seed at the end of an otherwise valid batch.
                var existing = new CuratedCatalogDataset.Venue("pizza-tempo-karla-marksa-26", last.name(), last.address(), true,
                        last.reviewStatus(), last.cuisines(), last.catalogSource(), last.catalogVerifiedAt(), last.check(),
                        last.hoursSource(), last.hoursVerifiedAt(), last.openingIntervals(), null);
                var existingBatch = new ArrayList<>(data.venues()); existingBatch.set(existingBatch.size() - 1, existing);
                var mapped = new HashMap<>(BRANDS); mapped.remove(last.seedKey()); mapped.put(existing.seedKey(), "yangtze");
                assertThatThrownBy(() -> CuratedCatalogImport.apply(c, new CuratedCatalogDataset(existingBatch), mapped))
                        .isInstanceOf(IllegalArgumentException.class);
                var identity = new CuratedCatalogDataset.Venue("duplicate-identity", "PIZZA TEMPO", "Минск, ул. Карла Маркса, 26", true,
                        last.reviewStatus(), last.cuisines(), last.catalogSource(), last.catalogVerifiedAt(), last.check(),
                        last.hoursSource(), last.hoursVerifiedAt(), last.openingIntervals(), null);
                existingBatch.set(existingBatch.size() - 1, identity);
                mapped.remove(existing.seedKey()); mapped.put(identity.seedKey(), "yangtze");
                assertThatThrownBy(() -> CuratedCatalogImport.apply(c, new CuratedCatalogDataset(existingBatch), mapped))
                        .isInstanceOf(IllegalArgumentException.class);
                assertThat(snapshot(jdbc, false)).isEqualTo(before);
                // Direct replay is a controlled error, while Flyway replay is zero migrations.
                config(c, schema).load().migrate(); c.setSchema(schema);
                var after = snapshot(jdbc, false);
                assertThatThrownBy(() -> CuratedCatalogImport.apply(c, data, BRANDS)).isInstanceOf(IllegalArgumentException.class);
                assertThat(snapshot(jdbc, false)).isEqualTo(after);
            } finally { drop(c, jdbc, schema); }
        }
    }
    @Test void databaseFailureInLastMenuRollsBackTheWholeFlywayBatch() throws Exception {
        String schema = "v13_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = connection()) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(c, true));
            try {
                config(c, schema).target("12").load().migrate(); c.setSchema(schema);
                // Force a real JDBC failure after the previous five venues were inserted.
                jdbc.execute("ALTER TABLE menu_items ADD CONSTRAINT v13_fault CHECK (seed_key <> 'yangtze-chicken-shiitake-soup')");
                var before = snapshot(jdbc, false);
                assertThatThrownBy(() -> config(c, schema).load().migrate()).hasRootCauseInstanceOf(SQLException.class);
                c.setSchema(schema);
                assertThat(snapshot(jdbc, false)).isEqualTo(before);
                if (c.getMetaData().getDatabaseProductName().equals("PostgreSQL")) {
                    assertThat(config(c, schema).load().info().current().getVersion().getVersion()).isEqualTo("12");
                    assertThat(config(c, schema).target("12").load().validateWithResult().validationSuccessful).isTrue();
                    jdbc.execute("ALTER TABLE menu_items DROP CONSTRAINT v13_fault");
                    assertThat(config(c, schema).load().migrate().migrationsExecuted).isEqualTo(1);
                    c.setSchema(schema); assertCatalog(jdbc);
                }
            } finally { drop(c, jdbc, schema); }
        }
    }
    void drop(Connection c, JdbcTemplate jdbc, String schema) throws SQLException {
        c.setSchema(c.getMetaData().getDatabaseProductName().equals("PostgreSQL") ? "public" : "PUBLIC");
        jdbc.execute("DROP SCHEMA \"" + schema + "\" CASCADE");
    }
}

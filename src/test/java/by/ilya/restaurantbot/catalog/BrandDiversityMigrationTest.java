package by.ilya.restaurantbot.catalog;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.*;

/** Own disposable schemas only. Runs on H2 or an explicitly supplied isolated local PostgreSQL DB. */
class BrandDiversityMigrationTest {
    private static final List<String> TABLES = List.of("restaurants", "restaurant_cuisines", "restaurant_tags",
            "opening_intervals", "menu_items", "conversation_state", "selection_items", "spring_ai_chat_memory");

    Connection connection() throws SQLException {
        String url = System.getProperty("spring.datasource.url", "jdbc:h2:mem:brand_" + UUID.randomUUID());
        if (url.startsWith("jdbc:postgresql:") && !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:55436/post_v2_01(_[a-z]+)?")) {
            throw new IllegalArgumentException("Migration acceptance requires the isolated POST-V2-01 local database");
        }
        return DriverManager.getConnection(url, System.getProperty("spring.datasource.username", "sa"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
    }
    FluentConfiguration config(Connection c, String schema) throws SQLException {
        String vendor = c.getMetaData().getDatabaseProductName().equals("PostgreSQL") ? "postgresql" : "h2";
        return Flyway.configure().dataSource(new SingleConnectionDataSource(c, true))
                .schemas(schema).defaultSchema(schema).createSchemas(true)
                .locations("classpath:db/migration", "classpath:db/memory/" + vendor);
    }
    void validateHibernate(Connection c, String schema) {
        var factory = new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(new SingleConnectionDataSource(c, true));
        factory.setPackagesToScan("by.ilya.restaurantbot.catalog");
        factory.setJpaVendorAdapter(new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter());
        // Flyway creates the generated schema quoted; preserve its case on H2 as well as PostgreSQL.
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.default_schema", "\"" + schema + "\""));
        try {
            factory.afterPropertiesSet();
            assertThat(factory.getObject()).isNotNull();
        } finally {
            factory.destroy();
        }
    }
    Map<String, List<Map<String, Object>>> snapshot(JdbcTemplate jdbc) {
        var snapshot = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (var table : TABLES) {
            var rows = jdbc.queryForList("SELECT * FROM " + table);
            rows.forEach(row -> row.keySet().removeIf(key -> key.equalsIgnoreCase("brand_key")));
            rows.sort(Comparator.comparing(Object::toString));
            snapshot.put(table, rows);
        }
        return snapshot;
    }
    void assertCatalog(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM restaurants WHERE brand_key IS NOT NULL", Integer.class)).isEqualTo(30);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT brand_key) FROM restaurants", Integer.class)).isEqualTo(17);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM menu_items", Integer.class)).isEqualTo(89);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM opening_intervals", Integer.class)).isEqualTo(205);
        Map<String, List<String>> expected = Map.ofEntries(
                Map.entry("vasilki", List.of("vasilki-nezavisimosti-16", "vasilki-nezavisimosti-58", "vasilki-yakuba-kolasa-37", "vasilki-nezavisimosti-89")),
                Map.entry("pizza-tempo", List.of("pizza-tempo-karla-marksa-26", "pizza-tempo-nezavisimosti-18", "pizza-tempo-nezavisimosti-78", "pizza-tempo-pobediteley-84", "pizza-tempo-bobruyskaya-6")),
                Map.entry("khinkalnya", List.of("khinkalnya-dzerzhinskogo-104")),
                Map.entry("kuhmistr", List.of("kuhmistr-karla-marksa-40")),
                Map.entry("chumaczki-shlyah", List.of("chumaczki-myasnikova-34")),
                Map.entry("berezka", List.of("berezka-nezavisimosti-40")),
                Map.entry("druzya", List.of("druzya-kulman-40")),
                Map.entry("gan-bei", List.of("ganbei-pritytskogo-156", "ganbei-mstislavtsa-11", "ganbei-timiryazeva-74a")),
                Map.entry("coffee-embassy", List.of("embassy-karla-marksa-6", "embassy-mira-1", "embassy-pobediteley-115")),
                Map.entry("litviny", List.of("litviny-revolyutsionnaya-26", "litviny-pritytskogo-156-1", "litviny-pobediteley-119")),
                Map.entry("beans-leaves", List.of("beans-leaves-pobediteley-9")),
                Map.entry("bardot-xx1", List.of("bardot-pobediteley-9")),
                Map.entry("tashkent", List.of("tashkent-lenina-3")),
                Map.entry("ember", List.of("ember-pobediteley-9")),
                Map.entry("mlyn-ruzh", List.of("mlyn-ruzh-kalvariyskaya-1")),
                Map.entry("chaika", List.of("chaika-dzerzhinskogo-104")),
                Map.entry("mimino", List.of("mimino-kirova-13")));
        expected.forEach((brand, keys) -> assertThat(jdbc.queryForList(
                "SELECT seed_key FROM restaurants WHERE brand_key=?", String.class, brand))
                .containsExactlyInAnyOrderElementsOf(keys));
    }
    @Test void freshThroughV12AndReapply() throws Exception { migrate(false); }
    @Test void upgradeV11PreservesEveryExistingValueIncludingPrivateState() throws Exception { migrate(true); }

    @Test void unmappedVenueStopsMigrationAndPostgresRollsBackAllV12Changes() throws Exception {
        String schema = "brand_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = connection()) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(c, true));
            try {
                config(c, schema).target("11").load().migrate();
                c.setSchema(schema);
                jdbc.update("""
                        INSERT INTO restaurants (seed_key,name,address,catalog_source,catalog_verified_at)
                        VALUES ('unmapped','Unmapped','Минск, Test','https://example.test',DATE '2026-10-08')
                        """);
                var before = snapshot(jdbc);
                assertThatThrownBy(() -> config(c, schema).load().migrate())
                        .hasRootCauseInstanceOf(SQLException.class);
                c.setSchema(schema);
                if (c.getMetaData().getDatabaseProductName().equals("PostgreSQL")) {
                    assertThat(snapshot(jdbc)).isEqualTo(before);
                    assertThat(jdbc.queryForObject("""
                            SELECT COUNT(*) FROM information_schema.columns
                            WHERE table_schema=? AND table_name='restaurants' AND column_name='brand_key'
                            """, Integer.class, schema)).isZero();
                    assertThat(config(c, schema).target("11").load().info().current().getVersion().getVersion()).isEqualTo("11");
                }
            } finally {
                c.setSchema(c.getMetaData().getDatabaseProductName().equals("PostgreSQL") ? "public" : "PUBLIC");
                jdbc.execute("DROP SCHEMA \"" + schema + "\" CASCADE");
            }
        }
    }

    void migrate(boolean upgrade) throws Exception {
        String schema = "brand_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = connection()) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(c, true));
            try {
                Map<String, List<Map<String, Object>>> before = null;
                if (upgrade) {
                    assertThat(config(c, schema).target("11").load().migrate().migrationsExecuted).isEqualTo(11);
                    c.setSchema(schema);
                    jdbc.update("INSERT INTO conversation_state VALUES (901,2,'{}',3,TIMESTAMP '2026-10-08 12:00:00')");
                    jdbc.update("INSERT INTO selection_items VALUES (901,1,3,2)");
                    jdbc.update("INSERT INTO spring_ai_chat_memory VALUES ('brand-test','synthetic private state','USER',TIMESTAMP '2026-10-08 12:00:00')");
                    // Also preserve a legitimate existing inactive flag.
                    jdbc.update("UPDATE restaurants SET active=FALSE WHERE seed_key='ember-pobediteley-9'");
                    before = snapshot(jdbc);
                }
                var flyway = config(c, schema).load();
                assertThat(flyway.migrate().migrationsExecuted).isEqualTo(upgrade ? 1 : 12);
                c.setSchema(schema);
                assertCatalog(jdbc);
                if (upgrade) assertThat(snapshot(jdbc)).isEqualTo(before);
                validateHibernate(c, schema);
                assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                for (String invalid : List.of("", " ", "Pizza-Tempo", " pizza", "pizza ", "pizza_tempo", "pizza\ttempo", "pizza\n", "pizza--tempo", "x".repeat(101))) {
                    assertThatThrownBy(() -> jdbc.update("UPDATE restaurants SET brand_key=? WHERE id=2", invalid))
                            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                }
                assertThatThrownBy(() -> jdbc.update("UPDATE restaurants SET brand_key=NULL WHERE id=2"))
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO restaurants (seed_key,name,address,catalog_source,catalog_verified_at)
                        VALUES ('unbranded','Unbranded','Минск, Test','https://example.test',DATE '2026-10-08')
                        """)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                assertCatalog(jdbc);
            } finally {
                // Never clean the caller's schema/database, only this generated acceptance schema.
                c.setSchema(c.getMetaData().getDatabaseProductName().equals("PostgreSQL") ? "public" : "PUBLIC");
                jdbc.execute("DROP SCHEMA \"" + schema + "\" CASCADE");
            }
        }
    }
}

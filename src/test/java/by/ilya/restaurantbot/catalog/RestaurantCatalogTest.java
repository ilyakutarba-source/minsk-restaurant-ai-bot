package by.ilya.restaurantbot.catalog;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RestaurantCatalogTest {
    @Autowired RestaurantRepository repository;
    @Autowired RestaurantService service;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired MockMvc mvc;

    @Test
    void migrationsValidateAndReapplyWithoutDuplicatingSeed() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().applied()).hasSize(4);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(repository.count()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM opening_intervals", Integer.class)).isEqualTo(21);
    }

    @Test
    void seedPreservesBranchIdentityAndVerifiedChecksForAllThreeRestaurants() {
        var catalog = service.getCatalog(0, 20);
        assertThat(catalog.totalElements()).isEqualTo(3);
        assertThat(catalog.content()).extracting(RestaurantDetails::name)
                .containsExactly("Васильки", "Pizza Tempo", "Хинкальня");
        assertThat(catalog.content()).extracting(RestaurantDetails::address).containsExactly(
                "Минск, проспект Независимости, 16", "Минск, ул. Карла Маркса, 26",
                "Минск, проспект Дзержинского, 104");
        assertThat(catalog.content()).extracting(RestaurantDetails::cuisines).containsExactly(
                Set.of(Cuisine.BELARUSIAN), Set.of(Cuisine.ITALIAN), Set.of(Cuisine.GEORGIAN));
        for (var restaurant : catalog.content()) {
            assertThat(restaurant.catalogSource()).startsWith("https://");
            assertThat(restaurant.catalogVerifiedAt()).isEqualTo(LocalDate.of(2026, 10, 2));
            assertThat(restaurant.hoursSource()).startsWith("https://");
            assertThat(restaurant.hoursVerifiedAt()).isEqualTo(LocalDate.of(2026, 10, 2));
            assertThat(restaurant.openingIntervals()).hasSize(7);
            assertThat(restaurant.estimatedCheckPerGuest()).isPositive();
            assertThat(restaurant.checkEstimationType()).isEqualTo(CheckEstimationType.DERIVED);
            assertThat(restaurant.checkSource()).startsWith("https://").contains("один гость", "не официальный");
            assertThat(restaurant.checkVerifiedAt()).isEqualTo(LocalDate.of(2026, 10, 2));
        }
        assertThat(catalog.content().get(0).estimatedCheckPerGuest()).isEqualByComparingTo("39.80");
        assertThat(catalog.content().get(0).checkSource())
                .contains("Драники_1.jpg", "Супы_1.jpg", "22.90", "16.90", "Независимости, 16", "единое меню сети");
        assertThat(catalog.content().get(1).estimatedCheckPerGuest()).isEqualByComparingTo("32.70");
        assertThat(catalog.content().get(1).checkSource())
                .contains("пасты%201.jpg", "супы%201.jpg", "19.50", "13.20", "Маркса, 26", "единое меню сети");
        var titan = catalog.content().get(2);
        assertThat(titan.estimatedCheckPerGuest()).isEqualByComparingTo("44.00");
        assertThat(titan.checkEstimationType()).isEqualTo(CheckEstimationType.DERIVED);
        assertThat(titan.checkSource()).contains("/restoran/menyu/", "25.00", "19.00", "не официальный");
        assertThat(titan.checkVerifiedAt()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(titan.tags()).containsExactlyInAnyOrder(RestaurantTag.COZY, RestaurantTag.FRIENDS);
        var friday = catalog.content().get(0).openingIntervals().get(4);
        assertThat(friday.weekday()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(friday.closesAt()).isEqualTo(LocalTime.of(1, 0));
        assertThat(friday.closesNextDay()).isTrue();
        assertThat(titan.openingIntervals()).allSatisfy(i -> {
            assertThat(i.closesAt()).isEqualTo(LocalTime.MIDNIGHT);
            assertThat(i.closesNextDay()).isTrue();
        });
    }

    @Test
    void persistenceRoundTripKeepsMoneyEnumsAndOvernightHoursAndAllowsAnotherBranchName() {
        var restaurant = new Restaurant("synthetic-other-branch", "Васильки", "Synthetic test address",
                true, "https://example.test/catalog", LocalDate.of(2026, 10, 2),
                Set.of(Cuisine.BELARUSIAN, Cuisine.ITALIAN));
        restaurant.setEstimatedCheck(new BigDecimal("19.99"), CheckEstimationType.PUBLISHED,
                "https://example.test/check", LocalDate.of(2026, 10, 2));
        restaurant.setOpeningHours(List.of(new OpeningInterval(DayOfWeek.FRIDAY,
                LocalTime.of(20, 0), LocalTime.of(2, 0), true)),
                "https://example.test/hours", LocalDate.of(2026, 10, 2));
        var id = repository.saveAndFlush(restaurant).getId();
        entityManager.clear();
        var actual = service.getRestaurant(id).orElseThrow();
        assertThat(actual.id()).isPositive();
        assertThat(actual.estimatedCheckPerGuest()).isEqualByComparingTo("19.99");
        assertThat(actual.checkEstimationType()).isEqualTo(CheckEstimationType.PUBLISHED);
        assertThat(actual.cuisines()).containsExactlyInAnyOrder(Cuisine.BELARUSIAN, Cuisine.ITALIAN);
        assertThat(actual.openingIntervals()).containsExactly(new RestaurantDetails.Hours(
                DayOfWeek.FRIDAY, LocalTime.of(20, 0), LocalTime.of(2, 0), true));
        assertThat(repository.count()).isEqualTo(4);
    }

    @Test
    void activeCatalogIsPagedAndDetailsAreMappedBeforeTransactionEnds() {
        var first = service.getCatalog(0, 1);
        var second = service.getCatalog(1, 1);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.content()).hasSize(1);
        assertThat(second.content().getFirst().id()).isGreaterThan(first.content().getFirst().id());
        jdbc.update("UPDATE restaurants SET active = FALSE WHERE id = ?", first.content().getFirst().id());
        entityManager.clear();
        assertThat(service.getCatalog(0, 20).content()).hasSize(2);
        assertThat(service.getRestaurant(first.content().getFirst().id()).orElseThrow().active()).isFalse();
        assertThat(service.getRestaurant(Long.MAX_VALUE)).isEmpty();
        assertThat(service.getCatalog(100, 20).content()).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void restReturnsOwnDtoWithMetadata404AndSwaggerOperations() throws Exception {
        var id = service.getCatalog(0, 20).content().get(2).id();
        mvc.perform(get("/api/v1/restaurants").param("page", "0").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/v1/restaurants/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Хинкальня"))
                .andExpect(jsonPath("$.estimatedCheckPerGuest").value(44.00))
                .andExpect(jsonPath("$.checkEstimationType").value("DERIVED"))
                .andExpect(jsonPath("$.checkSource").exists())
                .andExpect(jsonPath("$.checkVerifiedAt").value("2026-10-02"))
                .andExpect(jsonPath("$.openingIntervals.length()").value(7))
                .andExpect(jsonPath("$.seedKey").doesNotExist())
                .andExpect(jsonPath("$.googleData").doesNotExist());
        mvc.perform(get("/api/v1/restaurants/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/restaurants'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/restaurants/{id}'].get").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "101"})
    void invalidPageSizeIsRejectedAtRestAndService(String size) throws Exception {
        mvc.perform(get("/api/v1/restaurants").param("size", size)).andExpect(status().isBadRequest());
        assertThatThrownBy(() -> service.getCatalog(0, Integer.parseInt(size)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidPageIsRejectedAtRestAndService() throws Exception {
        mvc.perform(get("/api/v1/restaurants").param("page", "-1")).andExpect(status().isBadRequest());
        assertThatThrownBy(() -> service.getCatalog(-1, 20)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "UPDATE restaurants SET seed_key = (SELECT seed_key FROM restaurants WHERE id = 1) WHERE id = 2",
        "UPDATE restaurants SET name = ' ' WHERE id = 1",
        "UPDATE restaurants SET catalog_source = NULL WHERE id = 1",
        "UPDATE restaurants SET estimated_check_per_guest = 0 WHERE id = 3",
        "UPDATE restaurants SET estimated_check_per_guest = -1 WHERE id = 3",
        "UPDATE restaurants SET check_source = NULL WHERE id = 3",
        "UPDATE restaurants SET check_verified_at = NULL WHERE id = 3",
        "UPDATE restaurants SET check_estimation_type = NULL WHERE id = 3",
        "UPDATE restaurants SET check_estimation_type = 'UNKNOWN' WHERE id = 3",
        "UPDATE restaurants SET hours_verified_at = NULL WHERE id = 1",
        "UPDATE restaurant_cuisines SET cuisine = 'UNKNOWN' WHERE restaurant_id = 1",
        "INSERT INTO restaurant_cuisines VALUES (1, 'BELARUSIAN')",
        "INSERT INTO restaurant_tags VALUES (1, 'UNKNOWN')",
        "INSERT INTO restaurant_tags VALUES (999999, 'COZY')",
        "UPDATE opening_intervals SET weekday = 'UNKNOWN' WHERE restaurant_id = 1 AND weekday = 'MONDAY'",
        "UPDATE opening_intervals SET closes_at = opens_at WHERE restaurant_id = 1 AND weekday = 'MONDAY'",
        "UPDATE opening_intervals SET closes_next_day = FALSE WHERE restaurant_id = 1 AND weekday = 'FRIDAY'"
    })
    void databaseRejectsInvalidCatalogData(String sql) {
        assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidDerivedCheckAndAmbiguousHoursAreRejectedBeforePersistence() {
        var restaurant = new Restaurant("synthetic", "Synthetic", "Synthetic", true,
                "https://example.test", LocalDate.of(2026, 10, 2), Set.of(Cuisine.ITALIAN));
        assertThatThrownBy(() -> restaurant.setEstimatedCheck(new BigDecimal("10.001"),
                CheckEstimationType.DERIVED, "https://example.test", LocalDate.of(2026, 10, 2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restaurant.setEstimatedCheck(BigDecimal.ONE,
                CheckEstimationType.DERIVED, " ", LocalDate.of(2026, 10, 2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OpeningInterval(DayOfWeek.MONDAY,
                LocalTime.NOON, LocalTime.NOON, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OpeningInterval(DayOfWeek.MONDAY,
                LocalTime.NOON, LocalTime.of(13, 0), true)).isInstanceOf(IllegalArgumentException.class);
    }
}

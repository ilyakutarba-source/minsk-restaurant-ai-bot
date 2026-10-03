package by.ilya.restaurantbot.search;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import by.ilya.restaurantbot.catalog.CheckEstimationType;
import by.ilya.restaurantbot.catalog.Cuisine;
import by.ilya.restaurantbot.catalog.OpeningInterval;
import by.ilya.restaurantbot.catalog.Restaurant;
import by.ilya.restaurantbot.catalog.RestaurantRepository;
import by.ilya.restaurantbot.catalog.RestaurantTag;
import by.ilya.restaurantbot.search.SearchResult.ReasonCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RestaurantSearchTest.FixedClock.class)
@Transactional
class RestaurantSearchTest {
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock testClock() {
            // UTC Thursday, already Friday in Minsk; tests must not depend on the host date/zone.
            return Clock.fixed(Instant.parse("2026-10-01T21:30:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired RestaurantSearchService service;
    @Autowired RestaurantRepository restaurants;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private SearchRequest request(int guests, String budget, String date, String time,
                                  Cuisine cuisine, Set<RestaurantTag> tags) {
        return new SearchRequest(guests, new BigDecimal(budget), date, time, cuisine, tags);
    }

    private SearchRequest standard(Set<RestaurantTag> tags) {
        return request(2, "150", "2026-10-02", "21:00", null, tags);
    }

    private List<String> names(SearchResult result) {
        return result.candidates().stream().map(c -> c.restaurant().name()).toList();
    }

    private Restaurant synthetic(String key, String check, List<OpeningInterval> hours) {
        var r = new Restaurant(key, key, "Synthetic test address", true,
                "https://example.test/catalog", LocalDate.of(2026, 10, 2), Set.of(Cuisine.ITALIAN));
        r.setEstimatedCheck(new BigDecimal(check), CheckEstimationType.PUBLISHED,
                "https://example.test/check", LocalDate.of(2026, 10, 2));
        r.setOpeningHours(hours, "https://example.test/hours", LocalDate.of(2026, 10, 2));
        return restaurants.saveAndFlush(r);
    }

    private List<OpeningInterval> fridayHours() {
        return List.of(new OpeningInterval(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(23, 0), false));
    }

    @Test
    void noPreferencesUsesEstimatedTotalAndReturnsVerifiedDto() {
        var result = service.search(standard(null));
        assertThat(names(result)).containsExactly("Pizza Tempo", "Васильки", "Хинкальня");
        assertThat(result.currency()).isEqualTo("BYN");
        assertThat(result.normalizedCriteria().preferredTags()).isEmpty();
        assertThat(result.normalizedCriteria().totalBudgetByn()).isEqualTo(new BigDecimal("150.00"));
        assertThat(result.candidates()).allSatisfy(c -> {
            assertThat(c.matchCount()).isZero();
            assertThat(c.allowedReasonCodes()).containsExactly(ReasonCode.BUDGET_MATCH, ReasonCode.HOURS_MATCH);
            assertThat(c.restaurant().checkSource()).isNotBlank();
            assertThat(c.restaurant().hoursVerifiedAt()).isNotNull();
            assertThat(c.estimatedTotalByn()).isEqualByComparingTo(
                    c.restaurant().estimatedCheckPerGuest().multiply(BigDecimal.valueOf(2)));
        });
        assertThat(result.candidates().getFirst().estimatedTotalByn()).isEqualByComparingTo("65.40");
    }

    @Test
    void cuisineIsStrictWhileUnmatchedTagsDoNotExclude() {
        var result = service.search(request(2, "150", "TODAY", "21:00", Cuisine.ITALIAN,
                Set.of(RestaurantTag.COZY, RestaurantTag.FRIENDS)));
        assertThat(names(result)).containsExactly("Pizza Tempo");
        assertThat(result.candidates().getFirst().matchCount()).isZero();
        assertThat(result.candidates().getFirst().allowedReasonCodes()).containsExactly(
                ReasonCode.CUISINE_MATCH, ReasonCode.BUDGET_MATCH, ReasonCode.HOURS_MATCH);
    }

    @ParameterizedTest
    @CsvSource({"1,50,true", "2,70,false", "2,80,true", "2,79.99,false", "6,240,true"})
    void budgetUsesGuestsAndIncludesExactDecimalBoundary(int guests, String budget, boolean eligible) {
        jdbc.update("UPDATE restaurants SET active = FALSE");
        synthetic("synthetic-budget", "40.00", fridayHours());
        entityManager.clear();
        var result = service.search(request(guests, budget, "TODAY", "21:00", null, null));
        assertThat(result.candidates()).hasSize(eligible ? 1 : 0);
        if (eligible) {
            assertThat(result.candidates().getFirst().estimatedTotalByn())
                    .isEqualByComparingTo(new BigDecimal("40.00").multiply(BigDecimal.valueOf(guests)));
        }
    }

    @Test
    void realBudgetBoundariesDoNotRoundOrTreatTotalAsPerGuest() {
        assertThat(names(service.search(request(2, "65.40", "TODAY", "21:00", null, null))))
                .containsExactly("Pizza Tempo");
        assertThat(service.search(request(2, "65.39", "TODAY", "21:00", null, null)).candidates()).isEmpty();
        assertThat(names(service.search(request(1, "39.80", "TODAY", "21:00", null, null))))
                .containsExactly("Pizza Tempo", "Васильки");
    }

    @Test
    void tagsOnlyRankEligibleRestaurantsAndReasonsOnlyContainRequestedMatches() {
        var result = service.search(standard(Set.of(RestaurantTag.FRIENDS, RestaurantTag.COZY, RestaurantTag.QUIET)));
        assertThat(names(result)).containsExactly("Хинкальня", "Pizza Tempo", "Васильки");
        assertThat(result.candidates().getFirst().matchCount()).isEqualTo(2);
        assertThat(result.candidates().getFirst().allowedReasonCodes()).containsExactly(
                ReasonCode.BUDGET_MATCH, ReasonCode.HOURS_MATCH, ReasonCode.COZY_TAG_MATCH, ReasonCode.FRIENDS_TAG_MATCH);
        assertThat(names(service.search(request(2, "70", "TODAY", "21:00", null,
                Set.of(RestaurantTag.COZY, RestaurantTag.FRIENDS)))))
                .containsExactly("Pizza Tempo");
        assertThat(names(service.search(request(2, "150", "TODAY", "10:00", null,
                Set.of(RestaurantTag.COZY, RestaurantTag.FRIENDS)))))
                .containsExactly("Pizza Tempo", "Васильки");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE restaurants SET active = FALSE WHERE name = 'Хинкальня'",
            "UPDATE restaurants SET estimated_check_per_guest = NULL, check_estimation_type = NULL, check_source = NULL, check_verified_at = NULL WHERE name = 'Хинкальня'",
            "UPDATE restaurants SET hours_source = NULL, hours_verified_at = NULL WHERE name = 'Хинкальня'",
            "DELETE FROM opening_intervals WHERE restaurant_id = (SELECT id FROM restaurants WHERE name = 'Хинкальня')"
    })
    void inactiveOrUnknownCheckOrHoursCannotBeRescuedByTags(String sql) {
        jdbc.update(sql);
        entityManager.clear();
        assertThat(names(service.search(standard(Set.of(RestaurantTag.COZY, RestaurantTag.FRIENDS)))))
                .containsExactly("Pizza Tempo", "Васильки");
    }

    @Test
    void combinesCuisineBudgetAndArrivalWithoutRelaxation() {
        assertThat(service.search(request(2, "65.39", "TODAY", "21:00", Cuisine.ITALIAN, null)).candidates()).isEmpty();
        assertThat(service.search(request(2, "150", "TODAY", "23:00", Cuisine.ITALIAN, null)).candidates()).isEmpty();
        assertThat(names(service.search(request(2, "65.40", "TODAY", "21:00", Cuisine.ITALIAN, null))))
                .containsExactly("Pizza Tempo");
    }

    @ParameterizedTest
    @CsvSource({"09:59,false", "10:00,true", "21:00,true", "22:59,true", "23:00,false", "23:01,false"})
    void daytimeHoursIncludeOpeningButExcludeClosing(String time, boolean open) {
        assertThat(service.search(request(1, "100", "TODAY", time, Cuisine.ITALIAN, null)).candidates())
                .hasSize(open ? 1 : 0);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-02,17:59,false", "2026-10-02,18:00,true", "2026-10-02,21:00,true",
            "2026-10-03,00:00,true", "2026-10-03,01:00,true", "2026-10-03,02:00,false",
            "2026-10-03,03:00,false", "2026-10-04,01:00,false"
    })
    void overnightUsesPreviousOpeningDayAndExactClosingBoundary(String date, String time, boolean open) {
        jdbc.update("UPDATE restaurants SET active = FALSE");
        synthetic("synthetic-friday-night", "40.00", List.of(new OpeningInterval(
                DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(2, 0), true)));
        entityManager.clear();
        assertThat(service.search(request(1, "50", date, time, null, null)).candidates()).hasSize(open ? 1 : 0);
    }

    @Test
    void overnightCrossesWeekBoundaryAndHandlesMultipleIntervals() {
        jdbc.update("UPDATE restaurants SET active = FALSE");
        synthetic("synthetic-sunday-night", "40.00", List.of(
                new OpeningInterval(DayOfWeek.SUNDAY, LocalTime.of(18, 0), LocalTime.of(2, 0), true),
                new OpeningInterval(DayOfWeek.MONDAY, LocalTime.of(12, 0), LocalTime.of(14, 0), false),
                new OpeningInterval(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(23, 0), false)));
        entityManager.clear();
        assertThat(service.search(request(1, "50", "2026-10-05", "01:00", null, null)).candidates()).hasSize(1);
        assertThat(service.search(request(1, "50", "2026-10-05", "02:00", null, null)).candidates()).isEmpty();
        assertThat(service.search(request(1, "50", "2026-10-05", "13:00", null, null)).candidates()).hasSize(1);
        assertThat(service.search(request(1, "50", "2026-10-05", "16:00", null, null)).candidates()).isEmpty();
        assertThat(service.search(request(1, "50", "2026-10-05", "20:00", null, null)).candidates()).hasSize(1);
    }

    @Test
    void seededNightHoursMatchArrivalAndMidnightIsExclusiveForKhinkalnya() {
        assertThat(names(service.search(request(2, "150", "2026-10-03", "00:00", null, null))))
                .containsExactly("Васильки");
        assertThat(service.search(request(2, "150", "2026-10-03", "01:00", null, null)).candidates()).isEmpty();
        assertThat(service.search(request(2, "150", "2026-10-05", "00:30", null, null)).candidates()).isEmpty();
    }

    @Test
    void stableIdTieBreakAndLimitAreAppliedAfterSorting() throws Exception {
        jdbc.update("UPDATE restaurants SET active = FALSE");
        var a = synthetic("synthetic-a", "40.00", fridayHours());
        var b = synthetic("synthetic-b", "40.00", fridayHours());
        var c = synthetic("synthetic-c", "40.00", fridayHours());
        var cheapest = synthetic("synthetic-cheapest", "30.00", fridayHours());
        entityManager.clear();
        var expected = List.of(cheapest.getId(), a.getId(), b.getId());
        var input = standard(Set.of(RestaurantTag.QUIET));
        var result = service.search(input);
        assertThat(result.candidates().stream().map(r -> r.restaurant().id()).toList()).isEqualTo(expected);
        assertThat(result.candidates()).noneMatch(r -> r.restaurant().id().equals(c.getId()));
        assertThat(mapper.writeValueAsString(service.search(input))).isEqualTo(mapper.writeValueAsString(result));
    }

    @Test
    void tagOrderIsNormalizedAndDuplicateTagsDoNotIncreaseScore() throws Exception {
        var one = new HashSet<>(List.of(RestaurantTag.FRIENDS, RestaurantTag.COZY));
        var two = new HashSet<>(List.of(RestaurantTag.COZY, RestaurantTag.FRIENDS));
        assertThat(mapper.writeValueAsString(service.search(standard(one))))
                .isEqualTo(mapper.writeValueAsString(service.search(standard(two))));
        mvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON).content("""
                {"guests":2,"totalBudgetByn":150,"date":"TODAY","time":"21:00",
                 "preferredTags":["COZY","COZY"]}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.candidates[0].matchCount").value(1));
    }

    @Test
    void menuRemovalAndMissingMetadataDoNotChangeSearch() {
        var before = service.search(standard(Set.of(RestaurantTag.COZY)));
        jdbc.update("DELETE FROM menu_items");
        jdbc.update("UPDATE restaurants SET menu_source = NULL, menu_verified_at = NULL, menu_coverage = NULL");
        entityManager.clear();
        assertThat(service.search(standard(Set.of(RestaurantTag.COZY)))).isEqualTo(before);
    }

    @Test
    void relativeDateTokensAndWindowUseMinskClockWithoutNaturalLanguageParsing() {
        assertThat(service.search(standard(null)).normalizedCriteria().date()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(service.search(request(2, "150", " today ", " 21:00 ", null, null)).normalizedCriteria().date())
                .isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(service.search(request(2, "150", "TOMORROW", "21:00", null, null)).normalizedCriteria().date())
                .isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(service.search(request(2, "150", "2026-10-08", "21:00", null, null)).normalizedCriteria().date())
                .isEqualTo(LocalDate.of(2026, 10, 8));
        for (var date : List.of("2026-10-01", "2026-10-09", "сегодня", "завтра", "FRIDAY", "2026-02-30", "")) {
            assertThatThrownBy(() -> service.search(request(2, "150", date, "21:00", null, null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void invalidServiceCriteriaFailBeforeSearch() {
        var invalid = Arrays.asList(
                null,
                new SearchRequest(null, new BigDecimal("150"), "TODAY", "21:00", null, null),
                request(0, "150", "TODAY", "21:00", null, null),
                request(7, "150", "TODAY", "21:00", null, null),
                new SearchRequest(2, null, "TODAY", "21:00", null, null),
                request(2, "0", "TODAY", "21:00", null, null),
                request(2, "-1", "TODAY", "21:00", null, null),
                request(2, "150.001", "TODAY", "21:00", null, null),
                request(2, "150", null, "21:00", null, null),
                request(2, "150", "TODAY", null, null, null),
                request(2, "150", "TODAY", "24:00", null, null),
                request(2, "150", "TODAY", "9:00", null, null),
                request(2, "150", "TODAY", "21:00:00", null, null),
                request(2, "150", "TODAY", "вечером", null, null),
                request(2, "150", "TODAY", "21:00", null, new HashSet<>(Arrays.asList(RestaurantTag.COZY, null))));
        for (var input : invalid) {
            assertThatThrownBy(() -> service.search(input)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void restReturnsDetachedDtoNormalizedCriteriaSourcesAndSwagger() throws Exception {
        mvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request(2, "150", "TODAY", "21:00", Cuisine.ITALIAN, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.normalizedCriteria.date").value("2026-10-02"))
                .andExpect(jsonPath("$.normalizedCriteria.guests").value(2))
                .andExpect(jsonPath("$.currency").value("BYN"))
                .andExpect(jsonPath("$.candidates.length()").value(1))
                .andExpect(jsonPath("$.candidates[0].restaurant.name").value("Pizza Tempo"))
                .andExpect(jsonPath("$.candidates[0].estimatedTotalByn").value(65.40))
                .andExpect(jsonPath("$.candidates[0].restaurant.checkEstimationType").value("DERIVED"))
                .andExpect(jsonPath("$.candidates[0].restaurant.checkSource").exists())
                .andExpect(jsonPath("$.candidates[0].restaurant.hoursVerifiedAt").value("2026-10-02"))
                .andExpect(jsonPath("$.candidates[0].restaurant.seedKey").doesNotExist())
                .andExpect(jsonPath("$.warnings").isNotEmpty());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/recommendations'].post").exists())
                .andExpect(jsonPath("$.components.schemas.SearchRequest.required.length()").value(4));
    }

    @Test
    void emptyRestResultIs200AndNeverRelaxesBudget() throws Exception {
        mvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request(2, "1", "TODAY", "21:00", null, null))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.candidates").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "null", "{broken",
            "{\"guests\":2.5,\"totalBudgetByn\":150,\"date\":\"TODAY\",\"time\":\"21:00\"}",
            "{\"guests\":7,\"totalBudgetByn\":150,\"date\":\"TODAY\",\"time\":\"21:00\"}",
            "{\"guests\":2,\"totalBudgetByn\":-1,\"date\":\"TODAY\",\"time\":\"21:00\"}",
            "{\"guests\":2,\"totalBudgetByn\":150.001,\"date\":\"TODAY\",\"time\":\"21:00\"}",
            "{\"guests\":2,\"totalBudgetByn\":150,\"date\":\"2026-10-09\",\"time\":\"21:00\"}",
            "{\"guests\":2,\"totalBudgetByn\":150,\"date\":\"TODAY\",\"time\":\"24:00\"}",
            "{\"guests\":2,\"totalBudgetByn\":150,\"date\":\"TODAY\",\"time\":\"21:00\",\"cuisine\":\"UNKNOWN\"}",
            "{\"guests\":2,\"totalBudgetByn\":150,\"date\":\"TODAY\",\"time\":\"21:00\",\"preferredTags\":[\"UNKNOWN\"]}",
            "{\"guests\":2,\"totalBudgetByn\":150,\"date\":\"TODAY\",\"time\":\"21:00\",\"preferredTags\":[null]}"
    })
    void invalidRestCriteriaAre400(String json) throws Exception {
        mvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }
}

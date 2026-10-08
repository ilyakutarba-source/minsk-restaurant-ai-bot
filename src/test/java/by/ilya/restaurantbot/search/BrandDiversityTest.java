package by.ilya.restaurantbot.search;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import by.ilya.restaurantbot.catalog.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrandDiversityTest {
    private final RestaurantRepository repository = mock(RestaurantRepository.class);
    private final RestaurantSearchService search = new RestaurantSearchService(repository,
            Clock.fixed(Instant.parse("2026-10-09T09:00:00Z"), ZoneId.of("Europe/Minsk")));

    Restaurant venue(long id, String brand, String name, String check, Set<RestaurantTag> tags) {
        var r = new Restaurant("fixture-" + id, brand, name, "Минск, Test, " + id, true,
                "https://example.test/catalog", LocalDate.of(2026, 10, 8), Set.of(Cuisine.ITALIAN));
        ReflectionTestUtils.setField(r, "id", id);
        ReflectionTestUtils.setField(r, "tags", tags);
        r.setEstimatedCheck(new BigDecimal(check), CheckEstimationType.PUBLISHED,
                "https://example.test/check", LocalDate.of(2026, 10, 8));
        r.setOpeningHours(List.of(new OpeningInterval(DayOfWeek.FRIDAY, LocalTime.of(18, 0),
                LocalTime.of(23, 0), false)), "https://example.test/hours", LocalDate.of(2026, 10, 8));
        return r;
    }

    Restaurant venue(long id, String brand) { return venue(id, brand, brand, "40", Set.of()); }
    SearchResult result(Restaurant... venues) {
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(venues));
        return search.search(new SearchRequest(2, new BigDecimal("150"), "TODAY", "21:00",
                Cuisine.ITALIAN, Set.of(RestaurantTag.COZY, RestaurantTag.QUIET)));
    }
    List<Long> ids(SearchResult result) { return result.candidates().stream().map(c -> c.restaurant().id()).toList(); }

    @Test void threeBranchesOfOneBrandReturnOne() {
        assertThat(ids(result(venue(3, "one"), venue(2, "one"), venue(1, "one")))).containsExactly(1L);
    }
    @Test void threeDifferentBrandsReturnThreeAndFourthIsLimited() {
        assertThat(ids(result(venue(4, "four"), venue(3, "three"), venue(2, "two"), venue(1, "one"))))
                .containsExactly(1L, 2L, 3L);
    }
    @Test void twoBrandsReturnTwoWithoutRepeatingBranches() {
        assertThat(ids(result(venue(1, "one"), venue(2, "one"), venue(3, "two")))).containsExactly(1L, 3L);
    }
    @Test void deduplicationPrecedesLimitSoLaterBrandsAreRetained() {
        assertThat(ids(result(venue(1, "one"), venue(2, "one"), venue(3, "one"),
                venue(4, "two"), venue(5, "three")))).containsExactly(1L, 4L, 5L);
    }
    @Test void bestBranchUsesMatchCountThenTotalThenIdAndGlobalOrderIsPreserved() {
        var cheap = venue(1, "one", "One", "10", Set.of());
        var matching = venue(9, "one", "One branch", "70", Set.of(RestaurantTag.COZY));
        var other = venue(2, "two", "Two", "20", Set.of());
        assertThat(ids(result(cheap, other, matching))).containsExactly(9L, 2L);
        var cheaperMatch = venue(8, "one", "One cheaper", "60", Set.of(RestaurantTag.COZY));
        assertThat(ids(result(matching, cheaperMatch, other))).containsExactly(8L, 2L);
        var tiedMatch = venue(7, "one", "One tie", "60.00", Set.of(RestaurantTag.COZY));
        assertThat(ids(result(cheaperMatch, matching, tiedMatch, other))).containsExactly(7L, 2L);
    }
    @ParameterizedTest
    @ValueSource(strings = {"inactive", "cuisine", "budget", "closed", "unknown-check", "unknown-hours"})
    void ineligibleBranchNeverSuppressesAnEligibleBranchOrFillsTheResult(String reason) {
        var invalid = venue(1, "one", "Invalid", "10", Set.of(RestaurantTag.COZY, RestaurantTag.QUIET));
        switch (reason) {
            case "inactive" -> ReflectionTestUtils.setField(invalid, "active", false);
            case "cuisine" -> ReflectionTestUtils.setField(invalid, "cuisines", Set.of(Cuisine.ASIAN));
            case "budget" -> invalid.setEstimatedCheck(new BigDecimal("75.01"), CheckEstimationType.PUBLISHED,
                    "https://example.test/check", LocalDate.of(2026, 10, 8));
            case "closed" -> invalid.setOpeningHours(List.of(new OpeningInterval(DayOfWeek.FRIDAY,
                    LocalTime.of(18, 0), LocalTime.of(21, 0), false)),
                    "https://example.test/hours", LocalDate.of(2026, 10, 8));
            case "unknown-check" -> ReflectionTestUtils.setField(invalid, "checkSource", null);
            case "unknown-hours" -> ReflectionTestUtils.setField(invalid, "hoursVerifiedAt", null);
            default -> throw new AssertionError(reason);
        }
        assertThat(ids(result(invalid, venue(2, "one")))).containsExactly(2L);
        assertThat(result(invalid).candidates()).isEmpty();
    }
    @Test void explicitKeysControlMembershipRegardlessOfNamesAndOrderIsDeterministic() {
        var a = venue(1, "one", "Pizza", "40", Set.of());
        var b = venue(2, "two", "Pizza Downtown", "40", Set.of());
        var c = venue(3, "one", "Completely different spelling", "40", Set.of());
        var d = venue(4, "three", "Pizza", "40", Set.of());
        var expected = result(a, b, c, d);
        assertThat(ids(expected)).containsExactly(1L, 2L, 4L);
        assertThat(result(d, c, b, a)).isEqualTo(expected);
        assertThat(result(b, d, a, c)).isEqualTo(expected);
    }
}

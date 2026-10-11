package by.ilya.restaurantbot.search;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import by.ilya.restaurantbot.catalog.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

/** Same 135 scenarios as POST-V2-02, actual Java service and its own eligibility predicates. */
@SpringBootTest @ActiveProfiles("test") @Transactional
@org.springframework.context.annotation.Import(CatalogExpansionAuditTest.FixedClock.class)
class CatalogExpansionAuditTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class FixedClock {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        Clock auditClock() { return Clock.fixed(Instant.parse("2026-10-08T09:00:00Z"), ZoneId.of("Europe/Minsk")); }
    }
    @Autowired RestaurantSearchService service;
    @Autowired RestaurantRepository repository;
    @Autowired javax.sql.DataSource source;
    @Autowired ObjectMapper json;
    Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        var method = RestaurantSearchService.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
    boolean eligible(Restaurant r, SearchCriteria c) throws Exception {
        return r.isActive() && r.getCuisines().contains(c.cuisine())
                && (boolean) invoke("hasVerifiedCheck", new Class[]{Restaurant.class}, r)
                && ((BigDecimal) invoke("estimatedTotal", new Class[]{Restaurant.class, SearchCriteria.class}, r, c))
                        .compareTo(c.totalBudgetByn()) <= 0
                && (boolean) invoke("isOpen", new Class[]{Restaurant.class, SearchCriteria.class}, r, c);
    }
    @Test void auditAll135ScenariosAndOriginalItalianRequest() throws Exception {
        var all = repository.findAllByOrderByIdAsc();
        assertThat(all).hasSize(36);
        assertThat(all.stream().map(Restaurant::getBrandKey).distinct()).hasSize(23);
        var scenarios = new ArrayList<Map<String, Object>>();
        var counts = new TreeMap<Integer, Integer>();
        for (var cuisine : Cuisine.values()) for (var date : List.of("2026-10-09", "2026-10-10", "2026-10-11"))
        for (var budget : List.of("80", "150", "250")) for (var time : List.of("13:00", "19:00", "21:00")) {
            var request = new SearchRequest(2, new BigDecimal(budget), date, time, cuisine, Set.of());
            var result = service.search(request);
            assertThat(service.search(request)).isEqualTo(result);
            var filtered = new ArrayList<Restaurant>();
            for (var r : all) if (eligible(r, result.normalizedCriteria())) filtered.add(r);
            filtered.sort(Comparator.comparing(Restaurant::getEstimatedCheckPerGuest).thenComparing(Restaurant::getId));
            var unique = new LinkedHashMap<String, Restaurant>();
            filtered.forEach(r -> unique.putIfAbsent(r.getBrandKey(), r));
            var expected = unique.values().stream().limit(3).toList();
            var ids = result.candidates().stream().map(c -> c.restaurant().id()).toList();
            assertThat(ids).containsExactlyElementsOf(expected.stream().map(Restaurant::getId).toList());
            assertThat(ids).hasSize(Math.min(3, unique.size()));
            counts.merge(ids.size(), 1, Integer::sum);
            var row = new LinkedHashMap<String, Object>();
            row.put("cuisine", cuisine); row.put("date", date); row.put("time", time); row.put("guests", 2);
            row.put("budgetByn", budget); row.put("eligibleVenues", filtered.size()); row.put("eligibleBrands", unique.size());
            row.put("returned", ids.size()); row.put("returnedBrands", expected.stream().map(Restaurant::getBrandKey).toList());
            row.put("returnedIds", ids); row.put("eligibleBrandKeys", new ArrayList<>(unique.keySet()));
            row.put("estimatedTotalsByn", result.candidates().stream().map(SearchResult.Candidate::estimatedTotalByn).toList());
            scenarios.add(row);
            if (cuisine == Cuisine.ITALIAN && time.equals("21:00") && budget.equals("150")) {
                assertThat(expected.stream().map(Restaurant::getBrandKey)).containsExactly("pizza-tempo", "da-claudio", "sorrento");
                assertThat(result.candidates().stream().map(c -> c.estimatedTotalByn().toPlainString()))
                        .containsExactly("65.40", "120.00", "130.00");
            }
            if (cuisine == Cuisine.ITALIAN && budget.equals("150"))
                assertThat(filtered.stream().map(Restaurant::getBrandKey)).doesNotContain("la-scala");
            if (cuisine == Cuisine.ITALIAN && budget.equals("250"))
                assertThat(filtered.stream().map(Restaurant::getBrandKey)).contains("la-scala");
        }
        assertThat(scenarios).hasSize(135);
        String output = System.getProperty("catalog.audit.output");
        if (output != null) {
            try (var c = source.getConnection()) {
                assertThat(c.getMetaData().getURL()).isEqualTo("jdbc:postgresql://127.0.0.1:55440/post_v2_02c_acceptance");
                var evidence = Map.of("clock", "2026-10-08T09:00:00Z", "postgresql", c.getMetaData().getDatabaseProductVersion(),
                        "counts", counts, "scenarios", scenarios);
                Files.writeString(Path.of(output), json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
            }
        }
        System.out.println("V13 AUDIT: 135 scenarios, 270 real Java search calls; result distribution " + counts);
    }
}

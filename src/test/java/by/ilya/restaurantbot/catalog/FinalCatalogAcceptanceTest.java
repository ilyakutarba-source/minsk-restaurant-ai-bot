package by.ilya.restaurantbot.catalog;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import by.ilya.restaurantbot.search.*;
import by.ilya.restaurantbot.conversation.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

/** Same acceptance on H2 and a fresh, externally selected PostgreSQL database. */
@SpringBootTest
@ActiveProfiles("test")
@Import(FinalCatalogAcceptanceTest.FixedClock.class)
@Transactional
class FinalCatalogAcceptanceTest {
    @TestConfiguration public static class FixedClock {
        @Bean @Primary Clock finalCatalogClock() {
            return Clock.fixed(Instant.parse("2026-10-06T09:00:00Z"), ZoneId.of("Europe/Minsk"));
        }
    }
    @Autowired RestaurantService catalog;
    @Autowired MenuService menus;
    @Autowired MenuImportService importer;
    @Autowired RestaurantSearchService search;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired SelectionService selection;
    @Autowired ReferenceResolver resolver;
    @Autowired ConversationStore store;

    // Preserve V1 catalog expectations as a regression; V2 expansion has its own full-catalog acceptance.
    @BeforeEach void isolateOriginalTen() {
        jdbc.update("UPDATE restaurants SET active=false WHERE catalog_verified_at=DATE '2026-10-07'");
        em.clear();
    }

    SearchRequest request(int guests, String budget, String date, String time, Cuisine cuisine, Set<RestaurantTag> tags) {
        return new SearchRequest(guests, new BigDecimal(budget), date, time, cuisine, tags);
    }
    List<Long> ids(SearchResult r) { return r.candidates().stream().map(c -> c.restaurant().id()).toList(); }
    long id(String key) { return jdbc.queryForObject("SELECT id FROM restaurants WHERE seed_key=?", Long.class, key); }

    @Test void finalDataHasTenSpecificBranchesAndSixtyVerifiedPartialItems() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().applied()).hasSize(11);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        var all = catalog.getCatalog(0, 20);
        assertThat(all.totalElements()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM menu_items WHERE restaurant_id IN (SELECT id FROM restaurants WHERE active=true)", Integer.class)).isEqualTo(60);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT seed_key) FROM restaurants WHERE active=true", Integer.class)).isEqualTo(10);
        assertThat(all.content()).extracting(RestaurantDetails::address).doesNotHaveDuplicates();
        for (var r : all.content()) {
            assertThat(r.catalogSource()).startsWith("https://");
            assertThat(r.address()).startsWith("Минск,");
            assertThat(r.openingIntervals()).hasSize(7);
            assertThat(r.hoursSource()).startsWith("https://");
            assertThat(r.catalogVerifiedAt()).isNotNull();
            assertThat(r.hoursVerifiedAt()).isNotNull();
            assertThat(r.checkVerifiedAt()).isNotNull();
            assertThat(r.checkEstimationType()).isEqualTo(CheckEstimationType.DERIVED);
            assertThat(r.checkSource()).contains("один гость", "не официальный средний чек");
            var menu = menus.getMenuByRestaurantId(r.id(), null, null).orElseThrow();
            assertThat(menu.items()).hasSize(6);
            assertThat(menu.coverage()).isEqualTo(MenuCoverage.PARTIAL);
            assertThat(menu.verifiedAt()).isNotNull();
            assertThat(menu.source()).startsWith("https://");
            assertThat(menu.items()).allSatisfy(i -> assertThat(i.priceByn()).isPositive());
            // The curated food-only estimate is reproducible from the two verified positions.
            String main = r.cuisines().contains(Cuisine.ITALIAN) ? "Паста Карбонара"
                    : r.cuisines().contains(Cuisine.BELARUSIAN) ? "Драники с мачанкой по-белорусски" : "Шашлык из свинины";
            String soup = r.cuisines().contains(Cuisine.ITALIAN) ? "Суп-крем из шампиньонов с сыром с голубой плесенью"
                    : r.cuisines().contains(Cuisine.BELARUSIAN) ? "Щи с лесными грибами" : "Харчо";
            var sum = menu.items().stream().filter(i -> i.name().equals(main) || i.name().equals(soup))
                    .map(MenuDetails.Item::priceByn).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum).isEqualByComparingTo(r.estimatedCheckPerGuest());
        }
    }

    @Test void expandedMenuImportIsIdempotentAndKeepsOtherBranchesAndItemIdentity() throws Exception {
        var before = jdbc.queryForList("SELECT id,restaurant_id,seed_key,price_byn FROM menu_items ORDER BY id");
        for (int i=0; i<2; i++) try (var data = getClass().getResourceAsStream("/db/seed/partial-menu-v9.json")) {
            importer.importJson(data);
        }
        assertThat(jdbc.queryForList("SELECT id,restaurant_id,seed_key,price_byn FROM menu_items ORDER BY id")).isEqualTo(before);
    }

    @Test void fullCatalogBudgetCuisineStableRankingAndHardFiltersBeatSoftTags() {
        var q = request(2,"65.40","TODAY","21:00",Cuisine.ITALIAN,Set.of(RestaurantTag.QUIET));
        var result = search.search(q);
        assertThat(ids(result)).containsExactly(id("pizza-tempo-karla-marksa-26"),id("pizza-tempo-bobruyskaya-6"),id("pizza-tempo-nezavisimosti-18"));
        assertThat(ids(search.search(q))).isEqualTo(ids(result));
        assertThat(result.candidates()).allSatisfy(c -> {
            assertThat(c.estimatedTotalByn()).isEqualByComparingTo("65.40");
            assertThat(c.matchCount()).isZero();
            assertThat(c.restaurant().cuisines()).contains(Cuisine.ITALIAN);
        });
        assertThat(search.search(request(2,"65.39","TODAY","21:00",null,null)).candidates()).isEmpty();
        assertThat(search.search(request(4,"65.40","TODAY","21:00",null,null)).candidates()).isEmpty();
        var tags = Set.of(RestaurantTag.COZY,RestaurantTag.FRIENDS);
        long bobruyskaya=id("pizza-tempo-bobruyskaya-6"), nez18=id("pizza-tempo-nezavisimosti-18"), nez78=id("pizza-tempo-nezavisimosti-78");
        assertThat(ids(search.search(request(2,"150","TODAY","21:00",null,tags)))).containsExactly(3L,2L,bobruyskaya);
        assertThat(ids(search.search(request(2,"70","TODAY","21:00",null,tags)))).containsExactly(2L,bobruyskaya,nez18);
        jdbc.update("UPDATE restaurants SET active=false WHERE id=2"); em.clear();
        assertThat(ids(search.search(q))).containsExactly(bobruyskaya,nez18,nez78);
    }

    @Test void finalBranchHoursCoverOpenCloseOvernightAndWeekBoundary() {
        // Other branches remain active: test membership by ID, not only the limited top three.
        long arena = id("pizza-tempo-pobediteley-84");
        assertThat(openAt(arena,"2026-10-06","10:00")).isTrue();
        assertThat(openAt(arena,"2026-10-06","09:59")).isFalse();
        assertThat(openAt(arena,"2026-10-06","21:59")).isTrue();
        assertThat(openAt(arena,"2026-10-06","22:00")).isFalse();
        long kolasa = id("vasilki-yakuba-kolasa-37");
        assertThat(openAt(kolasa,"2026-10-10","01:59")).isTrue();
        assertThat(openAt(kolasa,"2026-10-10","02:00")).isFalse();
        long galileo = id("pizza-tempo-bobruyskaya-6");
        assertThat(openAt(galileo,"2026-10-11","23:59")).isTrue();
        assertThat(openAt(galileo,"2026-10-12","00:00")).isFalse();
    }
    boolean openAt(long restaurant, String date, String time) {
        // Make this branch the cheapest in this transaction so the product limit cannot hide it.
        jdbc.update("UPDATE restaurants SET estimated_check_per_guest=0.01 WHERE id=?",restaurant); em.clear();
        return ids(search.search(request(1,"150",date,time,null,null))).contains(restaurant);
    }

    @Test void fullCatalogSearchRemainsIndependentOfAllSavedMenus() {
        var q=request(2,"150","TODAY","21:00",null,Set.of(RestaurantTag.COZY));
        var before=search.search(q);
        jdbc.update("DELETE FROM menu_items");
        jdbc.update("UPDATE restaurants SET menu_source=NULL,menu_verified_at=NULL,menu_coverage=NULL"); em.clear();
        assertThat(search.search(q)).isEqualTo(before);
    }

    @Test void realSameNameBranchesClarifyWhileOrdinalsRemainChatScoped() {
        long a=9100000101L,b=9100000102L;
        store.reset(store.load(a));store.reset(store.load(b));
        var italian=List.of(2L,id("pizza-tempo-bobruyskaya-6"),id("pizza-tempo-nezavisimosti-18"));
        selection.replace(a,italian);selection.replace(b,List.of(1L,3L));
        for(int p=1;p<=3;p++) assertThat(resolver.resolve(a,new RestaurantReference(p,null,null)).restaurantId()).isEqualTo(italian.get(p-1));
        assertThat(resolver.resolve(a,new RestaurantReference(null,true,null)).restaurantId()).isEqualTo(italian.getLast());
        assertThat(resolver.resolve(a,new RestaurantReference(null,null,"Pizza Tempo")).status()).isEqualTo(ReferenceResolver.Status.NEED_CLARIFICATION);
        assertThat(resolver.resolve(a,new RestaurantReference(null,null,"Unknown")).status()).isEqualTo(ReferenceResolver.Status.NOT_FOUND);
        assertThat(resolver.resolve(b,new RestaurantReference(null,null,"Хинкальня")).restaurantId()).isEqualTo(3L);
        assertThat(resolver.resolve(b,new RestaurantReference(3,null,null)).status()).isEqualTo(ReferenceResolver.Status.NEED_CLARIFICATION);
    }
}

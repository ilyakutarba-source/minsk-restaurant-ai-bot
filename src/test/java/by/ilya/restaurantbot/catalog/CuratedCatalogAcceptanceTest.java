package by.ilya.restaurantbot.catalog;

import java.math.BigDecimal;
import java.util.*;
import by.ilya.restaurantbot.search.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Runs unchanged on H2 and the isolated fresh PostgreSQL acceptance database. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
@org.springframework.context.annotation.Import(CuratedCatalogAcceptanceTest.FixedClock.class)
class CuratedCatalogAcceptanceTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class FixedClock {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        java.time.Clock curatedClock() {
            return java.time.Clock.fixed(java.time.Instant.parse("2026-10-07T09:00:00Z"),java.time.ZoneId.of("Europe/Minsk"));
        }
    }
    @Autowired RestaurantService catalog;
    @Autowired MenuService menus;
    @Autowired RestaurantSearchService search;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired javax.sql.DataSource source;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    CuratedCatalogDataset dataset() throws Exception {
        try(var input=getClass().getResourceAsStream("/db/seed/curated-catalog-v11.json")) {
            return CuratedCatalogDataset.read(input);
        }
    }
    long id(String key) { return jdbc.queryForObject("SELECT id FROM restaurants WHERE seed_key=?",Long.class,key); }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class); }
    SearchResult find(int guests,String budget,String date,String time,Cuisine cuisine,Set<RestaurantTag> tags) {
        return search.search(new SearchRequest(guests,new BigDecimal(budget),date,time,cuisine,tags));
    }
    List<Long> ids(SearchResult r) { return r.candidates().stream().map(c->c.restaurant().id()).toList(); }
    @Test void freshCatalogHasThirtyVenuesSeventeenBrandsAndOptionalVerifiedMenus() throws Exception {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().applied()).hasSize(11);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        var all=catalog.getCatalog(0,100);
        assertThat(all.totalElements()).isEqualTo(30);
        assertThat(all.content().stream().map(RestaurantDetails::name).distinct()).hasSize(17);
        assertThat(count("menu_items")).isEqualTo(89);
        assertThat(count("opening_intervals")).isEqualTo(205);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM restaurants WHERE check_estimation_type='PUBLISHED'",Integer.class)).isEqualTo(4);
        assertThat(all.content()).allSatisfy(r -> {
            assertThat(r.active()).isTrue(); assertThat(r.address()).startsWith("Минск,");
            assertThat(r.cuisines()).isNotEmpty(); assertThat(r.estimatedCheckPerGuest()).isPositive();
            assertThat(r.checkEstimationType()).isNotNull(); assertThat(r.checkSource()).startsWith("https://");
            assertThat(r.checkVerifiedAt()).isNotNull(); assertThat(r.hoursVerifiedAt()).isNotNull();
            assertThat(r.catalogVerifiedAt()).isNotNull(); assertThat(r.catalogSource()).startsWith("https://");
            assertThat(r.hoursSource()).startsWith("https://"); assertThat(r.openingIntervals()).isNotEmpty();
        });
        var identities=all.content().stream().map(r->CuratedCatalogDataset.identity(r.name(),r.address())).toList();
        assertThat(identities).doesNotHaveDuplicates();
        int withMenu=0,withoutMenu=0;
        for(var v:dataset().venues()) {
            var r=catalog.getRestaurant(id(v.seedKey())).orElseThrow();
            assertThat(r.name()).isEqualTo(v.name()); assertThat(r.address()).isEqualTo(v.address());
            assertThat(r.cuisines()).isEqualTo(v.cuisines());
            assertThat(r.estimatedCheckPerGuest()).isEqualByComparingTo(v.check().amount());
            assertThat(r.checkSource()).isEqualTo(v.check().provenance());
            assertThat(r.openingIntervals()).containsExactlyInAnyOrderElementsOf(v.openingIntervals().stream()
                    .map(h->new RestaurantDetails.Hours(h.weekday(),h.opensAt(),h.closesAt(),h.closesNextDay())).toList());
            var menu=menus.getMenuByRestaurantId(r.id(),null,null).orElseThrow();
            if(v.menu()==null) { withoutMenu++; assertThat(menu.status()).isEqualTo(MenuDetails.Status.DATA_UNAVAILABLE); }
            else { withMenu++; assertThat(menu.status()).isEqualTo(MenuDetails.Status.AVAILABLE);
                assertThat(menu.coverage()).isEqualTo(MenuCoverage.PARTIAL);
                assertThat(menu.items()).hasSize(v.menu().items().size());
            }
        }
        assertThat(withMenu).isEqualTo(16); assertThat(withoutMenu).isEqualTo(4);
    }
    @Test void replayAndLastRowDuplicateAreRejectedBeforeAnyInsertIncludingInactiveIdentity() throws Exception {
        var data=dataset(); var connection=DataSourceUtils.getConnection(source);
        assertThatThrownBy(()->CuratedCatalogImport.apply(connection,data)).isInstanceOf(IllegalArgumentException.class);
        var v=data.venues().getFirst();
        var fresh=new CuratedCatalogDataset.Venue("test-curated", "Synthetic", "Минск, Test, 1",true,v.reviewStatus(),
                v.cuisines(),v.catalogSource(),v.catalogVerifiedAt(),v.check(),v.hoursSource(),v.hoursVerifiedAt(),v.openingIntervals(),null);
        jdbc.update("UPDATE restaurants SET active=false WHERE id=?",id(v.seedKey())); em.clear();
        var duplicate=new CuratedCatalogDataset.Venue("different-key",v.name().toUpperCase(Locale.ROOT),v.address(),true,v.reviewStatus(),
                v.cuisines(),v.catalogSource(),v.catalogVerifiedAt(),v.check(),v.hoursSource(),v.hoursVerifiedAt(),v.openingIntervals(),null);
        assertThatThrownBy(()->CuratedCatalogImport.apply(connection,new CuratedCatalogDataset(List.of(fresh,duplicate))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(count("restaurants")).isEqualTo(30); assertThat(count("menu_items")).isEqualTo(89);
    }
    @Test void controlledImportSupportsEmptyPartialMenuAndValidatesEntireBatchFirst() throws Exception {
        var v=dataset().venues().getFirst(); var connection=DataSourceUtils.getConnection(source);
        var empty=new MenuDataset.RestaurantMenu("test-empty-menu",MenuCoverage.PARTIAL,v.catalogSource(),v.catalogVerifiedAt(),List.of());
        var fresh=new CuratedCatalogDataset.Venue("test-empty-menu","Synthetic","Минск, Test, 1",true,v.reviewStatus(),v.cuisines(),
                v.catalogSource(),v.catalogVerifiedAt(),v.check(),v.hoursSource(),v.hoursVerifiedAt(),v.openingIntervals(),empty);
        var invalid=new CuratedCatalogDataset.Venue("test-invalid","Synthetic 2","Минск, Test, 2",true,"UNAPPROVED",v.cuisines(),
                v.catalogSource(),v.catalogVerifiedAt(),v.check(),v.hoursSource(),v.hoursVerifiedAt(),v.openingIntervals(),null);
        assertThatThrownBy(()->CuratedCatalogImport.apply(connection,new CuratedCatalogDataset(List.of(fresh,invalid))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(count("restaurants")).isEqualTo(30);
        CuratedCatalogImport.apply(connection,new CuratedCatalogDataset(List.of(fresh))); em.clear();
        assertThat(menus.getMenuByRestaurantId(id(fresh.seedKey()),null,null).orElseThrow().status())
                .isEqualTo(MenuDetails.Status.DATA_UNAVAILABLE);
    }
    @Test void publishedCafeWithoutMenuRemainsSearchableAndRestReturnsHonestUnavailable() throws Exception {
        long mira=id("embassy-mira-1");
        assertThat(ids(find(2,"60.00","2026-10-09","12:00",Cuisine.EUROPEAN,null))).containsExactly(mira);
        assertThat(find(2,"59.99","2026-10-09","12:00",Cuisine.EUROPEAN,null).candidates()).isEmpty();
        assertThat(find(3,"60.00","2026-10-09","12:00",Cuisine.EUROPEAN,null).candidates()).isEmpty();
        mvc.perform(get("/api/v1/restaurants/{id}/menu",mira)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DATA_UNAVAILABLE")).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/restaurants/{id}",mira)).andExpect(status().isOk())
                .andExpect(jsonPath("$.cuisines[0]").value("EUROPEAN"));
        var asian=find(2,"93.20","2026-10-09","12:00",Cuisine.ASIAN,null);
        assertThat(ids(asian)).containsExactly(id("ganbei-pritytskogo-156"),id("ganbei-mstislavtsa-11"),id("ganbei-timiryazeva-74a"));
        assertThat(asian.candidates()).allSatisfy(c->assertThat(c.estimatedTotalByn()).isEqualByComparingTo("93.20"));
        assertThat(find(2,"93.19","2026-10-09","12:00",Cuisine.ASIAN,null).candidates()).isEmpty();
        jdbc.update("UPDATE restaurants SET active=false WHERE id=?",mira); em.clear();
        assertThat(find(2,"60","2026-10-09","12:00",Cuisine.EUROPEAN,null).candidates()).isEmpty();
    }
    @Test void fullCatalogRankingTagsBudgetLimitAndMenuIndependence() {
        var noTags=find(1,"150","2026-10-09","21:00",null,null);
        assertThat(ids(noTags)).containsExactly(id("embassy-mira-1"),2L,id("pizza-tempo-bobruyskaya-6"));
        var tags=Set.of(RestaurantTag.COZY,RestaurantTag.FRIENDS);
        var ranked=find(2,"150","2026-10-09","21:00",null,tags);
        assertThat(ids(ranked)).containsExactly(3L,id("embassy-mira-1"),2L);
        assertThat(find(2,"60","2026-10-09","21:00",null,tags).candidates()).hasSize(1);
        jdbc.update("DELETE FROM menu_items");
        jdbc.update("UPDATE restaurants SET menu_source=NULL,menu_verified_at=NULL,menu_coverage=NULL"); em.clear();
        assertThat(find(2,"150","2026-10-09","21:00",null,tags)).isEqualTo(ranked);
        assertThat(find(1,"150","2026-10-09","21:00",null,null)).isEqualTo(noTags);
    }
    @Test void ownBranchHoursAndClosedDaysIncludeOvernightWeekBoundary() {
        long bar=id("bardot-pobediteley-9"),mlyn=id("mlyn-ruzh-kalvariyskaya-1");
        assertThat(open(bar,"2026-10-09","17:59")).isFalse();
        assertThat(open(bar,"2026-10-09","18:00")).isTrue();
        assertThat(open(bar,"2026-10-10","01:59")).isTrue();
        assertThat(open(bar,"2026-10-10","02:00")).isFalse();
        assertThat(open(bar,"2026-10-11","18:00")).isFalse();
        assertThat(open(mlyn,"2026-10-12","04:59")).isTrue();
        assertThat(open(mlyn,"2026-10-12","05:00")).isFalse();
        assertThat(open(mlyn,"2026-10-13","21:00")).isFalse();
        long dana=id("ganbei-mstislavtsa-11"),green=id("ganbei-pritytskogo-156");
        assertThat(open(dana,"2026-10-09","10:00")).isTrue();
        assertThat(open(green,"2026-10-09","10:00")).isFalse();
    }
    boolean open(long id,String date,String time) {
        jdbc.update("UPDATE restaurants SET estimated_check_per_guest=0.01 WHERE id=?",id); em.clear();
        return ids(find(1,"150",date,time,null,null)).contains(id);
    }
}

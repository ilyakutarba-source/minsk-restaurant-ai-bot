package by.ilya.restaurantbot.catalog;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PartialMenuTest {
    @Autowired MenuItemRepository items;
    @Autowired RestaurantRepository restaurants;
    @Autowired RestaurantService catalog;
    @Autowired MenuService service;
    @Autowired MenuImportService importer;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private long id(String name) {
        return catalog.getCatalog(0, 20).content().stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow().id();
    }

    private MenuDetails menu(long id) {
        return service.getMenuByRestaurantId(id, null, null).orElseThrow();
    }

    private String seedJson() throws Exception {
        try (var input = getClass().getResourceAsStream("/db/seed/partial-menu-v4.json")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void importText(String json) throws Exception {
        importer.importJson(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void flywayCreatesAndSeedsMenuAndDoesNotRepeatInsert() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().applied()).hasSize(6);
        long before = items.count();
        assertThat(before).isPositive();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(items.count()).isEqualTo(before);
    }

    @Test
    void allThreeMenusExposeVerifiedPartialMetadataAndRepresentativePositivePrices() {
        for (var restaurant : catalog.getCatalog(0, 20).content()) {
            var menu = menu(restaurant.id());
            assertThat(menu.status()).isEqualTo(MenuDetails.Status.AVAILABLE);
            assertThat(menu.coverage()).isEqualTo(MenuCoverage.PARTIAL);
            assertThat(menu.source()).startsWith("https://");
            assertThat(menu.verifiedAt()).isEqualTo(LocalDate.of(2026, 10, 2));
            assertThat(menu.currency()).isEqualTo("BYN");
            assertThat(menu.items().size()).isBetween(5, 10);
            assertThat(menu.items()).allSatisfy(item -> {
                assertThat(item.name()).isNotBlank();
                assertThat(item.priceByn()).isPositive();
                assertThat(item.category()).isNotNull();
                assertThat(item.source()).startsWith("https://");
                assertThat(item.portion()).isNotBlank();
                assertThat(item.description()).isNull();
            });
            assertThat(menu.items().stream().map(MenuDetails.Item::category).distinct().count()).isGreaterThanOrEqualTo(2);
        }
        var carbonara = menu(id("Pizza Tempo")).items().stream()
                .filter(i -> i.name().equals("Паста Карбонара")).findFirst().orElseThrow();
        assertThat(carbonara.priceByn()).isEqualByComparingTo("19.50");
        assertThat(carbonara.portion()).isEqualTo("295 г");
        assertThat(carbonara.dishType()).isEqualTo(DishType.PASTA);
        assertThat(carbonara.source()).isEqualTo("https://tempo.by/img/menu/пасты%201.jpg");
    }

    @Test
    void jpaRoundTripKeepsRestaurantRelationDecimalAndAllowsDuplicateDishNames() {
        var restaurant = restaurants.findById(id("Pizza Tempo")).orElseThrow();
        var item = new MenuDataset.Item("synthetic-variant", "Паста Карбонара", MenuCategory.PASTA, DishType.PASTA,
                new BigDecimal("19.99"), null, null, null, true);
        var itemId = items.saveAndFlush(new MenuItem(restaurant, item)).getId();
        entityManager.clear();
        var actual = items.findById(itemId).orElseThrow();
        assertThat(actual.getRestaurant().getId()).isEqualTo(restaurant.getId());
        assertThat(actual.getPriceByn()).isEqualByComparingTo("19.99");
        assertThat(menu(restaurant.getId()).items()).anySatisfy(i -> {
            assertThat(i.id()).isEqualTo(itemId);
            assertThat(i.source()).isEqualTo("https://tempo.by/");
            assertThat(i.portion()).isNull();
        });
    }

    @Test
    void filtersUseInclusiveItemPriceAndTypeAndPreservePartialSemantics() {
        var selected = service.getMenuByRestaurantId(id("Pizza Tempo"), DishType.PASTA, new BigDecimal("19.50")).orElseThrow();
        assertThat(selected.items()).extracting(MenuDetails.Item::name).containsExactly("Паста Карбонара", "Паста Альфредо");
        var noResult = service.getMenuByRestaurantId(id("Васильки"), DishType.PASTA, null).orElseThrow();
        assertThat(noResult.status()).isEqualTo(MenuDetails.Status.NO_RESULTS);
        assertThat(noResult.items()).isEmpty();
        assertThat(noResult.coverage()).isEqualTo(MenuCoverage.PARTIAL);
        assertThat(noResult.notice()).isEqualTo("Не найдено в сохранённой части меню.");
        assertThat(service.getMenuByRestaurantId(id("Pizza Tempo"), null, BigDecimal.ONE).orElseThrow().status())
                .isEqualTo(MenuDetails.Status.NO_RESULTS);
    }

    @Test
    void inactiveItemsAreHiddenAndKnownEmptyMenuIsUnavailableRatherThanMissingRestaurant() throws Exception {
        var id = id("Хинкальня");
        jdbc.update("UPDATE menu_items SET active = FALSE WHERE restaurant_id = ?", id);
        entityManager.clear();
        assertThat(menu(id).status()).isEqualTo(MenuDetails.Status.DATA_UNAVAILABLE);
        assertThat(menu(id).coverage()).isEqualTo(MenuCoverage.PARTIAL);
        mvc.perform(get("/api/v1/restaurants/{id}/menu", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DATA_UNAVAILABLE"))
                .andExpect(jsonPath("$.items").isEmpty());
        assertThat(service.getMenuByRestaurantId(Long.MAX_VALUE, null, null)).isEmpty();
    }

    @Test
    void existingRestaurantWithoutMetadataIs200Unavailable() throws Exception {
        var restaurant = restaurants.saveAndFlush(new Restaurant("synthetic-no-menu", "Synthetic", "Synthetic",
                true, "https://example.test/catalog", LocalDate.of(2026, 10, 2), Set.of(Cuisine.ITALIAN)));
        var menu = menu(restaurant.getId());
        assertThat(menu.status()).isEqualTo(MenuDetails.Status.DATA_UNAVAILABLE);
        assertThat(menu.coverage()).isNull();
        mvc.perform(get("/api/v1/restaurants/{id}/menu", restaurant.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DATA_UNAVAILABLE"));
    }

    @Test
    void catalogRemainsReadableWithAllMenuRowsRemoved() {
        jdbc.update("DELETE FROM menu_items");
        entityManager.clear();
        assertThat(catalog.getCatalog(0, 20).content()).hasSize(3);
        for (var restaurant : catalog.getCatalog(0, 20).content()) {
            assertThat(catalog.getRestaurant(restaurant.id()).orElseThrow().estimatedCheckPerGuest()).isPositive();
            assertThat(menu(restaurant.id()).status()).isEqualTo(MenuDetails.Status.DATA_UNAVAILABLE);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void restExposesDtoMetadataFilters404AndSwaggerWithoutEntities() throws Exception {
        long id = id("Pizza Tempo");
        mvc.perform(get("/api/v1/restaurants/{id}/menu", id).param("dishType", "PASTA").param("maxItemPriceByn", "19.50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.restaurantId").value(id))
                .andExpect(jsonPath("$.coverage").value("PARTIAL"))
                .andExpect(jsonPath("$.currency").value("BYN"))
                .andExpect(jsonPath("$.source").value("https://tempo.by/"))
                .andExpect(jsonPath("$.verifiedAt").value("2026-10-02"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].priceByn").value(19.50))
                .andExpect(jsonPath("$.items[0].restaurant").doesNotExist())
                .andExpect(jsonPath("$.items[0].seedKey").doesNotExist());
        mvc.perform(get("/api/v1/restaurants/{id}/menu", Long.MAX_VALUE)).andExpect(status().isNotFound());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/restaurants/{id}/menu'].get").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "10.001", "100000000", "abc"})
    void restRejectsInvalidPrice(String price) throws Exception {
        mvc.perform(get("/api/v1/restaurants/{id}/menu", id("Pizza Tempo")).param("maxItemPriceByn", price))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedTypeIsRejected() throws Exception {
        mvc.perform(get("/api/v1/restaurants/{id}/menu", id("Pizza Tempo")).param("dishType", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        assertThatThrownBy(() -> service.getMenuByRestaurantId(id("Pizza Tempo"), null, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void repeatedJsonImportPreservesCountsKeysAndIds() throws Exception {
        var before = items.findAll().stream().map(MenuItem::getId).sorted().toList();
        importText(seedJson());
        importText(seedJson());
        entityManager.flush();
        entityManager.clear();
        assertThat(items.findAll().stream().map(MenuItem::getId).sorted().toList()).isEqualTo(before);
        assertThat(restaurants.count()).isEqualTo(3);
    }

    @Test
    void importReplacesOnlyListedSubsetUpdatesExistingIdsAndRemovesOmittedItems() throws Exception {
        var before = menu(id("Pizza Tempo"));
        var tree = mapper.readTree(seedJson());
        var menus = (com.fasterxml.jackson.databind.node.ArrayNode) tree.get("menus");
        var tempo = menus.get(1).deepCopy();
        menus.removeAll().add(tempo);
        var selected = (com.fasterxml.jackson.databind.node.ArrayNode) tempo.get("items");
        var first = (com.fasterxml.jackson.databind.node.ObjectNode) selected.get(0).deepCopy();
        first.put("priceByn", new BigDecimal("20.00"));
        selected.removeAll().add(first);
        importText(mapper.writeValueAsString(tree));
        entityManager.flush();
        entityManager.clear();
        assertThat(menu(id("Pizza Tempo")).items()).hasSize(1);
        assertThat(menu(id("Pizza Tempo")).items().getFirst().id()).isEqualTo(before.items().getFirst().id());
        assertThat(menu(id("Pizza Tempo")).items().getFirst().priceByn()).isEqualByComparingTo("20.00");
        assertThat(menu(id("Васильки")).items()).hasSize(6);
        assertThat(menu(id("Хинкальня")).items()).hasSize(6);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void unknownSecondRestaurantRollsBackAlreadyWrittenFirstMenuAndMetadata() throws Exception {
        var before = menu(id("Васильки"));
        var invalid = seedJson().replace("22.90", "1.00").replace("2026-10-02", "2026-10-01")
                .replace("pizza-tempo-karla-marksa-26", "unknown-restaurant");
        try {
            assertThatThrownBy(() -> importText(invalid)).isInstanceOf(IllegalArgumentException.class);
            assertThat(menu(id("Васильки"))).isEqualTo(before);
            assertThat(restaurants.count()).isEqualTo(3);
        } finally {
            importText(seedJson());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "UPDATE restaurants SET menu_coverage = 'FULL' WHERE id = 1",
        "UPDATE restaurants SET menu_source = NULL WHERE id = 1",
        "UPDATE restaurants SET menu_verified_at = NULL WHERE id = 1",
        "UPDATE menu_items SET price_byn = 0 WHERE restaurant_id = 1",
        "UPDATE menu_items SET price_byn = -1 WHERE restaurant_id = 1",
        "UPDATE menu_items SET price_byn = NULL WHERE restaurant_id = 1",
        "UPDATE menu_items SET restaurant_id = 999999 WHERE restaurant_id = 1",
        "UPDATE menu_items SET category = 'OTHER' WHERE restaurant_id = 1",
        "UPDATE menu_items SET dish_type = 'PASTA' WHERE restaurant_id = 1",
        "UPDATE menu_items SET source = ' ' WHERE restaurant_id = 1",
        "UPDATE menu_items SET name = ' ' WHERE restaurant_id = 1",
        "UPDATE menu_items SET seed_key = 'duplicate' WHERE restaurant_id = 1",
        "UPDATE menu_items SET active = NULL WHERE restaurant_id = 1"
    })
    void databaseRejectsInvalidMenuData(String sql) {
        assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01", "1.001", "100000000.00"})
    void importRejectsInvalidMoneyWithoutChangingRows(String price) throws Exception {
        var before = menu(id("Васильки"));
        assertThatThrownBy(() -> importText(seedJson().replace("22.90", price)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(menu(id("Васильки"))).isEqualTo(before);
    }

    @Test
    void importRejectsUnsupportedTypesMalformedJsonAndMissingOrDuplicateMetadata() throws Exception {
        String seed = seedJson();
        for (String invalid : List.of(seed.replace("\"MAIN\"", "\"OTHER\""),
                seed.replace("\"PARTIAL\"", "\"FULL\""), seed.replace("2026-10-02", "invalid-date"),
                seed.replace("draniki-goulash", "draniki-machanka"), seed.replace("https://vasilki.by/", "file:///tmp/menu"),
                seed.replace("\"verifiedAt\": \"2026-10-02\"", "\"verifiedAt\": null"),
                seed.replace("\"coverage\": \"PARTIAL\"", "\"coverage\": null"),
                seed.replace("\"category\": \"MAIN\"", "\"category\": null"),
                seed.replace("22.90", "null"), seed + " {}", "{}", "null", "{broken")) {
            long before = items.count();
            assertThatThrownBy(() -> importText(invalid)).isInstanceOfAny(IllegalArgumentException.class, java.io.IOException.class);
            assertThat(items.count()).isEqualTo(before);
        }
    }

    @Test
    void importAcceptsEmptySavedSubsetButRejectsMoreThanTenItemsDuplicateRestaurantsAndMissingActive() throws Exception {
        var tree = mapper.readTree(seedJson());
        var menus = (com.fasterxml.jackson.databind.node.ArrayNode) tree.get("menus");
        var firstMenu = menus.get(0).deepCopy();
        menus.removeAll().add(firstMenu);
        var selected = (com.fasterxml.jackson.databind.node.ArrayNode) firstMenu.get("items");
        selected.removeAll();
        importText(mapper.writeValueAsString(tree));
        entityManager.flush();
        entityManager.clear();
        assertThat(menu(id("Васильки")).status()).isEqualTo(MenuDetails.Status.DATA_UNAVAILABLE);
        assertThat(menu(id("Васильки")).coverage()).isEqualTo(MenuCoverage.PARTIAL);
        assertThat(menu(id("Pizza Tempo")).items()).hasSize(6);
        var template = mapper.readTree(seedJson()).get("menus").get(0).get("items").get(0);
        for (int index = 0; index < 11; index++) {
            var item = (com.fasterxml.jackson.databind.node.ObjectNode) template.deepCopy();
            item.put("seedKey", "synthetic-" + index);
            selected.add(item);
        }
        assertThatThrownBy(() -> importText(mapper.writeValueAsString(tree))).isInstanceOf(IllegalArgumentException.class);
        selected.removeAll();
        menus.add(firstMenu.deepCopy());
        assertThatThrownBy(() -> importText(mapper.writeValueAsString(tree))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> importText(seedJson().replace("\"active\": true", "\"active\": null")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package by.ilya.restaurantbot.miniapp;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import by.ilya.restaurantbot.search.RestaurantSearchService;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "restaurant-bot.telegram.bot-token=" + InitDataFixture.TOKEN)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MiniAppApiTest.FixedClock.class)
class MiniAppApiTest {
    @TestConfiguration
    static class FixedClock {
        @Bean @Primary Clock miniAppClock() {
            return Clock.fixed(Instant.ofEpochSecond(InitDataFixture.NOW), ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired RestaurantSearchService search;
    private final String auth = InitDataFixture.signed(InitDataFixture.NOW);
    private static final String VALID = """
            {"guests":2,"totalBudgetByn":150,"date":"2026-10-07","time":"19:00"}
            """;

    @Test
    void searchUsesExactlyTheExistingServiceOrderAndTotalsAndNarrowFields() throws Exception {
        var response = mvc.perform(post("/api/miniapp/v1/search").header("X-Telegram-Init-Data", auth)
                .contentType("application/json").content(VALID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.length()").value(3))
                .andExpect(jsonPath("$.cards[0].cuisines[0]").isString())
                .andExpect(jsonPath("$.cards[0].openingSummary").isString())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString();
        var expected = search.search(new MiniAppSearchRequest(2, new java.math.BigDecimal("150"),
                "2026-10-07", "19:00", null).toSearchRequest());
        var cards = mapper.readTree(response).get("cards");
        for (int i = 0; i < 3; i++) {
            assertThat(cards.get(i).get("id").asLong()).isEqualTo(expected.candidates().get(i).restaurant().id());
            assertThat(cards.get(i).get("estimatedTotalByn").decimalValue())
                    .isEqualByComparingTo(expected.candidates().get(i).estimatedTotalByn());
        }
        assertThat(response).doesNotContain("seedKey", "catalogSource", "checkSource", "DERIVED", "matchCount",
                "normalizedCriteria", "allowedReasonCodes", "openingIntervals", "https://", InitDataFixture.TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "null", "{", "{\"guests\":1.5}",
        "{\"guests\":7,\"totalBudgetByn\":150,\"date\":\"2026-10-07\",\"time\":\"19:00\"}",
        "{\"guests\":2,\"totalBudgetByn\":0,\"date\":\"2026-10-07\",\"time\":\"19:00\"}",
        "{\"guests\":2,\"totalBudgetByn\":100.001,\"date\":\"2026-10-07\",\"time\":\"19:00\"}",
        "{\"guests\":2,\"totalBudgetByn\":150,\"date\":\"2026-10-14\",\"time\":\"19:00\"}",
        "{\"guests\":2,\"totalBudgetByn\":150,\"date\":\"2026-10-06\",\"time\":\"19:00\"}",
        "{\"guests\":2,\"totalBudgetByn\":150,\"date\":\"2026-10-07\",\"time\":\"25:00\"}",
        "{\"guests\":2,\"totalBudgetByn\":150,\"date\":\"2026-10-07\",\"time\":\"19:00\",\"cuisine\":\"SQL\"}"
    })
    void invalidInputHasControlled400(String body) throws Exception {
        mvc.perform(post("/api/miniapp/v1/search").header("X-Telegram-Init-Data", auth)
                .contentType("application/json").content(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void noResultsIsSuccessfulEmptyResponse() throws Exception {
        mvc.perform(post("/api/miniapp/v1/search").header("X-Telegram-Init-Data", auth)
                .contentType("application/json").content(VALID.replace("150", "0.01")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cards").isEmpty());
    }

    @Test
    void knownDetailsOnlyExposeUiFieldsAndSecondaryEvidence() throws Exception {
        String body = mvc.perform(get("/api/miniapp/v1/restaurants/1").header("X-Telegram-Init-Data", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").isNotEmpty())
                .andExpect(jsonPath("$.openingInformation").isNotEmpty())
                .andExpect(jsonPath("$.evidence[0].verifiedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("seedKey", "checkEstimationType", "DERIVED", "tags", "hibernate", InitDataFixture.TOKEN);
    }

    @Test
    void evidenceLinksExcludeStoredCuratorMethodology() throws Exception {
        long id = jdbc.queryForObject("SELECT MIN(id) FROM restaurants WHERE name = 'Coffee Embassy'", Long.class);
        String body = mvc.perform(get("/api/miniapp/v1/restaurants/" + id).header("X-Telegram-Init-Data", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("PUBLISHED", "DERIVED", "сценари", "методик", " — ");
        assertThat(mapper.readTree(body).get("evidence").get(1).get("url").asText())
                .isEqualTo("https://embassy.by/nashi-kafe");
    }

    @Test
    void partialMenuAndUnavailableMenuHaveHonestMessages() throws Exception {
        mvc.perform(get("/api/miniapp/v1/restaurants/1/menu").header("X-Telegram-Init-Data", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.notice").value(org.hamcrest.Matchers.containsString("часть меню")))
                .andExpect(jsonPath("$.items[0].priceByn").isNumber())
                .andExpect(jsonPath("$.items[0].source").doesNotExist())
                .andExpect(jsonPath("$.items[0].id").doesNotExist());
        long id = jdbc.queryForObject("SELECT MIN(id) FROM restaurants WHERE active = TRUE AND menu_coverage IS NULL", Long.class);
        mvc.perform(get("/api/miniapp/v1/restaurants/" + id + "/menu").header("X-Telegram-Init-Data", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.notice").value("Для этого заведения сохранённое меню пока недоступно."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "999999999"})
    void arbitraryUnknownIdsHaveControlled404(String id) throws Exception {
        for (String suffix : new String[]{"", "/menu"}) {
            mvc.perform(get("/api/miniapp/v1/restaurants/" + id + suffix).header("X-Telegram-Init-Data", auth))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Test
    void defaultRequiresAuthOnEveryApiRouteEvenInTestProfile() throws Exception {
        mvc.perform(post("/api/miniapp/v1/search").contentType("application/json").content(VALID))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_FAILED"));
        for (String path : new String[]{"/restaurants/1", "/restaurants/1/menu"}) {
            mvc.perform(get("/api/miniapp/v1" + path)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/miniapp/v1" + path).header("X-Telegram-Init-Data", auth.replace("synthetic-query", "tampered")))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/miniapp/v1" + path).header("X-Telegram-Init-Data", InitDataFixture.signed(InitDataFixture.NOW - 3601)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void staticEntryAndAssetsArePublic() throws Exception {
        mvc.perform(get("/miniapp/")).andExpect(status().isOk()).andExpect(forwardedUrl("/miniapp/index.html"));
        mvc.perform(get("/miniapp/index.html")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("search-form")));
        mvc.perform(get("/miniapp/style.css")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
        mvc.perform(get("/miniapp/app.js")).andExpect(status().isOk());
    }
}

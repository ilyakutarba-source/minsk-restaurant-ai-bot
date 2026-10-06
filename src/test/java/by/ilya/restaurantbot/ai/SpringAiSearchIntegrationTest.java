package by.ilya.restaurantbot.ai;

import java.time.Clock;
import java.util.List;

import by.ilya.restaurantbot.catalog.Cuisine;
import by.ilya.restaurantbot.search.RestaurantSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import static by.ilya.restaurantbot.ai.AiFixtures.FULL;
import static by.ilya.restaurantbot.ai.AiSearchReply.Status.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(SpringAiSearchIntegrationTest.FixedClock.class)
class SpringAiSearchIntegrationTest {
    @Autowired RestaurantSearchService service;
    @Autowired Clock clock;

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock aiTestClock() { return AiFixtures.CLOCK; }
    }

    private SpringAiSearchAdapter adapter(ProviderFixture fixture, org.apache.hc.client5.http.impl.classic.CloseableHttpClient http) {
        return new SpringAiSearchAdapter(AiConfiguration.createModel(http, "offline-fixture", fixture.origin()),
                service, clock, true);
    }

    @Test
    void eval1FullItalianRequestWithRealJavaSearchAndProductionSdk() throws Exception {
        String plan = """
                {"items":[{"position":1,"reasonCodes":["CUISINE_MATCH","BUDGET_MATCH"],"phrasing":"NEUTRAL"},{"position":2,"reasonCodes":["BUDGET_MATCH"],"phrasing":"NEUTRAL"},{"position":3,"reasonCodes":["HOURS_MATCH"],"phrasing":"NEUTRAL"}]}
                """;
        try (var fixture = new ProviderFixture(ProviderFixture.selection(FULL), ProviderFixture.explanation(plan));
             var http = new AiConfiguration().aiHttpClient()) {
            var reply = adapter(fixture, http).search("Сегодня в 21:00 нас двое, бюджет 150 BYN, итальянская кухня.");
            assertThat(reply.status()).isEqualTo(OK);
            assertThat(reply.searchResult().normalizedCriteria().guests()).isEqualTo(2);
            assertThat(reply.searchResult().normalizedCriteria().cuisine()).isEqualTo(Cuisine.ITALIAN);
            assertThat(reply.searchResult().candidates()).hasSize(3);
            assertThat(reply.text()).contains("Pizza Tempo", "Карла Маркса, 26", "65.40 BYN");
            assertThat(reply.explanationFallback()).isFalse();
            assertThat(reply.modelCalls()).isEqualTo(2);
            assertThat(reply.toolExecutions()).isEqualTo(1);
            var requests = fixture.requests();
            assertThat(requests).hasSize(2);
            assertThat(requests.getFirst().path("model").asText()).isEqualTo("gpt-4.1-mini");
            assertThat(requests.getFirst().path("tools")).hasSize(3);
            assertThat(requests.getFirst().path("tools")).extracting(t -> t.path("function").path("name").asText())
                    .containsExactly("searchRestaurants", "getRestaurantDetails", "getRestaurantMenu");
            var tool = requests.getFirst().path("tools").get(0).path("function");
            assertThat(tool.path("name").asText()).isEqualTo("searchRestaurants");
            assertThat(tool.path("parameters").path("additionalProperties").asBoolean()).isFalse();
            assertThat(requests.getFirst().path("parallel_tool_calls").asBoolean()).isFalse();
            assertThat(requests.getLast().path("tools").size()).isZero();
            assertThat(requests.getLast().path("tool_choice").asText()).isEqualTo("none");
            assertThat(requests.getLast().path("response_format").path("type").asText()).isEqualTo("json_schema");
            assertThat(requests.getLast().path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
            assertThat(requests.getLast().toString()).doesNotContain("Pizza Tempo", "Карла Маркса", "65.40", "https://tempo.by");
        }
    }

    @Test
    void eval11ImpossibleBudgetReturnsNoResultsWithoutRelaxedSearch() throws Exception {
        try (var fixture = new ProviderFixture(ProviderFixture.selection(FULL.replace("150", "1")));
             var http = new AiConfiguration().aiHttpClient()) {
            var reply = adapter(fixture, http).search("Двое, сегодня 21:00, общий бюджет 1 BYN, итальянская кухня.");
            assertThat(reply.status()).isEqualTo(NO_RESULTS);
            assertThat(reply.searchResult().candidates()).isEmpty();
            assertThat(fixture.requests()).hasSize(1);
            assertThat(reply.toolExecutions()).isEqualTo(1);
        }
    }

    @Test
    void eval12ExplanationMayUseOnlyConfirmedTagsAndPositions() throws Exception {
        String args = FULL.replace("ITALIAN", "GEORGIAN").replace("QUIET", "COZY");
        String valid = """
                {"items":[{"position":1,"reasonCodes":["COZY_TAG_MATCH"],"phrasing":"WARM"}]}
                """;
        for (var plan : List.of(valid, valid.replace("COZY_TAG_MATCH", "QUIET_TAG_MATCH"))) {
            try (var fixture = new ProviderFixture(ProviderFixture.selection(args), ProviderFixture.explanation(plan));
                 var http = new AiConfiguration().aiHttpClient()) {
                var reply = adapter(fixture, http).search("Двое, сегодня 21:00, 150 BYN всего, грузинская кухня, уютно.");
                assertThat(reply.status()).isEqualTo(OK);
                assertThat(reply.text()).contains("Хинкальня", "88.00 BYN").doesNotContain("«спокойно»");
                assertThat(reply.explanationFallback()).isEqualTo(!plan.equals(valid));
                if (plan.equals(valid)) assertThat(reply.text()).contains("совпадает запрошенный тег «уютно»");
                assertThat(fixture.requests()).hasSize(2);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500, 503, 0})
    void productionSdkAndHttpTransportNeverRetryFirstFailure(int status) throws Exception {
        var error = new ProviderFixture.Reply(status, "{\"private\":\"provider payload\"}");
        try (var fixture = new ProviderFixture(error, error, error);
             var http = new AiConfiguration().aiHttpClient()) {
            var reply = adapter(fixture, http).search("Полный запрос");
            assertThat(reply.status()).isEqualTo(TEMPORARILY_UNAVAILABLE);
            assertThat(reply.modelCalls()).isEqualTo(1);
            assertThat(reply.toolExecutions()).isZero();
            assertThat(reply.text()).doesNotContain("private", "provider payload");
            assertThat(fixture.requests()).hasSize(1);
        }
    }

    @Test
    void productionSdkSecondFailureKeepsFactsAndDoesNotRetry() throws Exception {
        var error = new ProviderFixture.Reply(503, "{}");
        try (var fixture = new ProviderFixture(ProviderFixture.selection(FULL), error, error);
             var http = new AiConfiguration().aiHttpClient()) {
            var reply = adapter(fixture, http).search("Полный запрос");
            assertThat(reply.status()).isEqualTo(OK);
            assertThat(reply.explanationFallback()).isTrue();
            assertThat(reply.text()).contains("Pizza Tempo", "65.40 BYN");
            assertThat(fixture.requests()).hasSize(2);
            assertThat(reply.toolExecutions()).isEqualTo(1);
        }
    }

    @Test
    void productionResponseTimeoutConsumesOneAttemptAndDoesNotExecuteOrRetryTool() throws Exception {
        try (var fixture = new ProviderFixture(new ProviderFixture.Reply(200, "{}", 11_000));
             var http = new AiConfiguration().aiHttpClient()) {
            var reply = adapter(fixture, http).search("Полный запрос");
            assertThat(reply.status()).isEqualTo(TEMPORARILY_UNAVAILABLE);
            assertThat(reply.modelCalls()).isEqualTo(1);
            assertThat(reply.toolExecutions()).isZero();
            assertThat(fixture.requests()).hasSize(1);
            assertThat(reply.text()).doesNotContain("Exception", "timeout", "payload");
        }
    }
}

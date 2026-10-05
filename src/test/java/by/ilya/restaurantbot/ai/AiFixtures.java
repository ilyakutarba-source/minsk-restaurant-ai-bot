package by.ilya.restaurantbot.ai;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import by.ilya.restaurantbot.catalog.CheckEstimationType;
import by.ilya.restaurantbot.catalog.Cuisine;
import by.ilya.restaurantbot.catalog.RestaurantDetails;
import by.ilya.restaurantbot.catalog.RestaurantTag;
import by.ilya.restaurantbot.search.SearchCriteria;
import by.ilya.restaurantbot.search.SearchResult;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

final class AiFixtures {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T21:30:00Z"), ZoneOffset.UTC);
    static final String FULL = """
            {"guests":2,"totalBudgetByn":150,"date":"TODAY","time":"21:00",
             "cuisine":"ITALIAN","preferredTags":["QUIET"]}
            """;
    static final String PLAN = """
            {"items":[{"position":1,"reasonCodes":["BUDGET_MATCH","QUIET_TAG_MATCH"],"phrasing":"WARM"}]}
            """;

    static AssistantMessage.ToolCall tool(String name, String args) {
        return new AssistantMessage.ToolCall("call-1", "function", name, args);
    }

    static ChatResponse response(String content, String finish, List<AssistantMessage.ToolCall> calls) {
        return response(content, finish, calls, "");
    }

    static ChatResponse response(String content, String finish, List<AssistantMessage.ToolCall> calls, String refusal) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content(content)
                .properties(Map.of("refusal", refusal)).toolCalls(calls).build(),
                ChatGenerationMetadata.builder().finishReason(finish.toUpperCase(java.util.Locale.ROOT)).build())));
    }

    static ChatResponse selection(String args) {
        return response("ignored model prose", "tool_calls", List.of(tool(SearchRestaurantsTool.NAME, args)));
    }

    static ChatResponse explanation(String plan) {
        return response(plan, "stop", List.of());
    }

    static SearchResult result() {
        var date = LocalDate.of(2026, 10, 2);
        var restaurant = new RestaurantDetails(42L, "Synthetic fixture restaurant", "Synthetic fixture address", true,
                Set.of(Cuisine.ITALIAN), Set.of(RestaurantTag.QUIET), "https://example.test/catalog", date,
                new BigDecimal("32.70"), CheckEstimationType.PUBLISHED, "https://example.test/check", date,
                "https://example.test/hours", date,
                List.of(new RestaurantDetails.Hours(date.getDayOfWeek(), LocalTime.of(10, 0), LocalTime.of(23, 0), false)));
        return new SearchResult(new SearchCriteria(2, new BigDecimal("150.00"), date,
                LocalTime.of(21, 0), Cuisine.ITALIAN, Set.of(RestaurantTag.QUIET)), "BYN",
                List.of(new SearchResult.Candidate(restaurant, new BigDecimal("65.40"), 1,
                    List.of(SearchResult.ReasonCode.CUISINE_MATCH, SearchResult.ReasonCode.BUDGET_MATCH,
                        SearchResult.ReasonCode.HOURS_MATCH, SearchResult.ReasonCode.QUIET_TAG_MATCH))),
                List.of("Ориентировочный чек, не гарантия итоговой суммы."));
    }
}

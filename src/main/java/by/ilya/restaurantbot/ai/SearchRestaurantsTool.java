package by.ilya.restaurantbot.ai;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import by.ilya.restaurantbot.search.RestaurantSearchService;
import by.ilya.restaurantbot.search.SearchCriteria;
import by.ilya.restaurantbot.search.SearchRequest;
import by.ilya.restaurantbot.search.SearchResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static by.ilya.restaurantbot.ai.AiSearchReply.Status;

/** A new instance is created per turn; no model response can trigger a second search. */
final class SearchRestaurantsTool implements ToolCallback {
    static final String NAME = "searchRestaurants";
    private static final ToolDefinition DEFINITION = ToolDefinition.builder().name(NAME)
            .description("Search the own Minsk catalog for one visit. BYN budget is total for all guests. "
                    + "Use null for missing or ambiguous required criteria; never guess or relax filters.")
            .inputSchema("""
                    {"type":"object","additionalProperties":false,
                     "properties":{
                      "guests":{"type":["integer","null"],"minimum":1,"maximum":6},
                      "totalBudgetByn":{"type":["number","null"],"exclusiveMinimum":0,"multipleOf":0.01},
                      "date":{"type":["string","null"],"description":"TODAY, TOMORROW or ISO date within next six days in Minsk"},
                      "time":{"type":["string","null"],"pattern":"^[0-9]{2}:[0-9]{2}$","description":"Unambiguous local HH:mm"},
                      "cuisine":{"type":["string","null"],"enum":["BELARUSIAN","ITALIAN","GEORGIAN",null]},
                      "preferredTags":{"type":["array","null"],"items":{"type":"string",
                       "enum":["COZY","QUIET","ROMANTIC","CASUAL","FRIENDS","PREMIUM"]},"maxItems":6}},
                     "required":[]}
                    """).build();

    private final RestaurantSearchService service;
    private final Clock clock;
    private final UnaryOperator<SearchRequest> prepare;
    private final List<String> ambiguous;
    private boolean consumed;
    private int executions;
    private Status status;
    private SearchResult result;
    private List<String> missing = List.of();

    SearchRestaurantsTool(RestaurantSearchService service, Clock clock) {
        this(service, clock, UnaryOperator.identity());
    }

    SearchRestaurantsTool(RestaurantSearchService service, Clock clock, UnaryOperator<SearchRequest> prepare) {
        this(service, clock, prepare, List.of());
    }

    SearchRestaurantsTool(RestaurantSearchService service, Clock clock, UnaryOperator<SearchRequest> prepare, List<String> ambiguous) {
        this.service = service;
        this.clock = clock;
        this.prepare = prepare;
        this.ambiguous = ambiguous;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return DEFINITION;
    }

    @Override
    public String call(String arguments) {
        if (consumed) {
            return "{\"status\":\"INVALID_INPUT\"}";
        }
        consumed = true;
        SearchRequest request;
        try {
            if (arguments == null || arguments.length() > 2000) {
                throw new IllegalArgumentException();
            }
            var mapper = AiJson.mapper();
            var tree = mapper.readTree(arguments);
            if (!tree.isObject() || (tree.hasNonNull("preferredTags")
                    && (!tree.get("preferredTags").isArray() || tree.get("preferredTags").size() > 6))) {
                throw new IllegalArgumentException();
            }
            // Parse the original numeric token directly into BigDecimal; a JSON tree may round via double.
            request = mapper.readValue(arguments, SearchRequest.class);
            if (request.totalBudgetByn() != null
                    && (long) request.totalBudgetByn().precision() - request.totalBudgetByn().scale() > 2000) {
                // Bound expanded decimal size before normalize/setScale, including tiny scientific tokens.
                throw new IllegalArgumentException();
            }
            // Validate supplied values with the existing rules even when other fields are missing.
            SearchCriteria.normalize(new SearchRequest(
                    request.guests() == null ? 1 : request.guests(),
                    request.totalBudgetByn() == null ? BigDecimal.ONE : request.totalBudgetByn(),
                    request.date() == null ? "TODAY" : request.date(),
                    request.time() == null ? "12:00" : request.time(),
                    request.cuisine(), request.preferredTags()), clock);
            request = prepare.apply(request);
            var fields = new ArrayList<String>();
            if (request.guests() == null) fields.add("guests");
            if (request.totalBudgetByn() == null) fields.add("totalBudgetByn");
            if (request.date() == null) fields.add("date");
            if (request.time() == null) fields.add("time");
            for (String field : ambiguous) if (!fields.contains(field)) fields.add(field);
            missing = List.copyOf(fields);
            if (!missing.isEmpty()) {
                status = Status.NEED_CLARIFICATION;
                return statusJson();
            }
        } catch (Exception invalid) {
            status = Status.INVALID_INPUT;
            return statusJson();
        }
        // Reserve before invoking the service: a failed search still consumes the attempt.
        executions++;
        try {
            result = service.search(request);
            status = result.candidates().isEmpty() ? Status.NO_RESULTS : Status.OK;
        } catch (RuntimeException unavailable) {
            status = Status.TEMPORARILY_UNAVAILABLE;
        }
        return statusJson();
    }

    private String statusJson() {
        // Full factual DTO stays in Java; no tool conversation is sent back to the model.
        return "{\"status\":\"" + status.name() + "\"}";
    }

    Status status() { return status; }
    SearchResult result() { return result; }
    int executions() { return executions; }
    List<String> missing() { return missing; }
}

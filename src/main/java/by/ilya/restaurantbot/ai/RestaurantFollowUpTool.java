package by.ilya.restaurantbot.ai;

import java.math.BigDecimal;

import by.ilya.restaurantbot.catalog.DishType;
import by.ilya.restaurantbot.catalog.MenuService;
import by.ilya.restaurantbot.catalog.RestaurantService;
import by.ilya.restaurantbot.conversation.ReferenceResolver;
import by.ilya.restaurantbot.conversation.RestaurantReference;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static by.ilya.restaurantbot.ai.AiSearchReply.Status;
import static by.ilya.restaurantbot.ai.RestaurantFollowUpResult.*;

/** Two read-only callbacks share strict reference validation and the existing Java resolver. */
final class RestaurantFollowUpTool implements ToolCallback {
    static final String MENU = "getRestaurantMenu";
    static final String DETAILS = "getRestaurantDetails";
    private static final String REFERENCE_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{
             "ordinal":{"type":["integer","null"],"minimum":1,"maximum":3},
             "last":{"type":["boolean","null"],"enum":[true,null]},
             "name":{"type":["string","null"],"minLength":1,"maxLength":200}},
             "oneOf":[{"required":["ordinal"],"properties":{"ordinal":{"type":"integer"},"last":{"type":"null"},"name":{"type":"null"}}},
                      {"required":["last"],"properties":{"ordinal":{"type":"null"},"last":{"const":true},"name":{"type":"null"}}},
                      {"required":["name"],"properties":{"ordinal":{"type":"null"},"last":{"type":"null"},"name":{"type":"string"}}}]}
            """;
    record MenuRequest(RestaurantReference reference, DishType dishType, BigDecimal maxItemPriceByn) { }
    record DetailsRequest(RestaurantReference reference, Focus focus) { }

    private final Kind kind;
    private final Long chatId;
    private final ReferenceResolver resolver;
    private final MenuService menus;
    private final RestaurantService catalog;
    private final ToolDefinition definition;
    private boolean consumed;
    private int executions;
    private Status status;
    private RestaurantFollowUpResult result;

    RestaurantFollowUpTool(Kind kind, Long chatId, ReferenceResolver resolver, MenuService menus, RestaurantService catalog) {
        this.kind = kind;
        this.chatId = chatId;
        this.resolver = resolver;
        this.menus = menus;
        this.catalog = catalog;
        String filters = kind == Kind.MENU ? """
                ,"dishType":{"type":["string","null"],"enum":["PASTA",null]},
                "maxItemPriceByn":{"type":["number","null"],"exclusiveMinimum":0,"maximum":99999999.99,"multipleOf":0.01}
                """ : """
                ,"focus":{"type":["string","null"],"enum":["ALL","HOURS","CONTACTS","RATING",null]}
                """;
        this.definition = ToolDefinition.builder().name(kind == Kind.MENU ? MENU : DETAILS)
                .description(kind == Kind.MENU
                        ? "Read the saved PARTIAL menu for one reference. PASTA is the only supported dishType; maxItemPriceByn is per item. Never infer availability or search restaurants."
                        : "Reread own catalog details for one reference, including weekly HOURS or CONTACTS. Rating is unavailable. Never take facts from memory or search restaurants.")
                .inputSchema("{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{\"reference\":"
                        + REFERENCE_SCHEMA + filters + "},\"required\":[\"reference\"]}").build();
    }

    @Override public ToolDefinition getToolDefinition() { return definition; }

    @Override public String call(String arguments) {
        if (consumed) return "{\"status\":\"INVALID_INPUT\"}";
        consumed = true;
        RestaurantReference reference;
        DishType dishType = null;
        BigDecimal price = null;
        Focus focus = Focus.ALL;
        try {
            if (arguments == null || arguments.length() > 2000) throw new IllegalArgumentException();
            if (kind == Kind.MENU) {
                var request = AiJson.mapper().readValue(arguments, MenuRequest.class);
                reference = request.reference();
                dishType = request.dishType();
                price = request.maxItemPriceByn();
                // Same persisted NUMERIC(10,2) bounds; validate before any reference/catalog read.
                if (price != null && (price.signum() <= 0 || price.scale() > 2
                        || (long) price.precision() - price.scale() > 8)) throw new IllegalArgumentException();
            } else {
                var request = AiJson.mapper().readValue(arguments, DetailsRequest.class);
                reference = request.reference();
                focus = request.focus() == null ? Focus.ALL : request.focus();
            }
            if (reference == null || !reference.isValid()) throw new IllegalArgumentException();
        } catch (Exception invalid) {
            status = Status.INVALID_INPUT;
            result = new RestaurantFollowUpResult(kind, focus, null, null);
            return statusJson();
        }
        result = new RestaurantFollowUpResult(kind, focus, null, null);
        // A stateless entry has no selection scope. Never substitute another chat ID.
        if (chatId == null) {
            status = Status.NEED_CLARIFICATION;
            return statusJson();
        }
        executions++; // Resolution/service failures also consume the single attempt.
        try {
            var resolved = resolver.resolve(chatId, reference);
            status = Status.valueOf(resolved.status().name());
            if (status != Status.OK) return statusJson();
            long id = resolved.restaurantId();
            if (kind == Kind.MENU) {
                var menu = menus.getMenuByRestaurantId(id, dishType, price);
                if (menu.isEmpty()) status = Status.NOT_FOUND;
                else {
                    var data = menu.orElseThrow();
                    result = new RestaurantFollowUpResult(kind, focus, null, data);
                    status = switch (data.status()) {
                        case AVAILABLE -> Status.OK;
                        case NO_RESULTS -> Status.NO_RESULTS;
                        case DATA_UNAVAILABLE -> Status.DATA_UNAVAILABLE;
                    };
                }
            } else {
                var details = catalog.getRestaurant(id);
                if (details.isEmpty()) status = Status.NOT_FOUND;
                else result = new RestaurantFollowUpResult(kind, focus, details.orElseThrow(), null);
            }
        } catch (RuntimeException unavailable) {
            status = Status.TEMPORARILY_UNAVAILABLE;
        }
        return statusJson();
    }

    private String statusJson() { return "{\"status\":\"" + status.name() + "\"}"; }
    Status status() { return status; }
    int executions() { return executions; }
    RestaurantFollowUpResult result() { return result; }
}

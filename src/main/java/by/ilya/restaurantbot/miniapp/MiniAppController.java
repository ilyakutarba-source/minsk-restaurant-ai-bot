package by.ilya.restaurantbot.miniapp;

import java.util.List;
import by.ilya.restaurantbot.ai.AiSearchReply;
import by.ilya.restaurantbot.ai.SpringAiSearchAdapter;
import by.ilya.restaurantbot.conversation.CriteriaAmbiguity;
import by.ilya.restaurantbot.catalog.MenuService;
import by.ilya.restaurantbot.catalog.RestaurantService;
import by.ilya.restaurantbot.search.RestaurantSearchService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.ObjectProvider;

@RestController
@RequestMapping("/api/miniapp/v1")
public class MiniAppController {
    private final RestaurantSearchService search;
    private final RestaurantService restaurants;
    private final MenuService menus;
    private final ObjectProvider<SpringAiSearchAdapter> ai;

    public MiniAppController(RestaurantSearchService search, RestaurantService restaurants, MenuService menus,
                             ObjectProvider<SpringAiSearchAdapter> ai) {
        this.search = search;
        this.restaurants = restaurants;
        this.menus = menus;
        this.ai = ai;
    }

    @PostMapping("/search/natural")
    public ResponseEntity<?> natural(@RequestBody NaturalSearchRequest request) {
        if (request == null || request.query() == null || request.query().isBlank() || request.query().length() > 2000) {
            return ResponseEntity.badRequest().body(new MiniAppErrors.Error("INVALID_INPUT",
                    "Напишите непустой запрос длиной до 2000 символов."));
        }
        var adapter = ai.getIfAvailable();
        if (adapter == null) return ResponseEntity.status(503).body(MiniAppErrors.error("TEMPORARY_ERROR"));
        var reply = adapter.search(request.query(), List.of(), java.util.function.UnaryOperator.identity(),
                CriteriaAmbiguity.fields(request.query()));
        return switch (reply.status()) {
            case OK, NO_RESULTS -> {
                var result = reply.searchResult();
                if (result == null) yield ResponseEntity.status(503).body(MiniAppErrors.error("TEMPORARY_ERROR"));
                yield ResponseEntity.ok(new NaturalSearchResponse(reply.status(), result.candidates().stream()
                        .map(c -> MiniAppMapper.card(c, result)).toList(), result.warnings(), null));
            }
            case NEED_CLARIFICATION, NOT_FOUND, DATA_UNAVAILABLE -> ResponseEntity.ok(new NaturalSearchResponse(
                    AiSearchReply.Status.NEED_CLARIFICATION, List.of(), List.of(), reply.missingFields().isEmpty()
                    ? "Укажите в одном запросе число гостей (1–6), общий бюджет в BYN, дату и точное время HH:mm в Минске."
                    : SpringAiSearchAdapter.clarification(reply.missingFields())));
            case INVALID_INPUT -> ResponseEntity.badRequest().body(MiniAppErrors.error("INVALID_INPUT"));
            case TEMPORARILY_UNAVAILABLE -> ResponseEntity.status(503).body(MiniAppErrors.error("TEMPORARY_ERROR"));
        };
    }

    @PostMapping("/search")
    public SearchResponse search(@RequestBody MiniAppSearchRequest request) {
        var result = search.search(request.toSearchRequest());
        return new SearchResponse(result.candidates().stream().map(c -> MiniAppMapper.card(c, result)).toList(),
                result.warnings());
    }

    @GetMapping("/restaurants/{id}")
    public MiniAppRestaurantDetails details(@PathVariable long id) {
        return restaurants.getRestaurant(id).filter(r -> r.active()).map(MiniAppMapper::details)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/restaurants/{id}/menu")
    public MiniAppMenuResponse menu(@PathVariable long id) {
        restaurants.getRestaurant(id).filter(r -> r.active())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return menus.getMenuByRestaurantId(id, null, null).map(MiniAppMapper::menu)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public record SearchResponse(List<MiniAppRestaurantCard> cards, List<String> warnings) { }
    public record NaturalSearchRequest(String query) { }
    public record NaturalSearchResponse(AiSearchReply.Status status, List<MiniAppRestaurantCard> cards,
                                        List<String> warnings, String message) { }
}

package by.ilya.restaurantbot.miniapp;

import java.util.List;
import by.ilya.restaurantbot.catalog.MenuService;
import by.ilya.restaurantbot.catalog.RestaurantService;
import by.ilya.restaurantbot.search.RestaurantSearchService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/miniapp/v1")
public class MiniAppController {
    private final RestaurantSearchService search;
    private final RestaurantService restaurants;
    private final MenuService menus;

    public MiniAppController(RestaurantSearchService search, RestaurantService restaurants, MenuService menus) {
        this.search = search;
        this.restaurants = restaurants;
        this.menus = menus;
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
}

package by.ilya.restaurantbot.api;

import by.ilya.restaurantbot.catalog.CatalogPage;
import by.ilya.restaurantbot.catalog.RestaurantDetails;
import by.ilya.restaurantbot.catalog.RestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/restaurants")
public class RestaurantController {
    private final RestaurantService service;

    public RestaurantController(RestaurantService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Read the active own catalog")
    @ApiResponse(responseCode = "400", description = "Invalid page or size")
    public CatalogPage getCatalog(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
        }
        return service.getCatalog(page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read own restaurant details")
    @ApiResponse(responseCode = "404", description = "Restaurant not found")
    public ResponseEntity<RestaurantDetails> getRestaurant(@PathVariable long id) {
        return ResponseEntity.of(service.getRestaurant(id));
    }
}

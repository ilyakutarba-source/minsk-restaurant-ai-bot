package by.ilya.restaurantbot.api;

import java.math.BigDecimal;
import by.ilya.restaurantbot.catalog.DishType;
import by.ilya.restaurantbot.catalog.MenuDetails;
import by.ilya.restaurantbot.catalog.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/restaurants")
public class MenuController {
    private final MenuService service;

    public MenuController(MenuService service) {
        this.service = service;
    }

    @GetMapping("/{id}/menu")
    @Operation(summary = "Read the saved partial menu", description = "Missing saved items do not establish absence from the full restaurant menu.")
    @ApiResponse(responseCode = "400", description = "Invalid menu filter")
    @ApiResponse(responseCode = "404", description = "Restaurant not found")
    public ResponseEntity<MenuDetails> getMenu(@PathVariable long id,
            @RequestParam(required = false) DishType dishType,
            @RequestParam(required = false) BigDecimal maxItemPriceByn) {
        try {
            return ResponseEntity.of(service.getMenuByRestaurantId(id, dishType, maxItemPriceByn));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid menu filter");
        }
    }
}

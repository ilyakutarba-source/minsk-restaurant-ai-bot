package by.ilya.restaurantbot.api;

import by.ilya.restaurantbot.search.RestaurantSearchService;
import by.ilya.restaurantbot.search.SearchRequest;
import by.ilya.restaurantbot.search.SearchResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
    private final RestaurantSearchService service;

    public RecommendationController(RestaurantSearchService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Search the own restaurant catalog for a visit",
            description = "Java hard filters and deterministic ranking; no AI or external API calls. Returns up to three candidates.")
    @ApiResponse(responseCode = "200", description = "Ordered candidates, or an empty list")
    @ApiResponse(responseCode = "400", description = "Incomplete or invalid visit criteria")
    @ApiResponse(responseCode = "503", description = "Catalog database unavailable")
    public SearchResult recommend(@RequestBody SearchRequest request) {
        try {
            return service.search(request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (DataAccessException | TransactionException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog database unavailable");
        }
    }
}

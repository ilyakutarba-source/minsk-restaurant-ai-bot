package by.ilya.restaurantbot.catalog;

import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RestaurantService {
    private final RestaurantRepository repository;

    public RestaurantService(RestaurantRepository repository) {
        this.repository = repository;
    }

    public CatalogPage getCatalog(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be nonnegative; size must be between 1 and 100");
        }
        var result = repository.findByActiveTrue(PageRequest.of(page, size, Sort.by("id")));
        return new CatalogPage(result.getContent().stream().map(RestaurantDetails::from).toList(),
                page, size, result.getTotalElements());
    }

    public Optional<RestaurantDetails> getRestaurant(long id) {
        return repository.findById(id).map(RestaurantDetails::from);
    }
}

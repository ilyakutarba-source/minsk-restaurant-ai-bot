package by.ilya.restaurantbot.catalog;

import java.util.List;

public record CatalogPage(List<RestaurantDetails> content, int page, int size, long totalElements) {
}

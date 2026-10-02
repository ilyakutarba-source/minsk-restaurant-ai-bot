package by.ilya.restaurantbot.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MenuService {
    private final RestaurantRepository restaurants;
    private final MenuItemRepository items;

    public MenuService(RestaurantRepository restaurants, MenuItemRepository items) {
        this.restaurants = restaurants;
        this.items = items;
    }

    public Optional<MenuDetails> getMenuByRestaurantId(long id, DishType dishType, BigDecimal maxItemPriceByn) {
        if (maxItemPriceByn != null) {
            MenuDataset.requirePrice(maxItemPriceByn);
        }
        return restaurants.findById(id).map(restaurant -> {
            var saved = items.findByRestaurantIdOrderByIdAsc(id).stream().filter(MenuItem::isActive).toList();
            boolean unavailable = restaurant.getMenuCoverage() == null || saved.isEmpty();
            var selected = unavailable ? List.<MenuDetails.Item>of() : saved.stream()
                    .filter(i -> dishType == null || i.getDishType() == dishType)
                    .filter(i -> maxItemPriceByn == null || i.getPriceByn().compareTo(maxItemPriceByn) <= 0)
                    .limit(10)
                    .map(i -> new MenuDetails.Item(i.getId(), i.getName(), i.getCategory(), i.getDishType(),
                            i.getPriceByn(), i.getPortion(), i.getDescription(),
                            i.getSource() == null ? restaurant.getMenuSource() : i.getSource()))
                    .toList();
            var status = unavailable ? MenuDetails.Status.DATA_UNAVAILABLE
                    : selected.isEmpty() ? MenuDetails.Status.NO_RESULTS : MenuDetails.Status.AVAILABLE;
            var notice = switch (status) {
                case DATA_UNAVAILABLE -> "Сохранённое меню недоступно.";
                case NO_RESULTS -> "Не найдено в сохранённой части меню.";
                case AVAILABLE -> "Сохранена только часть меню. Наличие блюд и актуальность цен не гарантируются.";
            };
            return new MenuDetails(id, status, restaurant.getMenuCoverage(), restaurant.getMenuSource(),
                    restaurant.getMenuVerifiedAt(), "BYN", notice, selected);
        });
    }
}

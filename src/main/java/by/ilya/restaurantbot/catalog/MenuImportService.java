package by.ilya.restaurantbot.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuImportService {
    private final RestaurantRepository restaurants;
    private final MenuItemRepository items;

    public MenuImportService(RestaurantRepository restaurants, MenuItemRepository items) {
        this.restaurants = restaurants;
        this.items = items;
    }

    @Transactional(rollbackFor = IOException.class)
    public void importJson(InputStream input) throws IOException {
        var dataset = MenuDataset.read(input);
        for (var menu : dataset.menus()) {
            var restaurant = restaurants.findBySeedKey(menu.restaurantSeedKey())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown restaurant seed key"));
            restaurant.setPartialMenu(menu.source(), menu.verifiedAt());
            var existing = items.findByRestaurantIdOrderByIdAsc(restaurant.getId()).stream()
                    .collect(Collectors.toMap(MenuItem::getSeedKey, Function.identity()));
            var retainedKeys = new HashSet<String>();
            for (var item : menu.items()) {
                retainedKeys.add(item.seedKey());
                var entity = existing.get(item.seedKey());
                if (entity == null) {
                    items.save(new MenuItem(restaurant, item));
                } else {
                    entity.update(item);
                }
            }
            existing.values().stream().filter(i -> !retainedKeys.contains(i.getSeedKey())).forEach(items::delete);
        }
        items.flush();
    }
}

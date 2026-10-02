package by.ilya.restaurantbot.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** A complete replacement of the saved subset for each listed restaurant. */
public record MenuDataset(List<RestaurantMenu> menus) {
    public static MenuDataset read(InputStream input) throws IOException {
        var mapper = new ObjectMapper().findAndRegisterModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        var dataset = mapper.readValue(input, MenuDataset.class);
        if (dataset == null) {
            throw new IllegalArgumentException("Menu dataset is required");
        }
        dataset.validate();
        return dataset;
    }

    public void validate() {
        if (menus == null || menus.isEmpty()) {
            throw new IllegalArgumentException("At least one restaurant menu is required");
        }
        var restaurantKeys = new HashSet<String>();
        for (var menu : menus) {
            if (menu == null) {
                throw new IllegalArgumentException("Null restaurant menu");
            }
            requireText(menu.restaurantSeedKey(), 100);
            if (!restaurantKeys.add(menu.restaurantSeedKey())) {
                throw new IllegalArgumentException("Duplicate restaurant key");
            }
            requireSource(menu.source());
            if (menu.coverage() != MenuCoverage.PARTIAL || menu.verifiedAt() == null
                    || menu.items() == null || menu.items().size() > 10) {
                throw new IllegalArgumentException("A saved menu requires PARTIAL metadata and at most 10 items");
            }
            var itemKeys = new HashSet<String>();
            for (var item : menu.items()) {
                validateItem(item);
                if (!itemKeys.add(item.seedKey())) {
                    throw new IllegalArgumentException("Duplicate item key in one restaurant menu");
                }
            }
        }
    }

    static void validateItem(Item item) {
        if (item == null) {
            throw new IllegalArgumentException("Null menu item");
        }
        requireText(item.seedKey(), 100);
        requireText(item.name(), 200);
        requirePrice(item.priceByn());
        if (item.category() == null || item.active() == null
                || (item.dishType() == DishType.PASTA && item.category() != MenuCategory.PASTA)) {
            throw new IllegalArgumentException("Unsupported menu category/type combination");
        }
        if (item.source() != null) {
            requireSource(item.source());
        }
        if (item.portion() != null) {
            requireText(item.portion(), 100);
        }
        if (item.description() != null) {
            requireText(item.description(), 1000);
        }
    }

    static void requirePrice(BigDecimal price) {
        if (price == null || price.signum() <= 0 || price.scale() > 2 || price.precision() - price.scale() > 8) {
            throw new IllegalArgumentException("A positive NUMERIC(10,2) BYN price is required");
        }
    }

    static void requireSource(String source) {
        requireText(source, 2000);
        var uri = URI.create(source);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Source must be an HTTPS URL without credentials");
        }
    }

    private static void requireText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength || !value.equals(value.strip())) {
            throw new IllegalArgumentException("Missing, oversized or untrimmed menu field");
        }
    }

    public record RestaurantMenu(String restaurantSeedKey, MenuCoverage coverage, String source,
                                 LocalDate verifiedAt, List<Item> items) {
    }

    public record Item(String seedKey, String name, MenuCategory category, DishType dishType,
                       BigDecimal priceByn, String portion, String description, String source, Boolean active) {
    }
}

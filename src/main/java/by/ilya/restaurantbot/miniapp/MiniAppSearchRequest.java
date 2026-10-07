package by.ilya.restaurantbot.miniapp;

import java.math.BigDecimal;
import java.util.Set;
import by.ilya.restaurantbot.catalog.Cuisine;
import by.ilya.restaurantbot.search.SearchRequest;

public record MiniAppSearchRequest(Integer guests, BigDecimal totalBudgetByn, String date, String time,
                                   Cuisine cuisine) {
    SearchRequest toSearchRequest() {
        return new SearchRequest(guests, totalBudgetByn, date, time, cuisine, Set.of());
    }
}

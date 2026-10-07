package by.ilya.restaurantbot.miniapp;

import java.math.BigDecimal;
import java.util.List;

public record MiniAppRestaurantCard(long id, String name, String address, List<String> cuisines,
        BigDecimal estimatedTotalByn, String openingSummary, List<String> reasons) { }

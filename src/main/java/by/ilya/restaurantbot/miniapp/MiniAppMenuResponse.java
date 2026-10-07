package by.ilya.restaurantbot.miniapp;

import java.math.BigDecimal;
import java.util.List;

public record MiniAppMenuResponse(boolean available, String notice, List<Item> items) {
    public record Item(String name, BigDecimal priceByn, String portion) { }
}

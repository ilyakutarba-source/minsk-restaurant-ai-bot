package by.ilya.restaurantbot.miniapp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MiniAppRestaurantDetails(long id, String name, String address, List<String> cuisines,
        BigDecimal estimatedCheckPerGuestByn, List<String> openingInformation, List<Evidence> evidence) {
    public record Evidence(String label, String url, LocalDate verifiedAt) { }
}

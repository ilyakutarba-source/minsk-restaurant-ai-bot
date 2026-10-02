package by.ilya.restaurantbot.catalog;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public record RestaurantDetails(Long id, String name, String address, boolean active,
        Set<Cuisine> cuisines, Set<RestaurantTag> tags,
        String catalogSource, LocalDate catalogVerifiedAt,
        BigDecimal estimatedCheckPerGuest, CheckEstimationType checkEstimationType,
        String checkSource, LocalDate checkVerifiedAt,
        String hoursSource, LocalDate hoursVerifiedAt, List<Hours> openingIntervals) {

    static RestaurantDetails from(Restaurant restaurant) {
        var intervals = restaurant.getOpeningIntervals().stream()
                .sorted(Comparator.comparing(OpeningInterval::getWeekday).thenComparing(OpeningInterval::getOpensAt))
                .map(i -> new Hours(i.getWeekday(), i.getOpensAt(), i.getClosesAt(), i.isClosesNextDay()))
                .toList();
        return new RestaurantDetails(restaurant.getId(), restaurant.getName(), restaurant.getAddress(),
                restaurant.isActive(), restaurant.getCuisines(), restaurant.getTags(),
                restaurant.getCatalogSource(), restaurant.getCatalogVerifiedAt(),
                restaurant.getEstimatedCheckPerGuest(), restaurant.getCheckEstimationType(),
                restaurant.getCheckSource(), restaurant.getCheckVerifiedAt(),
                restaurant.getHoursSource(), restaurant.getHoursVerifiedAt(), intervals);
    }

    public record Hours(DayOfWeek weekday, LocalTime opensAt, LocalTime closesAt, boolean closesNextDay) {
    }
}

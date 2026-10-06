package by.ilya.restaurantbot.ai;

import by.ilya.restaurantbot.catalog.MenuDetails;
import by.ilya.restaurantbot.catalog.RestaurantDetails;

/** Trusted own DTOs kept in Java, including a typed marker for unresolved follow-ups. */
public record RestaurantFollowUpResult(Kind kind, Focus focus, RestaurantDetails details, MenuDetails menu) {
    public enum Kind { MENU, DETAILS }
    public enum Focus { ALL, HOURS, CONTACTS, RATING }
}

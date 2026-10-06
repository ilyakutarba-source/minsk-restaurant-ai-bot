package by.ilya.restaurantbot.conversation;

/** Foundation for future menu/details inputs; never accepts an arbitrary restaurantId. */
public record RestaurantReference(Integer ordinal, Boolean last, String name) {
    public boolean isValid() {
        int selectors = (ordinal == null ? 0 : 1) + (last == null ? 0 : 1) + (name == null ? 0 : 1);
        return selectors == 1
                && (ordinal == null || ordinal >= 1 && ordinal <= 3)
                && (last == null || last)
                && (name == null || !name.isBlank() && name.length() <= 200);
    }
}

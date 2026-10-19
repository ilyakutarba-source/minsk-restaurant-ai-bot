package by.ilya.restaurantbot.search;

/** A valid visit date/time that has already passed in Minsk. */
public final class PastVisitTimeException extends IllegalArgumentException {
    public static final String MESSAGE = "Указанное время на сегодня уже прошло. "
            + "Укажите другое время и дату — например, завтра в 21:00.";

    public PastVisitTimeException() {
        super(MESSAGE);
    }
}

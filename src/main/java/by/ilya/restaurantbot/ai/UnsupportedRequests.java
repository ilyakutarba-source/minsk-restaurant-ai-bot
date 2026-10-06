package by.ilya.restaurantbot.ai;

import java.util.Locale;
import java.util.regex.Pattern;

/** Conservative scope guards, not a replacement for model language interpretation. */
final class UnsupportedRequests {
    private static final String BOOKING_REPLY = "Бронирование не поддерживается. Могу подобрать ресторан и показать сохранённые меню и сведения.";
    private static final String CURRENCY_REPLY = "Поддерживается только BYN. Укажите общий бюджет в белорусских рублях; конвертация валют не выполняется.";
    private static final String CITY_REPLY = "Сейчас я работаю только с собственным каталогом Минска. Укажите посещение в Минске.";
    private static final Pattern BOOKING = Pattern.compile("(?iuU)\\b(?:заброниру\\p{L}*|брониру\\p{L}*|бронь|бронирован\\p{L}*|booking|reserve|reservation)\\b");
    private static final Pattern CURRENCY = Pattern.compile("(?iuU)(?:\\b(?:usd|eur|pln|rub|gbp|uah|kzt|доллар\\p{L}*|евро|злот\\p{L}*|фунт\\p{L}*|российск\\p{L}*\\s+руб\\p{L}*)\\b|[$€£₽])");
    private static final Pattern CITY = Pattern.compile("(?iuU)\\b(?:в|во|город(?:е)?)\\s+(?:москв\\p{L}*|варшав\\p{L}*|гродно|брест\\p{L}*|гомел\\p{L}*|витебск\\p{L}*|могил[её]в\\p{L}*|киев\\p{L}*|петербург\\p{L}*|вильнюс\\p{L}*)\\b");
    private static final Pattern EXPLICIT_CITY = Pattern.compile("(?iuU)\\b(?:в\\s+городе|город)\\s+([\\p{L}-]+)");

    private UnsupportedRequests() { }

    static String reason(String text) {
        if (BOOKING.matcher(text).find()) return BOOKING_REPLY;
        if (CURRENCY.matcher(text).find()) return CURRENCY_REPLY;
        var explicit = EXPLICIT_CITY.matcher(text);
        if (CITY.matcher(text).find() || (explicit.find() && !explicit.group(1).toLowerCase(Locale.ROOT).matches("минск(?:е)?"))) {
            return CITY_REPLY;
        }
        return null;
    }

    static String modelReason(String text) {
        if (text == null || text.length() > 2000) return null;
        return switch (text.strip()) {
            case "UNSUPPORTED_BOOKING" -> BOOKING_REPLY;
            case "UNSUPPORTED_CITY" -> CITY_REPLY;
            case "UNSUPPORTED_CURRENCY" -> CURRENCY_REPLY;
            default -> null; // Provider prose is never a user-visible explanation or source of facts.
        };
    }
}

package by.ilya.restaurantbot.ai;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;

import static by.ilya.restaurantbot.ai.AiSearchReply.Status;
import static by.ilya.restaurantbot.ai.RestaurantFollowUpResult.*;

/** Plain text from freshly read own DTOs; no provider prose or generated facts. */
final class RestaurantFollowUpRenderer {
    String render(Status status, RestaurantFollowUpResult result) {
        if (result.menu() != null) return menu(result);
        if (result.details() != null) return details(result);
        return switch (status) {
            case NEED_CLARIFICATION -> "Уточните ресторан: укажите существующую позицию текущей подборки (1–3) или точное название. При нескольких филиалах выберите позицию.";
            case NOT_FOUND -> "Ресторан не найден в текущей подборке или собственном каталоге. Уточните точное название.";
            case INVALID_INPUT -> "Укажите один ресторан: позицию 1–3, последний или точное название, и поддерживаемые параметры запроса.";
            default -> "Сведения временно недоступны. Попробуйте позже.";
        };
    }

    private String menu(RestaurantFollowUpResult result) {
        var data = result.menu();
        var text = new StringBuilder("Сохранённое меню ресторана (ID: ").append(data.restaurantId()).append(")\n")
                .append(data.notice()).append('\n');
        if (data.coverage() != null) text.append("Покрытие: ").append(data.coverage()).append('\n');
        metadata(text, "Источник меню", data.source(), data.verifiedAt());
        for (var item : data.items()) {
            text.append("• ").append(item.name()).append(" — ").append(money(item.priceByn())).append(" BYN");
            if (item.portion() != null) text.append("; ").append(item.portion());
            text.append('\n');
            if (item.source() != null && !item.source().equals(data.source())) text.append("Источник позиции: ").append(item.source()).append('\n');
        }
        if (data.status() != by.ilya.restaurantbot.catalog.MenuDetails.Status.AVAILABLE) {
            text.append("Сохранена только часть меню; наличие блюд и актуальность цен не гарантируются.\n");
        }
        return text.toString().stripTrailing();
    }

    private String details(RestaurantFollowUpResult result) {
        var data = result.details();
        var text = new StringBuilder(data.name()).append(" (ID: ").append(data.id()).append(")\n")
                .append("Адрес: ").append(data.address()).append('\n');
        metadata(text, "Источник каталога", data.catalogSource(), data.catalogVerifiedAt());
        if (result.focus() == Focus.ALL) {
            text.append("Кухни: ").append(String.join(", ", data.cuisines().stream().sorted().map(Enum::name).toList())).append('\n');
            if (data.estimatedCheckPerGuest() != null) {
                text.append("Ориентировочный чек на гостя: ").append(money(data.estimatedCheckPerGuest())).append(" BYN; ")
                        .append(data.checkEstimationType()).append('\n');
                metadata(text, "Источник чека", data.checkSource(), data.checkVerifiedAt());
            } else text.append("Сохранённый ориентировочный чек недоступен.\n");
        }
        if (result.focus() == Focus.ALL || result.focus() == Focus.HOURS) {
            text.append("Собственное недельное расписание:\n");
            if (data.openingIntervals().isEmpty()) text.append("Сохранённые часы недоступны.\n");
            for (var interval : data.openingIntervals()) {
                text.append(day(interval.weekday())).append(": ")
                        .append(interval.opensAt().format(DateTimeFormatter.ofPattern("HH:mm"))).append("–")
                        .append(interval.closesAt().format(DateTimeFormatter.ofPattern("HH:mm")));
                if (interval.closesNextDay()) text.append(" следующего дня");
                text.append('\n');
            }
            metadata(text, "Источник часов", data.hoursSource(), data.hoursVerifiedAt());
            text.append("Недельное расписание не гарантирует праздничных исключений.\n");
        }
        if (result.focus() == Focus.ALL || result.focus() == Focus.CONTACTS)
            text.append("Телефон и сайт не сохранены в собственном каталоге.\n");
        if (result.focus() == Focus.RATING) text.append("Рейтинг недоступен: Google enrichment не подключён.\n");
        return text.toString().stripTrailing();
    }

    private static void metadata(StringBuilder text, String label, String source, java.time.LocalDate date) {
        if (source != null) text.append(label).append(": ").append(source).append('\n');
        if (date != null) text.append("Дата проверки: ").append(date).append('\n');
    }
    private static String money(BigDecimal value) { return value.setScale(2).toPlainString(); }
    private static String day(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Пн"; case TUESDAY -> "Вт"; case WEDNESDAY -> "Ср";
            case THURSDAY -> "Чт"; case FRIDAY -> "Пт"; case SATURDAY -> "Сб"; case SUNDAY -> "Вс";
        };
    }
}

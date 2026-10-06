package by.ilya.restaurantbot.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import by.ilya.restaurantbot.catalog.CheckEstimationType;
import by.ilya.restaurantbot.catalog.MenuCategory;
import by.ilya.restaurantbot.catalog.MenuCoverage;
import by.ilya.restaurantbot.catalog.MenuDetails;
import by.ilya.restaurantbot.catalog.RestaurantDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static by.ilya.restaurantbot.ai.AiSearchReply.Status.*;
import static by.ilya.restaurantbot.ai.RestaurantFollowUpResult.*;
import static org.assertj.core.api.Assertions.assertThat;

class RestaurantFollowUpRendererTest {
    private final RestaurantFollowUpRenderer renderer = new RestaurantFollowUpRenderer();

    @Test void compactMenuKeepsEveryItemPricePortionAndOneSecondarySourceBlock() {
        var item = new MenuDetails.Item(1, "Основное блюдо", MenuCategory.MAIN, null, new BigDecimal("22.90"),
                "350 г", null, "https://example.test/item");
        var second = new MenuDetails.Item(2, "Суп", MenuCategory.SOUP, null, new BigDecimal("16.90"),
                null, null, "https://example.test/second-item");
        var data = new MenuDetails(42, MenuDetails.Status.AVAILABLE, MenuCoverage.PARTIAL,
                "https://example.test/menu", LocalDate.of(2026, 10, 2), "BYN",
                "Сохранена только часть меню. Наличие блюд и актуальность цен не гарантируются.", List.of(item, second));
        var text = renderer.render(OK, new RestaurantFollowUpResult(Kind.MENU, null, null, data));

        assertThat(text).contains("🍽 Сохранённая часть меню (PARTIAL)", data.notice(),
                "• Основное блюдо — 22.90 BYN · 350 г\n• Суп — 16.90 BYN",
                "Источник меню: https://example.test/menu\nДата проверки: 2026-10-02")
                .doesNotContain("ID:", "Источник позиции", item.source(), second.source(), "null");
        assertThat(text.indexOf("Источник меню")).isGreaterThan(text.indexOf("• Суп"));
        assertThat(text.lines().filter(line -> line.startsWith("Источник меню"))).hasSize(1);
        assertThat(data.items()).containsExactly(item, second);
        assertThat(data.items().getFirst().source()).isEqualTo("https://example.test/item");
    }

    @ParameterizedTest
    @EnumSource(value = MenuDetails.Status.class, names = {"NO_RESULTS", "DATA_UNAVAILABLE"})
    void emptyMenuKeepsPartialMeaningAndAvailableEvidence(MenuDetails.Status status) {
        var notice = status == MenuDetails.Status.NO_RESULTS
                ? "Не найдено в сохранённой части меню." : "Сохранённое меню недоступно.";
        var data = new MenuDetails(42, status, MenuCoverage.PARTIAL, "https://example.test/menu",
                LocalDate.of(2026, 10, 2), "BYN", notice, List.of());
        assertThat(renderer.render(status == MenuDetails.Status.NO_RESULTS ? NO_RESULTS : DATA_UNAVAILABLE,
                new RestaurantFollowUpResult(Kind.MENU, null, null, data)))
                .contains(notice, "PARTIAL", "наличие блюд и актуальность цен не гарантируются",
                        "https://example.test/menu", "2026-10-02")
                .doesNotContain("Такого блюда нет", "ID:");
    }

    @Test void missingMenuMetadataDoesNotInventVerificationOrSource() {
        var data = new MenuDetails(42, MenuDetails.Status.DATA_UNAVAILABLE, null, null, null, "BYN",
                "Сохранённое меню недоступно.", List.of());
        assertThat(renderer.render(DATA_UNAVAILABLE, new RestaurantFollowUpResult(Kind.MENU, null, null, data)))
                .contains(data.notice()).doesNotContain("null", "Источник меню", "Дата проверки", "https://");
    }

    @ParameterizedTest
    @EnumSource(CheckEstimationType.class)
    void detailsStillExposeFullCatalogCheckAndHoursEvidence(CheckEstimationType type) {
        var original = AiFixtures.result().candidates().getFirst().restaurant();
        var details = new RestaurantDetails(original.id(), original.name(), original.address(), original.active(),
                original.cuisines(), original.tags(), original.catalogSource(), original.catalogVerifiedAt(),
                original.estimatedCheckPerGuest(), type, "https://example.test/check; основное блюдо + суп",
                original.checkVerifiedAt(), original.hoursSource(), original.hoursVerifiedAt(), original.openingIntervals());
        assertThat(renderer.render(OK, new RestaurantFollowUpResult(Kind.DETAILS, Focus.ALL, details, null)))
                .contains("ID: 42", original.name(), original.address(), original.catalogSource(),
                        "32.70 BYN; " + type, details.checkSource(), original.hoursSource(),
                        "Дата проверки: 2026-10-02", "Пт: 10:00–23:00", "праздничных исключений");
    }
}

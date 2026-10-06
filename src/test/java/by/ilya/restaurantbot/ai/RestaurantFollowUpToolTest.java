package by.ilya.restaurantbot.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import by.ilya.restaurantbot.catalog.*;
import by.ilya.restaurantbot.conversation.ReferenceResolver;
import by.ilya.restaurantbot.conversation.RestaurantReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static by.ilya.restaurantbot.ai.AiSearchReply.Status.*;
import static by.ilya.restaurantbot.ai.RestaurantFollowUpResult.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RestaurantFollowUpToolTest {
    ReferenceResolver resolver;
    MenuService menus;
    RestaurantService catalog;

    @BeforeEach void setup() {
        resolver = mock(ReferenceResolver.class);
        menus = mock(MenuService.class);
        catalog = mock(RestaurantService.class);
        when(resolver.resolve(eq(81L), any())).thenReturn(new ReferenceResolver.Resolution(ReferenceResolver.Status.OK, 42L));
    }

    RestaurantFollowUpTool tool(Kind kind) { return new RestaurantFollowUpTool(kind, 81L, resolver, menus, catalog); }

    @Test void menuUsesOnlyResolvedIdAndExistingFiltersAndKeepsDtoInJava() {
        var menu = new MenuDetails(42, MenuDetails.Status.AVAILABLE, MenuCoverage.PARTIAL,
                "https://example.test/menu", LocalDate.of(2026, 10, 2), "BYN", "Часть меню", List.of());
        when(menus.getMenuByRestaurantId(42, DishType.PASTA, new BigDecimal("20.00"))).thenReturn(Optional.of(menu));
        var tool = tool(Kind.MENU);
        assertThat(tool.call("{\"reference\":{\"ordinal\":2},\"dishType\":\"PASTA\",\"maxItemPriceByn\":20.00}"))
                .isEqualTo("{\"status\":\"OK\"}").doesNotContain("example.test", "restaurantId");
        verify(resolver).resolve(81, new RestaurantReference(2, null, null));
        verify(menus).getMenuByRestaurantId(42, DishType.PASTA, new BigDecimal("20.00"));
        verifyNoInteractions(catalog);
        assertThat(tool.result().menu()).isSameAs(menu);
        assertThat(tool.result().details()).isNull();
        assertThat(tool.executions()).isEqualTo(1);
        assertThat(tool.call("{\"reference\":{\"ordinal\":1}}")) .contains("INVALID_INPUT");
        verifyNoMoreInteractions(resolver, menus);
        assertThat(tool.result().menu()).isSameAs(menu);
    }

    @Test void detailsRereadsOwnServiceAndNeverReadsMenu() {
        var details = AiFixtures.result().candidates().getFirst().restaurant();
        when(catalog.getRestaurant(42)).thenReturn(Optional.of(details));
        var tool = tool(Kind.DETAILS);
        tool.call("{\"reference\":{\"name\":\"Synthetic fixture restaurant\"},\"focus\":\"HOURS\"}");
        verify(resolver).resolve(81, new RestaurantReference(null, null, "Synthetic fixture restaurant"));
        verify(catalog).getRestaurant(42);
        verifyNoInteractions(menus);
        assertThat(tool.result().details()).isSameAs(details);
        assertThat(new RestaurantFollowUpRenderer().render(tool.status(), tool.result()))
                .contains("Пт: 10:00–23:00", "https://example.test/hours", "2026-10-02")
                .doesNotContain("Ориентировочный чек", "ignored model prose");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{}", "{} {}", "broken",
            "{\"reference\":null}", "{\"reference\":{}}", "{\"reference\":{\"ordinal\":0}}",
            "{\"reference\":{\"ordinal\":4}}", "{\"reference\":{\"ordinal\":1.5}}",
            "{\"reference\":{\"ordinal\":\"2\"}}", "{\"reference\":{\"last\":false}}",
            "{\"reference\":{\"last\":\"true\"}}", "{\"reference\":{\"name\":\" \"}}",
            "{\"reference\":{\"name\":123}}", "{\"reference\":{\"ordinal\":1,\"name\":\"Pizza Tempo\"}}",
            "{\"reference\":{\"restaurantId\":2}}", "{\"restaurantId\":2,\"reference\":{\"ordinal\":1}}",
            "{\"reference\":{\"ordinal\":1,\"ordinal\":2}}"})
    void invalidReferencesAreRejectedBeforeAnyRead(String args) {
        for (Kind kind : Kind.values()) {
            var tool = tool(kind);
            tool.call(args);
            assertThat(tool.status()).isEqualTo(INVALID_INPUT);
            assertThat(tool.executions()).isZero();
        }
        verifyNoInteractions(resolver, menus, catalog);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"dishType\":\"PIZZA\"", "\"maxItemPriceByn\":0", "\"maxItemPriceByn\":-1",
            "\"maxItemPriceByn\":1.001", "\"maxItemPriceByn\":1e1000000000", "\"maxItemPriceByn\":100000000",
            "\"maxItemPriceByn\":\"20\"", "\"maxItemPriceByn\":10.000", "\"focus\":\"ALL\""})
    void invalidMenuFiltersAreRejectedBeforeReferenceResolution(String filter) {
        var tool = tool(Kind.MENU);
        tool.call("{\"reference\":{\"ordinal\":1}," + filter + "}");
        assertThat(tool.status()).isEqualTo(INVALID_INPUT);
        assertThat(tool.executions()).isZero();
        verifyNoInteractions(resolver, menus, catalog);
    }

    @Test void oversizedInputsAndUnsupportedFocusCannotReadServices() {
        for (Kind kind : Kind.values()) {
            for (String args : List.of("x".repeat(2001), "{\"reference\":{\"name\":\"" + "n".repeat(201) + "\"}}")) {
                var tool = tool(kind);
                tool.call(args);
                assertThat(tool.status()).isEqualTo(INVALID_INPUT);
            }
        }
        var details = tool(Kind.DETAILS);
        details.call("{\"reference\":{\"last\":true},\"focus\":\"GOOGLE\"}");
        assertThat(details.status()).isEqualTo(INVALID_INPUT);
        verifyNoInteractions(resolver, menus, catalog);
    }

    @Test void unresolvedReferencesStayControlledAndDoNotReadFacts() {
        for (var status : List.of(ReferenceResolver.Status.NEED_CLARIFICATION, ReferenceResolver.Status.NOT_FOUND,
                ReferenceResolver.Status.INVALID_INPUT)) {
            when(resolver.resolve(eq(81L), any())).thenReturn(new ReferenceResolver.Resolution(status, null));
            for (Kind kind : Kind.values()) {
                var tool = tool(kind);
                tool.call("{\"reference\":{\"ordinal\":1}}");
                assertThat(tool.status().name()).isEqualTo(status.name());
                assertThat(tool.executions()).isEqualTo(1);
                assertThat(new RestaurantFollowUpRenderer().render(tool.status(), tool.result())).isNotBlank();
            }
        }
        verifyNoInteractions(menus, catalog);
    }

    @Test void missingRestaurantAndServiceFailureConsumeOneAttemptWithoutRetry() {
        when(catalog.getRestaurant(42)).thenReturn(Optional.empty());
        var details = tool(Kind.DETAILS);
        details.call("{\"reference\":{\"last\":true}}");
        assertThat(details.status()).isEqualTo(NOT_FOUND);
        when(menus.getMenuByRestaurantId(42, null, null)).thenReturn(Optional.empty());
        var menu = tool(Kind.MENU);
        menu.call("{\"reference\":{\"last\":true}}");
        assertThat(menu.status()).isEqualTo(NOT_FOUND);
        when(menus.getMenuByRestaurantId(42, null, null)).thenThrow(new IllegalStateException("private provider payload"));
        var failed = tool(Kind.MENU);
        failed.call("{\"reference\":{\"last\":true}}");
        assertThat(failed.status()).isEqualTo(TEMPORARILY_UNAVAILABLE);
        assertThat(failed.executions()).isEqualTo(1);
        assertThat(new RestaurantFollowUpRenderer().render(failed.status(), failed.result())).doesNotContain("private provider payload");
        failed.call("{}");
        verify(menus, times(2)).getMenuByRestaurantId(42, null, null);
    }

    @Test void referenceReadFailureAlsoConsumesAttempt() {
        when(resolver.resolve(eq(81L), any())).thenThrow(new IllegalStateException("secret"));
        var tool = tool(Kind.DETAILS);
        tool.call("{\"reference\":{\"ordinal\":1}}");
        assertThat(tool.status()).isEqualTo(TEMPORARILY_UNAVAILABLE);
        assertThat(tool.executions()).isEqualTo(1);
        verifyNoInteractions(catalog, menus);
    }
}

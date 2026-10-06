package by.ilya.restaurantbot.ai;

import java.util.List;

import by.ilya.restaurantbot.catalog.Cuisine;
import by.ilya.restaurantbot.search.SearchCriteria;
import by.ilya.restaurantbot.search.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static by.ilya.restaurantbot.ai.AiFixtures.result;
import static org.assertj.core.api.Assertions.assertThat;

class SearchFactualRendererTest {
    private final SearchFactualRenderer renderer = new SearchFactualRenderer();

    @Test void compactCardKeepsVisitFactsAndEvidenceInTrustedDto() {
        var search = result();
        var restaurant = search.candidates().getFirst().restaurant();
        var text = renderer.render(search, null);

        assertThat(text).contains("Подборка на 2026-10-02 в 21:00 · гостей: 2",
                "1. 🍽 Synthetic fixture restaurant", "📍 Synthetic fixture address",
                "💰 ≈65.40 BYN на 2 гостей", "🟢 Открыто по расписанию к 21:00",
                "Почему подходит:\n• итальянская кухня\n• входит в бюджет по ориентировочному чеку",
                "Ориентировочный чек, не гарантия итоговой суммы.", "Подробности первого", "Меню первого")
                .doesNotContain("ID:", "42", "PUBLISHED", "DERIVED", "https://", "Источник",
                        "проверено", "Расписание:", "FRIDAY", "32.70", "23:00");
        assertThat(restaurant.id()).isEqualTo(42);
        assertThat(restaurant.checkSource()).isEqualTo("https://example.test/check");
        assertThat(restaurant.hoursSource()).isEqualTo("https://example.test/hours");
        assertThat(restaurant.catalogVerifiedAt()).isEqualTo("2026-10-02");
        assertThat(restaurant.checkVerifiedAt()).isEqualTo("2026-10-02");
        assertThat(restaurant.hoursVerifiedAt()).isEqualTo("2026-10-02");
        assertThat(restaurant.openingIntervals()).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"BELARUSIAN,белорусская кухня", "ITALIAN,итальянская кухня", "GEORGIAN,грузинская кухня"})
    void cuisineReasonUsesRequestedValidatedCuisine(Cuisine cuisine, String label) {
        var original = result();
        var c = original.normalizedCriteria();
        var search = new SearchResult(new SearchCriteria(c.guests(), c.totalBudgetByn(), c.date(), c.time(),
                cuisine, c.preferredTags()), original.currency(), original.candidates(), original.warnings());
        assertThat(renderer.render(search, null)).contains("• " + label);
    }

    @Test void invalidExplanationUsesSameCompactFallbackWithoutAddingUnsupportedReasons() {
        var search = result();
        var invalid = new ExplanationPlan(List.of(new ExplanationPlan.Item(1,
                List.of(SearchResult.ReasonCode.ROMANTIC_TAG_MATCH), ExplanationPlan.Phrasing.WARM)));
        assertThat(renderer.render(search, invalid)).isEqualTo(renderer.render(search, null))
                .doesNotContain("романтично");
    }

    @Test void noResultsDoesNotInventCardOrOfferOrdinalForEmptySelection() {
        var original = result();
        var empty = new SearchResult(original.normalizedCriteria(), original.currency(), List.of(), original.warnings());
        assertThat(renderer.render(empty, null)).contains("нет ресторанов, подходящих под все заданные условия")
                .doesNotContain("🍽", "Открыто", "Меню первого", "Synthetic");
    }
}

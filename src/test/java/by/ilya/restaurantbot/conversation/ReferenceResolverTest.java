package by.ilya.restaurantbot.conversation;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import by.ilya.restaurantbot.catalog.RestaurantService;
import by.ilya.restaurantbot.catalog.RestaurantService.RestaurantName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static by.ilya.restaurantbot.conversation.ReferenceResolver.Status.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReferenceResolverTest {
    SelectionService selections;
    RestaurantService catalog;
    ReferenceResolver resolver;

    @BeforeEach
    void setup() {
        selections = mock(SelectionService.class);
        catalog = mock(RestaurantService.class);
        resolver = new ReferenceResolver(selections, catalog);
        when(selections.current(1)).thenReturn(List.of());
        when(catalog.getRestaurantNames()).thenReturn(List.of(new RestaurantName(3, "Хинкальня"),
                new RestaurantName(1, "Васильки"), new RestaurantName(2, "Pizza Tempo")));
    }

    @ParameterizedTest
    @CsvSource({"1,1,3", "2,1,3", "2,2,1", "3,1,3", "3,2,1", "3,3,2"})
    void ordinalsUseShownOrderForOneTwoAndThreeCandidates(int count, int ordinal, long id) {
        select(count);
        assertThat(resolver.resolve(1, new RestaurantReference(ordinal, null, null)))
                .isEqualTo(new ReferenceResolver.Resolution(OK, id));
        verifyNoInteractions(catalog);
    }

    @ParameterizedTest
    @CsvSource({"1,3", "2,1", "3,2"})
    void lastUsesHighestShownPosition(int count, long id) {
        select(count);
        assertThat(resolver.resolve(1, new RestaurantReference(null, true, null)).restaurantId()).isEqualTo(id);
    }

    @ParameterizedTest
    @CsvSource({"0,1", "1,2", "2,3"})
    void absentPositionClarifiesWithoutCatalogFallback(int count, int ordinal) {
        select(count);
        assertThat(resolver.resolve(1, new RestaurantReference(ordinal, null, null)).status()).isEqualTo(NEED_CLARIFICATION);
        assertThat(resolver.resolve(1, new RestaurantReference(ordinal, null, null)).restaurantId()).isNull();
        verifyNoInteractions(catalog);
    }

    @Test
    void lastWithoutSelectionClarifiesAndAnotherChatsSelectionIsInvisible() {
        select(3);
        assertThat(resolver.resolve(2, new RestaurantReference(1, null, null)).status()).isEqualTo(NEED_CLARIFICATION);
        assertThat(resolver.resolve(2, new RestaurantReference(null, true, null)).status()).isEqualTo(NEED_CLARIFICATION);
        verifyNoInteractions(catalog);
    }

    static Stream<RestaurantReference> invalidReferences() {
        return Stream.of(null, new RestaurantReference(null, null, null), new RestaurantReference(0, null, null),
                new RestaurantReference(4, null, null), new RestaurantReference(null, false, null),
                new RestaurantReference(1, true, null), new RestaurantReference(1, null, "Васильки"),
                new RestaurantReference(null, true, "Васильки"), new RestaurantReference(1, true, "Васильки"),
                new RestaurantReference(null, null, " "), new RestaurantReference(null, null, "a".repeat(201)));
    }

    @ParameterizedTest
    @MethodSource("invalidReferences")
    void invalidSelectorContractReturnsNoIdWithoutDataAccess(RestaurantReference reference) {
        clearInvocations(selections, catalog);
        assertThat(resolver.resolve(1, reference)).isEqualTo(new ReferenceResolver.Resolution(INVALID_INPUT, null));
        verifyNoInteractions(selections, catalog);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Pizza Tempo", "  pIZZA   tEMPO  ", "\tPIZZA\nTEMPO\u2003"})
    void exactNormalizedNameResolvesOnlyInCurrentSelection(String name) {
        select(3);
        assertThat(resolver.resolve(1, new RestaurantReference(null, null, name)))
                .isEqualTo(new ReferenceResolver.Resolution(OK, 2L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Pizza", "Pizza Temp", "Unknown", "самый дешёвый"})
    void unknownOrFuzzyNameNeverResolves(String name) {
        select(3);
        assertThat(resolver.resolve(1, new RestaurantReference(null, null, name)))
                .isEqualTo(new ReferenceResolver.Resolution(NOT_FOUND, null));
    }

    @Test
    void nameCannotEscapeCurrentSelectionButCanUseOwnCatalogWithoutSelection() {
        select(1);
        var name = new RestaurantReference(null, null, "Pizza Tempo");
        assertThat(resolver.resolve(1, name).status()).isEqualTo(NOT_FOUND);
        when(selections.current(1)).thenReturn(List.of());
        assertThat(resolver.resolve(1, name).restaurantId()).isEqualTo(2L);
    }

    @Test
    void duplicateNormalizedBranchNamesClarifyBothInSelectionAndInCatalog() {
        when(catalog.getRestaurantNames()).thenReturn(List.of(
                new RestaurantName(3, "Васильки"), new RestaurantName(1, " ВАСИЛЬКИ ")));
        var name = new RestaurantReference(null, null, "васильки");
        assertThat(resolver.resolve(1, name)).isEqualTo(new ReferenceResolver.Resolution(NEED_CLARIFICATION, null));
        select(2);
        assertThat(resolver.resolve(1, name).status()).isEqualTo(NEED_CLARIFICATION);
        select(1);
        assertThat(resolver.resolve(1, name).restaurantId()).isEqualTo(3L);
    }

    private void select(int count) {
        long[] ids = {3, 1, 2};
        when(selections.current(1)).thenReturn(IntStream.range(0, count)
                .mapToObj(i -> new SelectionItem(1, i + 1, 7, ids[i])).toList());
    }
}

package by.ilya.restaurantbot.search;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import by.ilya.restaurantbot.catalog.RestaurantRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VisitTimeTest {
    private final RestaurantRepository repository = mock(RestaurantRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T20:45:59Z"), ZoneOffset.UTC);

    @ParameterizedTest
    @ValueSource(strings = {"TODAY", "2026-10-19"})
    void pastTimeIsRejectedBeforeReadingCatalog(String date) {
        var service = new RestaurantSearchService(repository, clock);
        assertThatThrownBy(() -> service.search(request(date, "21:00")))
                .isInstanceOf(PastVisitTimeException.class).hasMessage(PastVisitTimeException.MESSAGE);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @CsvSource({"TODAY,23:45", "TODAY,23:46", "TOMORROW,00:00", "TOMORROW,21:00"})
    void currentMinuteAndFutureTimesReachSearchAndCanHaveNoResults(String date, String time) {
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of());
        var result = new RestaurantSearchService(repository, clock).search(request(date, time));
        assertThat(result.candidates()).isEmpty();
        assertThat(result.normalizedCriteria().time().toString()).isEqualTo(time);
        verify(repository).findAllByOrderByIdAsc();
    }

    @ParameterizedTest
    @CsvSource({"2026-10-19T21:01:00Z,TODAY,00:00,true",
            "2026-10-19T21:01:00Z,TODAY,00:01,false",
            "2026-10-19T21:01:00Z,TOMORROW,00:00,false"})
    void midnightUsesMinskRatherThanClockZone(String instant, String date, String time, boolean past) {
        var service = new RestaurantSearchService(repository, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
        if (past) {
            assertThatThrownBy(() -> service.search(request(date, time))).isInstanceOf(PastVisitTimeException.class);
            verifyNoInteractions(repository);
        } else {
            when(repository.findAllByOrderByIdAsc()).thenReturn(List.of());
            assertThat(service.search(request(date, time)).normalizedCriteria().date().toString())
                    .isEqualTo(date.equals("TODAY") ? "2026-10-20" : "2026-10-21");
        }
    }

    private SearchRequest request(String date, String time) {
        return new SearchRequest(2, new BigDecimal("150"), date, time, null, null);
    }
}

package by.ilya.restaurantbot.search;

import by.ilya.restaurantbot.api.RecommendationController;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationErrorTest {
    static RuntimeException[] unavailableDatabase() {
        return new RuntimeException[] {
                new DataAccessResourceFailureException("Synthetic connection failure"),
                new CannotCreateTransactionException("Synthetic transaction failure")
        };
    }

    @ParameterizedTest
    @MethodSource("unavailableDatabase")
    void databaseFailureIs503WithoutFallbackSearch(RuntimeException failure) throws Exception {
        var service = mock(RestaurantSearchService.class);
        when(service.search(any())).thenThrow(failure);
        var mvc = MockMvcBuilders.standaloneSetup(new RecommendationController(service)).build();
        mvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guests\":2,\"totalBudgetByn\":150,\"date\":\"TODAY\",\"time\":\"21:00\"}"))
                .andExpect(status().isServiceUnavailable());
        verify(service, times(1)).search(any());
    }
}

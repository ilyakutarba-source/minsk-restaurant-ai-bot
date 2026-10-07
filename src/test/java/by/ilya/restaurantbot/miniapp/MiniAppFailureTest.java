package by.ilya.restaurantbot.miniapp;

import by.ilya.restaurantbot.catalog.MenuService;
import by.ilya.restaurantbot.catalog.RestaurantService;
import by.ilya.restaurantbot.search.RestaurantSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MiniAppFailureTest {
    @Test
    void databaseAndUnexpectedFailuresExposeOnlyControlledMessages() throws Exception {
        var search = mock(RestaurantSearchService.class);
        var restaurants = mock(RestaurantService.class);
        var menus = mock(MenuService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new MiniAppController(search, restaurants, menus))
                .setControllerAdvice(new MiniAppErrors()).build();
        when(search.search(any())).thenThrow(new DataAccessResourceFailureException("SQL private details"));
        mvc.perform(post("/api/miniapp/v1/search").contentType("application/json")
                .content("{\"guests\":2,\"totalBudgetByn\":150,\"date\":\"TODAY\",\"time\":\"19:00\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TEMPORARY_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL"))));
        when(restaurants.getRestaurant(1)).thenThrow(new IllegalStateException("provider private details"));
        mvc.perform(get("/api/miniapp/v1/restaurants/1")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Сервис временно недоступен. Попробуйте позже."));
        verifyNoInteractions(menus);
    }
}

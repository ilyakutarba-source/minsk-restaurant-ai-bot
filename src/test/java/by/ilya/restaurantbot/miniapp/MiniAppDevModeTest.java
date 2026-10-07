package by.ilya.restaurantbot.miniapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "restaurant-bot.miniapp.dev-mode=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MiniAppDevModeTest {
    @Autowired MockMvc mvc;

    @Test
    void explicitDevModeAllowsOnlyLocalUnsignedRequests() throws Exception {
        mvc.perform(get("/api/miniapp/v1/restaurants/1")).andExpect(status().isOk());
        mvc.perform(get("/api/miniapp/v1/restaurants/1").with(request -> {
            request.setRemoteAddr("192.0.2.1"); return request;
        })).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/miniapp/v1/restaurants/1").header("X-Telegram-Init-Data", "tampered"))
                .andExpect(status().isUnauthorized());
    }
}

package by.ilya.restaurantbot.miniapp;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import by.ilya.restaurantbot.ai.*;
import by.ilya.restaurantbot.catalog.*;
import by.ilya.restaurantbot.conversation.*;
import by.ilya.restaurantbot.search.*;
import by.ilya.restaurantbot.telegram.TelegramUpdateHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.pengrad.telegrambot.utility.BotUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Same real search, offline model and transport, authenticated Mini App endpoints. No polling. */
@SpringBootTest(properties = "restaurant-bot.telegram.bot-token=" + InitDataFixture.TOKEN)
@AutoConfigureMockMvc @ActiveProfiles("test") @Import(MiniAppAiIntegrationTest.Config.class)
class MiniAppBrandDiversityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RestaurantSearchService search;
    @Autowired RestaurantRepository restaurants;
    @Autowired ChatModel model;
    @Autowired SpringAiSearchAdapter adapter;
    @Autowired ConversationStore store;
    @Autowired SelectionService selections;
    @Autowired ChatMemory memory;
    @Autowired Clock clock;

    @ParameterizedTest @CsvSource({"ITALIAN,1", "ASIAN,2", "ANY,3"})
    void telegramStructuredAndNaturalReturnTheSameOneTwoOrThreeBrands(String cuisineName, int count) throws Exception {
        reset(model);
        Cuisine cuisine = cuisineName.equals("ANY") ? null : Cuisine.valueOf(cuisineName);
        var request = new SearchRequest(2, new BigDecimal("150"), "TODAY", "21:00", cuisine, Set.of());
        var expected = search.search(request);
        var ids = expected.candidates().stream().map(c -> c.restaurant().id()).toList();
        assertThat(ids).hasSize(count);
        assertThat(restaurants.findAllById(ids).stream().map(Restaurant::getBrandKey)).doesNotHaveDuplicates();
        var tool = MiniAppAiIntegrationTest.tool("searchRestaurants", json.writeValueAsString(request));
        var explanation = MiniAppAiIntegrationTest.response("{}", "STOP", List.of());
        when(model.call(any(Prompt.class))).thenReturn(tool, explanation, tool, explanation);
        String auth = InitDataFixture.signed(InitDataFixture.NOW);
        String structured = mvc.perform(post("/api/miniapp/v1/search").header("X-Telegram-Init-Data", auth)
                .contentType("application/json").content(json.writeValueAsString(
                        new MiniAppSearchRequest(2, new BigDecimal("150"), "TODAY", "21:00", cuisine))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String natural = mvc.perform(post("/api/miniapp/v1/search/natural").header("X-Telegram-Init-Data", auth)
                .contentType("application/json").content(json.writeValueAsString(Map.of(
                        "query", "Сегодня 21:00 двое 150 BYN " + cuisineName))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(natural).get("cards")).isEqualTo(json.readTree(structured).get("cards"));
        for (int i = 0; i < count; i++) {
            var card = json.readTree(structured).get("cards").get(i);
            assertThat(card.get("id").asLong()).isEqualTo(ids.get(i));
            assertThat(card.get("estimatedTotalByn").decimalValue())
                    .isEqualByComparingTo(expected.candidates().get(i).estimatedTotalByn());
        }
        long chat = 9300000001L;
        store.reset(store.load(chat));
        try {
            var bot = mock(TelegramBot.class);
            var sent = mock(SendResponse.class);
            when(sent.isOk()).thenReturn(true);
            when(bot.execute(any(SendMessage.class))).thenReturn(sent);
            var conversation = new ConversationService(store, memory, adapter, clock, selections);
            new TelegramUpdateHandler(bot, conversation).process(List.of(BotUtils.parseUpdate("""
                    {"update_id":1,"message":{"message_id":1,"chat":{"id":9300000001,"type":"private"},
                    "text":"Сегодня 21:00 двое 150 BYN"}}
                    """)));
            assertThat(selections.current(chat).stream().map(SelectionItem::restaurantId).toList()).isEqualTo(ids);
            var capture = ArgumentCaptor.forClass(SendMessage.class);
            verify(bot).execute(capture.capture());
            String text = capture.getValue().getText();
            int previous = -1;
            for (int i = 0; i < count; i++) {
                var candidate = expected.candidates().get(i);
                int position = text.indexOf((i + 1) + ". 🍽 " + candidate.restaurant().name() + "\n📍 " + candidate.restaurant().address());
                assertThat(position).isGreaterThan(previous);
                previous = position;
                assertThat(text).contains(candidate.estimatedTotalByn().toPlainString() + " BYN");
            }
            assertThat(text).doesNotContain((count + 1) + ". 🍽");
            verify(model, times(4)).call(any(Prompt.class));
        } finally {
            store.reset(store.load(chat));
        }
    }
}

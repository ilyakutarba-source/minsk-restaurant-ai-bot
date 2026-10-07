package by.ilya.restaurantbot.miniapp;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import by.ilya.restaurantbot.ai.AiSearchReply;
import by.ilya.restaurantbot.ai.SpringAiSearchAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in only: -Dtest=MiniAppAiLiveSmokeIT; no Telegram poller, three bounded provider turns. */
@SpringBootTest(properties = {"restaurant-bot.ai.enabled=true", "restaurant-bot.telegram.bot-token="})
@AutoConfigureMockMvc @ActiveProfiles("test") @Import(MiniAppAiLiveSmokeIT.Auth.class)
class MiniAppAiLiveSmokeIT {
    @TestConfiguration
    static class Auth {
        @Bean @Primary TelegramMiniAppInitDataVerifier liveVerifier(java.time.Clock clock) {
            return new TelegramMiniAppInitDataVerifier(InitDataFixture.TOKEN, clock);
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoSpyBean SpringAiSearchAdapter adapter;

    @Test void threeSelfContainedQueriesThroughAuthenticatedMiniAppApi() throws Exception {
        var trusted = new AtomicReference<AiSearchReply>();
        doAnswer(invocation -> {
            var reply = (AiSearchReply) invocation.callRealMethod();
            trusted.set(reply);
            return reply;
        }).when(adapter).search(anyString(), anyList(), any(), anyList());
        var queries = List.of("Сегодня в 21:00 нас двое, до 150 BYN, итальянская кухня",
                "Завтра вечером нас трое, бюджет 180 рублей", "Хочется итальянскую кухню");
        for (int i = 0; i < queries.size(); i++) {
            var body = mvc.perform(post("/api/miniapp/v1/search/natural")
                    .header("X-Telegram-Init-Data", InitDataFixture.signed(Instant.now().getEpochSecond()))
                    .contentType("application/json;charset=UTF-8").content(json.writeValueAsString(Map.of("query", queries.get(i)))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var reply = trusted.get();
            assertThat(reply.modelCalls()).isBetween(1, 2);
            assertThat(reply.toolExecutions()).isBetween(0, 1);
            assertThat(body).doesNotContain("tool_calls", "choices", "PRIVATE", "searchResult", "modelCalls",
                    "toolExecutions", "ExplanationPlan", InitDataFixture.TOKEN);
            var cards = json.readTree(body).get("cards");
            assertThat(cards.size()).isLessThanOrEqualTo(3);
            if (i == 0) {
                assertThat(reply.status()).isEqualTo(AiSearchReply.Status.OK);
                assertThat(cards.size()).isEqualTo(reply.searchResult().candidates().size());
                for (int c = 0; c < cards.size(); c++) {
                    assertThat(cards.get(c)).isEqualTo(json.readTree(json.writeValueAsString(
                            MiniAppMapper.card(reply.searchResult().candidates().get(c), reply.searchResult()))));
                }
            } else {
                // "Вечером" deliberately lacks an exact time; the adapter must not guess it.
                assertThat(reply.status()).isEqualTo(AiSearchReply.Status.NEED_CLARIFICATION);
                assertThat(cards).isEmpty();
                assertThat(json.readTree(body).get("message").asText()).isNotBlank();
            }
            System.out.printf("MINIAPP_LIVE case=%d status=%s modelCalls=%d toolExecutions=%d cards=%d trustedMapping=PASS%n",
                    i + 1, reply.status(), reply.modelCalls(), reply.toolExecutions(), cards.size());
        }
    }
}

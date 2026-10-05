package by.ilya.restaurantbot.ai;

import java.time.Clock;
import java.util.List;

import by.ilya.restaurantbot.search.RestaurantSearchService;
import by.ilya.restaurantbot.search.SearchRequest;
import by.ilya.restaurantbot.telegram.TelegramUpdateHandler;
import com.pengrad.telegrambot.utility.BotUtils;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static by.ilya.restaurantbot.ai.AiFixtures.FULL;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Offline Telegram transport + production AI SDK + real seeded Java search/database. */
@SpringBootTest
@ActiveProfiles("test")
@Import(SpringAiSearchIntegrationTest.FixedClock.class)
class TelegramSearchIntegrationTest {
    @Autowired RestaurantSearchService service;
    @Autowired Clock clock;
    private TelegramBot bot;

    @BeforeEach
    void setup() {
        bot = mock(TelegramBot.class);
        var sent = mock(SendResponse.class);
        when(sent.isOk()).thenReturn(true);
        when(bot.execute(any(SendMessage.class))).thenReturn(sent);
    }

    private AiSearchReply deliver(SpringAiSearchAdapter adapter, String text) {
        var observed = new AiSearchReply[1];
        var transportAdapter = spy(adapter);
        doAnswer(call -> {
            observed[0] = (AiSearchReply) call.callRealMethod();
            return observed[0];
        }).when(transportAdapter).search(text);
        new TelegramUpdateHandler(bot, transportAdapter).process(List.of(BotUtils.parseUpdate("""
                {"update_id":1,"message":{"message_id":1,"chat":{"id":1,"type":"private"},"text":"%s"}}
                """.formatted(text))));
        verify(transportAdapter).search(text);
        return observed[0];
    }

    private String sentText() {
        var request = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(request.capture());
        assertThat(request.getValue().getParseMode()).isNull();
        return request.getValue().getText();
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid", "invalid", "failed"})
    void fullTelegramTurnPreservesTrustedFactsAndExplanationFallback(String explanation) throws Exception {
        var plan = """
                {"items":[{"position":1,"reasonCodes":["CUISINE_MATCH","BUDGET_MATCH"],"phrasing":"NEUTRAL"}]}
                """;
        var response = switch (explanation) {
            case "valid" -> ProviderFixture.explanation(plan);
            case "invalid" -> ProviderFixture.explanation("{\"items\":[]}");
            default -> new ProviderFixture.Reply(503, "{}");
        };
        try (var fixture = new ProviderFixture(ProviderFixture.selection(FULL), response);
             var http = new AiConfiguration().aiHttpClient()) {
            var adapter = new SpringAiSearchAdapter(AiConfiguration.createModel(http, "offline-fixture", fixture.origin()),
                    service, clock, true);
            String message = "Сегодня в 21:00 нас двое, общий бюджет 150 BYN, итальянская кухня.";
            var reply = deliver(adapter, message);
            var text = sentText();
            assertThat(text).contains("1. Pizza Tempo (ID: 2)", "Карла Маркса, 26",
                    "Ориентировочный чек на 2 гостей: 65.40 BYN", "21:00", "не гарантия итоговой суммы")
                    .doesNotContain("Васильки", "Хинкальня", "provider payload");
            assertThat(reply.modelCalls()).isEqualTo(2);
            assertThat(reply.toolExecutions()).isEqualTo(1);
            assertThat(reply.explanationFallback()).isEqualTo(!explanation.equals("valid"));
            assertThat(text).isEqualTo(reply.text());
            assertThat(fixture.requests()).hasSize(2);
            assertThat(fixture.requests().getLast().path("tools").size()).isZero();
            assertThat(fixture.requests().getLast().path("tool_choice").asText()).isEqualTo("none");
        }
    }

    @Test
    void threeOwnCandidatesKeepJavaOrderTotalsAndCount() throws Exception {
        String args = FULL.replace("\"ITALIAN\"", "null").replace("[\"QUIET\"]", "[]");
        var request = AiJson.mapper().readValue(args, SearchRequest.class);
        var expected = service.search(request);
        assertThat(expected.candidates()).hasSize(3);
        try (var fixture = new ProviderFixture(ProviderFixture.selection(args));
             var http = new AiConfiguration().aiHttpClient()) {
            var adapter = new SpringAiSearchAdapter(AiConfiguration.createModel(http, "offline-fixture", fixture.origin()),
                    service, clock, false);
            deliver(adapter, "Сегодня в 21:00 нас двое, общий бюджет 150 BYN, кухня любая.");
            var text = sentText();
            int previous = -1;
            for (int i = 0; i < expected.candidates().size(); i++) {
                var candidate = expected.candidates().get(i);
                var heading = (i + 1) + ". " + candidate.restaurant().name() + " (ID: " + candidate.restaurant().id() + ")";
                int position = text.indexOf(heading);
                assertThat(position).isGreaterThan(previous);
                previous = position;
                assertThat(text).contains(candidate.estimatedTotalByn().toPlainString() + " BYN");
            }
            assertThat(text).doesNotContain("4. ");
            // Existing three-record dataset must fit a single plain-text Telegram message.
            assertThat(text.length()).isLessThanOrEqualTo(4096);
            assertThat(fixture.requests()).hasSize(1);
        }
    }

    @Test
    void noResultsSendsHonestTextWithoutSecondCallOrRelaxedSearch() throws Exception {
        try (var fixture = new ProviderFixture(ProviderFixture.selection(FULL.replace("150", "1")));
             var http = new AiConfiguration().aiHttpClient()) {
            var adapter = new SpringAiSearchAdapter(AiConfiguration.createModel(http, "offline-fixture", fixture.origin()),
                    service, clock, true);
            deliver(adapter, "Сегодня в 21:00 двое, общий бюджет 1 BYN, итальянская кухня.");
            assertThat(sentText()).contains("нет ресторанов, подходящих под все заданные условия").doesNotContain("Pizza Tempo");
            assertThat(fixture.requests()).hasSize(1);
        }
    }

    @Test
    void nextMessageHasNoPreviousCriteriaMemoryOrSelection() throws Exception {
        try (var fixture = new ProviderFixture(ProviderFixture.selection(FULL),
                ProviderFixture.selection("{\"guests\":4}"));
             var http = new AiConfiguration().aiHttpClient()) {
            var adapter = new SpringAiSearchAdapter(AiConfiguration.createModel(http, "offline-fixture", fixture.origin()),
                    service, clock, false);
            deliver(adapter, "Сегодня в 21:00 двое, общий бюджет 150 BYN, итальянская кухня.");
            deliver(adapter, "А если нас четверо?");
            var requests = ArgumentCaptor.forClass(SendMessage.class);
            verify(bot, times(2)).execute(requests.capture());
            assertThat(requests.getAllValues().getLast().getText()).contains("Укажите один запрос").doesNotContain("Pizza Tempo");
            assertThat(fixture.requests()).hasSize(2);
            assertThat(fixture.requests().getLast().path("messages").toString()).contains("А если нас четверо?")
                    .doesNotContain("150 BYN", "Pizza Tempo", "итальянская кухня");
        }
    }
}

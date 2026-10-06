package by.ilya.restaurantbot.telegram;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import by.ilya.restaurantbot.ai.AiSearchReply;
import by.ilya.restaurantbot.conversation.ConversationService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Pengrad 10.1.0 and production transport, loopback only, no token or live Telegram. */
class TelegramLibraryTest {
    @Test
    void listenerAcknowledgesBatchWithNextOffsetAndNewPollerStartsWithoutLocalCheckpoint() throws Exception {
        var polls = new LinkedBlockingQueue<Map<String, String>>();
        var sends = new java.util.concurrent.atomic.AtomicInteger();
        var requests = new java.util.concurrent.atomic.AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var values = form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String body;
            if (exchange.getRequestURI().getPath().endsWith("getUpdates")) {
                polls.add(values);
                body = requests.getAndIncrement() == 0 ? """
                        {"ok":true,"result":[
                         {"update_id":41,"message":{"message_id":1,"chat":{"id":1,"type":"private"},"text":"/start"}},
                         {"update_id":42,"message":{"message_id":2,"chat":{"id":1,"type":"private"},"text":"DB failure"}}]}
                        """ : "{\"ok\":true,\"result\":[]}";
            } else {
                sends.incrementAndGet();
                // Delivery failure still completes handling; no delivery retry/checkpoint.
                body = "{\"ok\":false,\"error_code\":429,\"description\":\"fixture\",\"parameters\":{\"retry_after\":1}}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
        var conversation = mock(ConversationService.class);
        when(conversation.handle(eq(1L), eq("/start"), any())).thenAnswer(call -> {
            var reply = new AiSearchReply(AiSearchReply.Status.OK, null, "Знакомство", 0, 0, false, List.of());
            java.util.function.Function<AiSearchReply, Boolean> send = call.getArgument(2);
            assertThat(send.apply(reply)).isFalse();
            return reply;
        });
        when(conversation.handle(eq(1L), eq("DB failure"), any())).thenThrow(new IllegalStateException("fixture"));
        try {
            var bot = bot(server);
            var polling = new TelegramPolling(bot, new TelegramUpdateHandler(bot, conversation));
            try {
                polling.start();
                assertThat(polls.poll(5, TimeUnit.SECONDS)).doesNotContainKey("offset");
                assertThat(polls.poll(5, TimeUnit.SECONDS)).containsEntry("offset", "43");
                assertThat(sends).hasValue(1);
                verify(conversation).handle(eq(1L), eq("DB failure"), any());
            } finally { polling.stop(); bot.shutdown(); }
            polls.clear();
            var restarted = bot(server);
            var newPolling = new TelegramPolling(restarted, new TelegramUpdateHandler(restarted, conversation));
            try {
                newPolling.start();
                assertThat(polls.poll(5, TimeUnit.SECONDS)).doesNotContainKey("offset");
            } finally { newPolling.stop(); restarted.shutdown(); }
        } finally { server.stop(0); }
    }

    @Test
    void api429ExposesRetryAfterAndDoesNotAutomaticallyResendMessage() throws Exception {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            attempts.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"ok\":false,\"error_code\":429,\"description\":\"fixture\",\"parameters\":{\"retry_after\":7}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var bot = bot(server);
        try {
            var response = bot.execute(new SendMessage(1L, "Fixture"));
            assertThat(response.isOk()).isFalse();
            assertThat(response.errorCode()).isEqualTo(429);
            assertThat(response.parameters().retryAfter()).isEqualTo(7);
            assertThat(attempts).hasValue(1);
        } finally { bot.shutdown(); server.stop(0); }
    }

    private static TelegramBot bot(HttpServer server) {
        return new TelegramBot.Builder("offline-fixture").okHttpClient(TelegramConfiguration.transport())
                .apiUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/")
                .updateListenerSleep(100).build();
    }

    private static Map<String, String> form(String body) {
        var values = new java.util.HashMap<String, String>();
        for (String entry : body.split("&")) {
            var pair = entry.split("=", 2);
            if (pair.length == 2) values.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return values;
    }
}

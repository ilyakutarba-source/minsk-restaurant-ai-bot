package by.ilya.restaurantbot.ai;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;

/** Loopback HTTP fixture exercises the real SDK without external calls or credentials. */
final class ProviderFixture implements AutoCloseable {
    record Reply(int status, String body) { }
    private final HttpServer server;
    private final ArrayDeque<Reply> replies = new ArrayDeque<>();
    private final List<JsonNode> requests = new ArrayList<>();

    ProviderFixture(Reply... responses) throws Exception {
        replies.addAll(List.of(responses));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            var request = AiJson.mapper().readTree(exchange.getRequestBody());
            Reply reply;
            synchronized (this) {
                requests.add(request);
                reply = replies.isEmpty() ? new Reply(500, "{}") : replies.removeFirst();
            }
            if (reply.status() == 0) { // Connection dies after the server received the request.
                exchange.close();
                return;
            }
            byte[] body = reply.body().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    String origin() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    synchronized List<JsonNode> requests() { return List.copyOf(requests); }
    @Override public void close() { server.stop(0); }

    static Reply selection(String args) throws Exception {
        return response(Map.of("role", "assistant", "tool_calls", List.of(Map.of(
                "id", "fixture-call-1", "type", "function", "function",
                Map.of("name", "searchRestaurants", "arguments", args)))), "tool_calls");
    }

    static Reply explanation(String json) throws Exception {
        return response(Map.of("role", "assistant", "content", json), "stop");
    }

    private static Reply response(Map<String, Object> message, String finish) throws Exception {
        return new Reply(200, AiJson.mapper().writeValueAsString(Map.of("id", "fixture-response",
                "object", "chat.completion", "created", 1, "model", "gpt-4.1-mini",
                "choices", List.of(Map.of("index", 0, "finish_reason", finish, "message", message)),
                "usage", Map.of("prompt_tokens", 100, "completion_tokens", 20, "total_tokens", 120))));
    }
}

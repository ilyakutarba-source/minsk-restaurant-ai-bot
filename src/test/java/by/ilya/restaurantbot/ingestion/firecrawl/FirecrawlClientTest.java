package by.ilya.restaurantbot.ingestion.firecrawl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class FirecrawlClientTest {
    @Test
    void sendsDocumentedV2SchemaRequestOnceWithBearerAndParsesRealHttpResponse() throws Exception {
        var requests = new AtomicInteger();
        var request = new AtomicReference<com.fasterxml.jackson.databind.JsonNode>();
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/scrape", exchange -> {
            requests.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            request.set(FirecrawlPoc.mapper().readTree(exchange.getRequestBody()));
            var body = "{\"success\":true,\"data\":{\"json\":{}}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var client = new FirecrawlClient("synthetic-test-credential", endpoint(server));
            assertThat(client.scrape(FirecrawlCandidateTest.SOURCE)).contains("success");
            assertThat(requests.get()).isEqualTo(1);
            assertThat(authorization.get()).isEqualTo("Bearer synthetic-test-credential");
            assertThat(request.get().path("url").asText()).isEqualTo(FirecrawlCandidateTest.SOURCE);
            assertThat(request.get().path("onlyMainContent").asBoolean()).isFalse();
            assertThat(request.get().path("formats").get(0).path("type").asText()).isEqualTo("json");
            assertThat(request.get().path("formats").get(0).path("schema").path("required")).hasSize(6);
            assertThat(request.get().has("jsonOptions")).isFalse();
            assertThat(request.get().toString()).doesNotContain("synthetic-test-credential");
        } finally { server.stop(0); }
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 401, 402, 429, 500, 503})
    void failedHttpNeverRetriesFollowsRedirectOrExposesBody(int status) throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/scrape", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Location", "/redirect-must-not-be-followed");
            byte[] body = "sensitive-provider-body synthetic-test-credential".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var client = new FirecrawlClient("synthetic-test-credential", endpoint(server));
            assertThatThrownBy(() -> client.scrape(FirecrawlCandidateTest.SOURCE)).isInstanceOf(IOException.class)
                    .hasMessage("Firecrawl acquisition failed").hasNoCause();
            assertThat(requests.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }

    @Test
    void requiresKeyAndRefusesNonOfficialRemoteApiEndpoint() {
        assertThatThrownBy(() -> new FirecrawlClient(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FirecrawlClient("synthetic", URI.create("https://other.example/v2/scrape")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://restaurant.example/", "https://user:password@restaurant.example/",
            "https://restaurant.example/?token=synthetic", "file:///tmp/data"})
    void rejectsUnsafeSourceBeforeHttp(String source) {
        assertThatThrownBy(() -> new FirecrawlClient("synthetic").scrape(source))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static URI endpoint(HttpServer server) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v2/scrape");
    }
}

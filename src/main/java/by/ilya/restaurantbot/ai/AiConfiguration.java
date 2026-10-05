package by.ilya.restaurantbot.ai;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;

import by.ilya.restaurantbot.search.RestaurantSearchService;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "restaurant-bot.ai.enabled", havingValue = "true")
public class AiConfiguration {
    @Bean(destroyMethod = "close")
    CloseableHttpClient aiHttpClient() {
        var connections = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(2)).setSocketTimeout(Timeout.ofSeconds(10)).build())
                .build();
        return HttpClients.custom().setConnectionManager(connections)
                .setDefaultRequestConfig(RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.ofSeconds(2)).setResponseTimeout(Timeout.ofSeconds(10)).build())
                .disableAutomaticRetries().disableRedirectHandling().build();
    }

    @Bean
    OpenAiChatModel searchChatModel(CloseableHttpClient aiHttpClient,
            @Value("${restaurant-bot.ai.api-key}") String key) {
        if (key == null || key.isBlank()) throw new IllegalStateException("AIAI_API_KEY is required when AI is enabled");
        return createModel(aiHttpClient, key, "https://api.aiai.by");
    }

    // Package access allows HTTP fixtures to exercise the production transport and SDK settings.
    static OpenAiChatModel createModel(CloseableHttpClient http, String key, String origin) {
        var errors = new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return response.getStatusCode().isError();
            }
            @Override
            public void handleError(URI url, HttpMethod method, ClientHttpResponse response) {
                throw new IllegalStateException("AI provider request failed");
            }
        };
        var api = OpenAiApi.builder().baseUrl(origin).completionsPath("/v1/chat/completions")
                .apiKey(key).responseErrorHandler(errors)
                .restClientBuilder(RestClient.builder().requestFactory(new HttpComponentsClientHttpRequestFactory(http)))
                .build();
        return OpenAiChatModel.builder().openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model("gpt-4.1-mini").temperature(0.0)
                    .maxTokens(350).N(1).internalToolExecutionEnabled(false).parallelToolCalls(false).build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build()).build();
    }

    @Bean
    SpringAiSearchAdapter springAiSearchAdapter(OpenAiChatModel searchChatModel,
            RestaurantSearchService service, Clock clock,
            @Value("${restaurant-bot.ai.explanation-enabled:true}") boolean explanationEnabled) {
        return new SpringAiSearchAdapter(searchChatModel, service, clock, explanationEnabled);
    }
}

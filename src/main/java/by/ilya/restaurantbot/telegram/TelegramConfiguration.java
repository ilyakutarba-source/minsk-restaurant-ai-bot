package by.ilya.restaurantbot.telegram;

import by.ilya.restaurantbot.conversation.ConversationService;
import com.pengrad.telegrambot.TelegramBot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/** The transport needs the existing opt-in AI flow and a runtime token. */
@Configuration
@ConditionalOnProperty(name = "restaurant-bot.ai.enabled", havingValue = "true")
@Conditional(TelegramConfiguration.TokenConfigured.class)
public class TelegramConfiguration {
    static final class TokenConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty("restaurant-bot.telegram.bot-token"));
        }
    }

    @Bean(destroyMethod = "shutdown")
    TelegramBot telegramBot(@Value("${restaurant-bot.telegram.bot-token}") String token) {
        return new TelegramBot.Builder(token).okHttpClient(transport()).build();
    }

    static okhttp3.OkHttpClient transport() {
        return new okhttp3.OkHttpClient.Builder().retryOnConnectionFailure(false)
                .followRedirects(false).followSslRedirects(false)
                .connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build();
    }

    @Bean
    TelegramUpdateHandler telegramUpdateHandler(TelegramBot bot, ConversationService conversation) {
        return new TelegramUpdateHandler(bot, conversation);
    }

    @Bean
    TelegramPolling telegramPolling(TelegramBot bot, TelegramUpdateHandler handler) {
        return new TelegramPolling(bot, handler);
    }
}

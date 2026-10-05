package by.ilya.restaurantbot.telegram;

import by.ilya.restaurantbot.ai.SpringAiSearchAdapter;
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
        return new TelegramBot(token);
    }

    @Bean
    TelegramUpdateHandler telegramUpdateHandler(TelegramBot bot, SpringAiSearchAdapter adapter) {
        return new TelegramUpdateHandler(bot, adapter);
    }

    @Bean
    TelegramPolling telegramPolling(TelegramBot bot, TelegramUpdateHandler handler) {
        return new TelegramPolling(bot, handler);
    }
}

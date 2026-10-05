package by.ilya.restaurantbot.telegram;

import by.ilya.restaurantbot.conversation.ConversationService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.ExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TelegramConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TelegramConfiguration.class)
            .withBean(ConversationService.class, () -> mock(ConversationService.class));

    @Test
    void disabledAiDoesNotCreateTransportEvenWithToken() {
        runner.withPropertyValues("restaurant-bot.ai.enabled=false", "restaurant-bot.telegram.bot-token=offline-fixture")
                .run(context -> assertThat(context).doesNotHaveBean(TelegramBot.class).doesNotHaveBean(TelegramPolling.class));
    }

    @Test
    void enabledAiWithoutTokenKeepsTransportInactive() {
        runner.withPropertyValues("restaurant-bot.ai.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(TelegramBot.class).doesNotHaveBean(TelegramPolling.class));
        runner.withPropertyValues("restaurant-bot.ai.enabled=true", "restaurant-bot.telegram.bot-token=   ")
                .run(context -> assertThat(context).doesNotHaveBean(TelegramBot.class));
    }

    @Test
    void configuredTransportStartsOneLibraryPollerAndStopsWithContext() {
        try (var construction = mockConstruction(TelegramBot.class)) {
            runner.withPropertyValues("restaurant-bot.ai.enabled=true", "restaurant-bot.telegram.bot-token=offline-fixture")
                    .run(context -> {
                        assertThat(context).hasSingleBean(TelegramBot.class).hasSingleBean(TelegramPolling.class);
                        var polling = context.getBean(TelegramPolling.class);
                        assertThat(polling.isRunning()).isTrue();
                        polling.start();
                        verify(context.getBean(TelegramBot.class)).setUpdatesListener(
                                same(context.getBean(TelegramUpdateHandler.class)), any(ExceptionHandler.class));
                    });
            assertThat(construction.constructed()).hasSize(1);
            verify(construction.constructed().getFirst()).removeGetUpdatesListener();
            verify(construction.constructed().getFirst()).shutdown();
        }
    }
}

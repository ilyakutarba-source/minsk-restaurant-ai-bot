package by.ilya.restaurantbot.telegram;

import java.util.Arrays;
import java.util.List;

import by.ilya.restaurantbot.ai.AiSearchReply;
import by.ilya.restaurantbot.conversation.ConversationService;
import com.pengrad.telegrambot.utility.BotUtils;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TelegramUpdateHandlerTest {
    private TelegramBot bot;
    private ConversationService adapter;
    private TelegramUpdateHandler handler;

    @BeforeEach
    void setup() {
        bot = mock(TelegramBot.class);
        adapter = mock(ConversationService.class);
        handler = new TelegramUpdateHandler(bot, adapter);
        var sent = mock(SendResponse.class);
        when(sent.isOk()).thenReturn(true);
        when(bot.execute(any(SendMessage.class))).thenReturn(sent);
    }

    private static Update textUpdate(String type, String text) {
        return BotUtils.parseUpdate("""
                {"update_id":1,"message":{"message_id":1,"chat":{"id":7000000001,"type":"%s"},"text":"%s"}}
                """.formatted(type, text));
    }

    @Test
    void privateTextGoesDirectlyToExistingAdapterAndPlainTextSendMessage() {
        String text = "Сегодня в 21:00 нас двое, бюджет 150 BYN, итальянская кухня.";
        String rendered = "1. <Название> & _*[]()~`>#+-=|{}.!\nАдрес: <b>улица</b> & площадь";
        respond(text, new AiSearchReply(AiSearchReply.Status.OK, null, rendered, 2, 1, false, List.of()));
        assertThat(handler.process(List.of(textUpdate("private", text)))).isEqualTo(UpdatesListener.CONFIRMED_UPDATES_ALL);
        verify(adapter).handle(eq(7000000001L), eq(text), any());
        var request = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(request.capture());
        assertThat(request.getValue().getText()).isEqualTo(rendered);
        assertThat(request.getValue().getParseMode()).isNull();
        assertThat(request.getValue().getParameters()).containsEntry("chat_id", 7000000001L).doesNotContainKey("parse_mode");
        verifyNoMoreInteractions(adapter, bot);
    }

    @ParameterizedTest
    @ValueSource(strings = {"group", "supergroup", "channel"})
    void nonPrivateChatsAreIgnoredWithoutAiOrSend(String type) {
        handler.process(List.of(textUpdate(type, "Запрос")));
        verifyNoInteractions(adapter, bot);
    }

    @Test
    void unsupportedAndMalformedUpdatesAreIgnored() {
        handler.process(Arrays.asList(null, BotUtils.parseUpdate("{}"),
                BotUtils.parseUpdate("{\"channel_post\":{\"text\":\"Запрос\"}}"),
                BotUtils.parseUpdate("{\"message\":{\"text\":\"Запрос\"}}"),
                BotUtils.parseUpdate("{\"message\":{\"chat\":{\"id\":1,\"type\":\"private\"}}}"),
                textUpdate("private", " ")));
        verifyNoInteractions(adapter, bot);
    }

    @ParameterizedTest
    @EnumSource(value = AiSearchReply.Status.class, names = {"NO_RESULTS", "NEED_CLARIFICATION", "INVALID_INPUT", "TEMPORARILY_UNAVAILABLE"})
    void existingControlledTextIsSentOnceWithoutAdditionalAiCalls(AiSearchReply.Status status) {
        respond("Запрос", new AiSearchReply(status, null, "Контролируемый Java ответ", 1, 0, false, List.of()));
        handler.process(List.of(textUpdate("private", "Запрос")));
        var request = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(request.capture());
        assertThat(request.getValue().getText()).isEqualTo("Контролируемый Java ответ");
        verify(adapter).handle(eq(7000000001L), eq("Запрос"), any());
        verifyNoMoreInteractions(adapter, bot);
    }

    @Test
    void oneFailedUpdateDoesNotPreventNextUpdate() {
        when(adapter.handle(anyLong(), eq("Первый"), any())).thenThrow(new IllegalStateException("private provider payload"));
        respond("Второй", new AiSearchReply(AiSearchReply.Status.NEED_CLARIFICATION, null,
                "Укажите полный запрос", 1, 0, false, List.of()));
        handler.process(List.of(textUpdate("private", "Первый"), textUpdate("private", "Второй")));
        verify(adapter).handle(eq(7000000001L), eq("Второй"), any());
        var request = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(request.capture());
        assertThat(request.getValue().getText()).isEqualTo("Укажите полный запрос").doesNotContain("payload", "Exception");
    }

    private void respond(String text, AiSearchReply reply) {
        when(adapter.handle(anyLong(), eq(text), any())).thenAnswer(call -> {
            java.util.function.Function<AiSearchReply, Boolean> send = call.getArgument(2);
            send.apply(reply);
            return reply;
        });
    }
}

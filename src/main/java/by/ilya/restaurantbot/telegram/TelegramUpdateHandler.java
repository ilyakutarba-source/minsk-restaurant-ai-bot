package by.ilya.restaurantbot.telegram;

import java.util.List;

import by.ilya.restaurantbot.ai.AiSearchReply;
import by.ilya.restaurantbot.conversation.ConversationService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Private-chat transport; conversation lifecycle belongs to the application service. */
public final class TelegramUpdateHandler implements UpdatesListener {
    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateHandler.class);
    private final TelegramBot bot;
    private final ConversationService conversation;

    public TelegramUpdateHandler(TelegramBot bot, ConversationService conversation) {
        this.bot = bot;
        this.conversation = conversation;
    }

    @Override
    public int process(List<Update> updates) {
        for (var update : updates) {
            try {
                handle(update);
            } catch (RuntimeException failure) {
                // Isolate this update; no payload, chat identifiers or stack traces in logs/UX.
                log.warn("Telegram update handling failed");
            }
        }
        // Acknowledge handled/ignored/failed updates; no delivery retry or durable checkpoint.
        return CONFIRMED_UPDATES_ALL;
    }

    private void handle(Update update) {
        if (update == null || update.message() == null) return;
        var message = update.message();
        if (message.chat() == null || message.chat().type() != Chat.Type.Private
                || message.chat().id() == null || message.text() == null || message.text().isBlank()) return;

        conversation.handle(message.chat().id().longValue(), message.text(), reply -> send(message.chat().id().longValue(), reply));
    }

    private boolean send(long chatId, AiSearchReply reply) {
        // Plain text: model prose is never used and Telegram markup cannot interpret own data.
        boolean success = true;
        try {
            for (String part : TelegramMessages.split(reply.text())) {
                var sent = bot.execute(new SendMessage(chatId, part));
                if (sent == null || !sent.isOk()) {
                    success = false;
                    // Only numeric API status; never description, request URL or payload.
                    if (sent != null) log.warn("Telegram send failed: code={}, retryAfter={}", sent.errorCode(),
                            sent.parameters() == null ? null : sent.parameters().retryAfter());
                    break; // Partial delivery is a failed turn; do not resend an earlier part.
                }
            }
        } catch (RuntimeException transportFailure) {
            success = false;
            log.warn("Telegram send transport failed");
        }
        var candidates = reply.searchResult() == null ? List.of()
                : reply.searchResult().candidates().stream().map(c -> c.restaurant().id()).toList();
        log.info("Telegram search turn: status={}, modelCalls={}, toolExecutions={}, candidateIds={}, explanationFallback={}, sendMessage={}",
                reply.status(), reply.modelCalls(), reply.toolExecutions(), candidates, reply.explanationFallback(),
                success ? "PASS" : "FAIL");
        return success;
    }
}

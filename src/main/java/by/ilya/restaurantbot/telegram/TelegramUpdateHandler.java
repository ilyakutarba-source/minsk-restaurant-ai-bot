package by.ilya.restaurantbot.telegram;

import java.util.List;

import by.ilya.restaurantbot.ai.SpringAiSearchAdapter;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Stateless transport: facts, limits and rendering belong to the existing AI adapter. */
public final class TelegramUpdateHandler implements UpdatesListener {
    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateHandler.class);
    private final TelegramBot bot;
    private final SpringAiSearchAdapter adapter;

    public TelegramUpdateHandler(TelegramBot bot, SpringAiSearchAdapter adapter) {
        this.bot = bot;
        this.adapter = adapter;
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
        // Standard library acknowledgment; delivery/restart recovery is a later task.
        return CONFIRMED_UPDATES_ALL;
    }

    private void handle(Update update) {
        if (update == null || update.message() == null) return;
        var message = update.message();
        if (message.chat() == null || message.chat().type() != Chat.Type.Private
                || message.chat().id() == null || message.text() == null || message.text().isBlank()) return;

        var reply = adapter.search(message.text());
        // Plain text: model prose is never used and Telegram markup cannot interpret own data.
        var sent = bot.execute(new SendMessage(message.chat().id().longValue(), reply.text()));
        boolean success = sent != null && sent.isOk();
        var candidates = reply.searchResult() == null ? List.of()
                : reply.searchResult().candidates().stream().map(c -> c.restaurant().id()).toList();
        log.info("Telegram search turn: status={}, modelCalls={}, toolExecutions={}, candidateIds={}, explanationFallback={}, sendMessage={}",
                reply.status(), reply.modelCalls(), reply.toolExecutions(), candidates, reply.explanationFallback(),
                success ? "PASS" : "FAIL");
    }
}

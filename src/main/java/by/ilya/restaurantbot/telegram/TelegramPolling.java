package by.ilya.restaurantbot.telegram;

import com.pengrad.telegrambot.TelegramBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/** One library poller per application; offsets are managed by Pengrad. */
public final class TelegramPolling implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(TelegramPolling.class);
    private final TelegramBot bot;
    private final TelegramUpdateHandler handler;
    private volatile boolean running;

    public TelegramPolling(TelegramBot bot, TelegramUpdateHandler handler) {
        this.bot = bot;
        this.handler = handler;
    }

    @Override
    public synchronized void start() {
        if (running) return;
        // Never log SDK exceptions: their URLs can include the bot token.
        bot.setUpdatesListener(handler, failure -> log.warn("Telegram polling request failed"));
        running = true;
        log.info("Telegram private-chat long polling started");
    }

    @Override
    public synchronized void stop() {
        if (!running) return;
        bot.removeGetUpdatesListener();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}

package by.ilya.restaurantbot.telegram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TelegramMessagesTest {
    @Test
    void splitPreservesAllFactsAndUnicodeAtAndAcrossTheTelegramBoundary() {
        for (var text : java.util.List.of("я".repeat(4096), "я".repeat(4097), "я".repeat(4095) + "🍝 цена: 19.50 BYN",
                ("Название (ID: 42)\nАдрес: улица\nЦена: 19.50 BYN\n" + "я".repeat(1800) + "\n").repeat(5))) {
            var chunks = TelegramMessages.split(text);
            assertThat(String.join("", chunks)).isEqualTo(text);
            assertThat(chunks).allSatisfy(chunk -> {
                assertThat(chunk.length()).isBetween(1, 4096);
                assertThat(Character.isLowSurrogate(chunk.charAt(0))).isFalse();
                assertThat(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1))).isFalse();
            });
        }
    }
}

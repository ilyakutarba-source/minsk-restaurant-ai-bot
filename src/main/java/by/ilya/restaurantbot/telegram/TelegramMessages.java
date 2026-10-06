package by.ilya.restaurantbot.telegram;

import java.util.ArrayList;
import java.util.List;

/** Lossless plain-text split. Prefer complete lines; never cut a UTF-16 surrogate pair. */
final class TelegramMessages {
    static final int MAX_LENGTH = 4096;

    private TelegramMessages() { }

    static List<String> split(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Empty reply");
        var parts = new ArrayList<String>();
        for (int start = 0; start < text.length();) {
            int end = Math.min(start + MAX_LENGTH, text.length());
            if (end < text.length()) {
                int newline = text.lastIndexOf('\n', end - 1);
                if (newline >= start) end = newline + 1;
                else if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
            }
            parts.add(text.substring(start, end));
            start = end;
        }
        return List.copyOf(parts);
    }
}

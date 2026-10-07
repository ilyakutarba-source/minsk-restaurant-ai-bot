package by.ilya.restaurantbot.miniapp;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Bot-owned HMAC validation: https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app */
@Component
public class TelegramMiniAppInitDataVerifier {
    static final long MAX_AGE_SECONDS = 3600;
    static final long FUTURE_SKEW_SECONDS = 30;
    private final String token;
    private final Clock clock;

    public TelegramMiniAppInitDataVerifier(@Value("${restaurant-bot.telegram.bot-token:}") String token,
                                          Clock clock) {
        this.token = token;
        this.clock = clock;
    }

    public TrustedContext verify(String raw) {
        try {
            if (token == null || token.isBlank() || raw == null || raw.isBlank() || raw.length() > 8192) {
                throw new IllegalArgumentException();
            }
            var fields = new TreeMap<String, String>();
            for (String pair : raw.split("&", -1)) {
                int separator = pair.indexOf('=');
                if (separator < 1) throw new IllegalArgumentException();
                String key = decode(pair.substring(0, separator));
                String value = decode(pair.substring(separator + 1));
                if (!key.matches("[a-z_]+") || value.chars().anyMatch(c -> c < 32 || c == 127)
                        || fields.putIfAbsent(key, value) != null) throw new IllegalArgumentException();
            }
            String hash = fields.remove("hash");
            if (hash == null || !hash.matches("[a-fA-F0-9]{64}")) throw new IllegalArgumentException();
            // Include every remaining field (including signature, when supplied) in the bot-token HMAC.
            String check = fields.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("\n"));
            byte[] secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), token);
            if (!MessageDigest.isEqual(hmac(secret, check), HexFormat.of().parseHex(hash))) {
                throw new IllegalArgumentException();
            }
            String date = fields.get("auth_date");
            if (date == null || !date.matches("[0-9]{1,12}")) throw new IllegalArgumentException();
            long timestamp = Long.parseLong(date);
            long now = clock.instant().getEpochSecond();
            if (timestamp < now - MAX_AGE_SECONDS || timestamp > now + FUTURE_SKEW_SECONDS) {
                throw new IllegalArgumentException();
            }
            // No profile or user identity is needed by these read-only catalog endpoints.
            return new TrustedContext(Instant.ofEpochSecond(timestamp));
        } catch (IllegalArgumentException e) {
            // Never retain input, signature, token or decoder diagnostics in the exception.
            throw new AuthFailed();
        }
    }

    private static String decode(String value) {
        String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
        if (decoded.indexOf('\uFFFD') >= 0) throw new IllegalArgumentException();
        return decoded;
    }

    private static byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable");
        }
    }

    public record TrustedContext(Instant authenticatedAt) { }

    public static final class AuthFailed extends RuntimeException {
        public AuthFailed() { super("AUTH_FAILED", null, false, false); }
    }
}

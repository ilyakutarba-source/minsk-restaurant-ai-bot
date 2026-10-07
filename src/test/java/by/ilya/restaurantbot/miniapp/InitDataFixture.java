package by.ilya.restaurantbot.miniapp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class InitDataFixture {
    static final String TOKEN = "123456789:synthetic-miniapp-test-only";
    static final long NOW = 1791374400L; // 2026-10-07T12:00:00Z

    static String signed(long timestamp) {
        return signedFields("auth_date=" + timestamp + "\nquery_id=synthetic-query\nsignature=synthetic-signature"
                + "\nuser={\"id\":12345,\"first_name\":\"Тест & + /\"}");
    }

    static String signedFields(String check) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] key = mac.doFinal(TOKEN.getBytes(StandardCharsets.UTF_8));
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            String hash = HexFormat.of().formatHex(mac.doFinal(check.getBytes(StandardCharsets.UTF_8)));
            return java.util.Arrays.stream(check.split("\n"))
                    .map(pair -> pair.substring(0, pair.indexOf('=')) + "="
                            + URLEncoder.encode(pair.substring(pair.indexOf('=') + 1), StandardCharsets.UTF_8))
                    .collect(java.util.stream.Collectors.joining("&")) + "&hash=" + hash;
        } catch (Exception e) { throw new AssertionError(e); }
    }
}

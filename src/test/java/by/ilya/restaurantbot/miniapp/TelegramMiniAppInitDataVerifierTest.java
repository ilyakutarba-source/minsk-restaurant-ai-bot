package by.ilya.restaurantbot.miniapp;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class TelegramMiniAppInitDataVerifierTest {
    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(InitDataFixture.NOW), ZoneOffset.UTC);
    private final TelegramMiniAppInitDataVerifier verifier =
            new TelegramMiniAppInitDataVerifier(InitDataFixture.TOKEN, clock);

    @Test
    void validSignedUnicodeAndEncodedDelimitersAreAccepted() {
        assertThat(verifier.verify(InitDataFixture.signed(InitDataFixture.NOW)).authenticatedAt()).isEqualTo(clock.instant());
    }

    @Test
    void independentDotNetHmacVectorWithoutOptionalFieldsIsAcceptedAndWrongTokenFails() {
        // Computed independently with .NET HMACSHA256, not InitDataFixture's signing helper.
        String raw = "auth_date=1791374400&hash=eb3c3fbf0deee72e9f47e9e476a9154e160000c1d83d525a0864f7d1c1381fe5";
        assertThat(verifier.verify(raw).authenticatedAt()).isEqualTo(clock.instant());
        assertThatThrownBy(() -> new TelegramMiniAppInitDataVerifier("different-synthetic-token", clock).verify(raw))
                .isInstanceOf(TelegramMiniAppInitDataVerifier.AuthFailed.class);
    }

    @Test
    void fieldOrderDoesNotAffectValidation() {
        String[] pairs = InitDataFixture.signed(InitDataFixture.NOW).split("&");
        java.util.Collections.reverse(java.util.Arrays.asList(pairs));
        assertThatCode(() -> verifier.verify(String.join("&", pairs))).doesNotThrowAnyException();
    }

    @Test
    void modifiedFieldAndHashAreRejected() {
        String valid = InitDataFixture.signed(InitDataFixture.NOW);
        rejected(valid.replace("synthetic-query", "modified-query"));
        rejected(valid.replaceAll("hash=[a-f0-9]{64}", "hash=" + "0".repeat(64)));
        rejected(valid.substring(0, valid.indexOf("&hash=")));
        rejected(valid + "&auth_date=" + InitDataFixture.NOW);
        rejected(valid + "&%61uth_date=" + InitDataFixture.NOW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "broken", "a=%GG", "=value", "auth_date=1&&hash=ab", "a=%FF", "a=%0A", "a=%00"})
    void malformedInputIsRejected(String raw) { rejected(raw); }

    @Test
    void nullAndOversizedInputAndMissingTokenFailClosed() {
        rejected(null);
        rejected("a=" + "x".repeat(8192));
        assertThatThrownBy(() -> new TelegramMiniAppInitDataVerifier("", clock)
                .verify(InitDataFixture.signed(InitDataFixture.NOW)))
                .isInstanceOf(TelegramMiniAppInitDataVerifier.AuthFailed.class);
    }

    @Test
    void freshnessBoundariesAndFutureSkewAreEnforced() {
        assertThatCode(() -> verifier.verify(InitDataFixture.signed(InitDataFixture.NOW - 3600))).doesNotThrowAnyException();
        assertThatCode(() -> verifier.verify(InitDataFixture.signed(InitDataFixture.NOW + 30))).doesNotThrowAnyException();
        rejected(InitDataFixture.signed(InitDataFixture.NOW - 3601));
        rejected(InitDataFixture.signed(InitDataFixture.NOW + 31));
        rejected(InitDataFixture.signedFields("auth_date=invalid"));
        rejected(InitDataFixture.signedFields("query_id=missing-date"));
    }

    @Test
    void devModeCannotStartWithoutDevTestProfileOrWithPublicBinding() {
        var environment = new MockEnvironment();
        assertThatThrownBy(() -> config(environment, "127.0.0.1")).isInstanceOf(IllegalStateException.class);
        environment.setActiveProfiles("dev");
        assertThatCode(() -> config(environment, "127.0.0.1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> config(environment, "0.0.0.0")).isInstanceOf(IllegalStateException.class);
        environment.setActiveProfiles("dev", "production");
        assertThatThrownBy(() -> config(environment, "127.0.0.1")).isInstanceOf(IllegalStateException.class);
    }

    private void config(MockEnvironment environment, String address) {
        new MiniAppWebConfiguration(verifier, new ObjectMapper(), environment, true, address);
    }

    private void rejected(String raw) {
        assertThatThrownBy(() -> verifier.verify(raw)).isInstanceOf(TelegramMiniAppInitDataVerifier.AuthFailed.class)
                .hasMessage("AUTH_FAILED").hasNoCause();
    }
}

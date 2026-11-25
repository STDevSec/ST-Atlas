package com.stdevsec.atlas.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpTest {

    // RFC 6238 Appendix B secret "12345678901234567890" (SHA-1), base32-encoded.
    private static final String SECRET = Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void rfc6238ReferenceVectors() {
        assertThat(SECRET).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        // RFC values are 8 digits; a 6-digit code is the last 6 of them.
        assertThat(Totp.code(SECRET, 59 / 30)).isEqualTo("287082");
        assertThat(Totp.code(SECRET, 1111111109L / 30)).isEqualTo("081804");
        assertThat(Totp.code(SECRET, 1234567890L / 30)).isEqualTo("005924");
    }

    @Test
    void acceptsOneStepOfDriftOnly() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        long step = Totp.timeStep(now);
        assertThat(Totp.verify(SECRET, Totp.code(SECRET, step - 1), now)).isEqualTo(step - 1);
        assertThat(Totp.verify(SECRET, Totp.code(SECRET, step + 2), now)).isEqualTo(-1);
        assertThat(Totp.verify(SECRET, "12345", now)).isEqualTo(-1);
    }

    @Test
    void base32RoundTrip() {
        byte[] data = {0, 1, 2, (byte) 250, (byte) 255};
        assertThat(Totp.decode(Totp.base32(data))).containsExactly(data);
    }
}

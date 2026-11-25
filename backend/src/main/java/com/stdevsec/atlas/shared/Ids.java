package com.stdevsec.atlas.shared;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/** Time-ordered UUID v7 identifiers (RFC 9562): index-friendly and not guessable. */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {}

    public static UUID newId() {
        return newId(Clock.systemUTC());
    }

    static UUID newId(Clock clock) {
        long millis = clock.millis();
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        long msb = (millis << 16) | 0x7000L | ((random[0] & 0x0FL) << 8) | (random[1] & 0xFFL);
        long lsb = 0x8000000000000000L | ((random[2] & 0x3FL) << 56);
        for (int i = 3; i < 10; i++) {
            lsb |= (random[i] & 0xFFL) << (8 * (9 - i));
        }
        return new UUID(msb, lsb);
    }
}

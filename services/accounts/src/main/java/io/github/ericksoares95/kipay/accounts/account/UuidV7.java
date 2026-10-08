package io.github.ericksoares95.kipay.accounts.account;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUID version 7 (RFC 9562): 48-bit Unix timestamp in milliseconds followed by random bits. Time-ordered, which keeps
 * B-tree primary key indexes compact. Used by the JPA id generator and by native inserts.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        return generate(System.currentTimeMillis());
    }

    public static UUID generate(long epochMillis) {
        long randomA = RANDOM.nextLong();
        long randomB = RANDOM.nextLong();
        long msb = (epochMillis << 16) | 0x7000L | (randomA & 0x0FFFL);
        long lsb = (randomB & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }
}

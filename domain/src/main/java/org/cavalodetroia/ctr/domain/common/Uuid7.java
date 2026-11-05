package org.cavalodetroia.ctr.domain.common;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * UUID version 7 (RFC 9562): a 48-bit Unix timestamp in milliseconds, then random bits. Ids sort by creation time,
 * which keeps index inserts at the end of the B-tree. Twenty lines here instead of a dependency.
 */
public final class Uuid7 {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long VERSION_7 = 0x7000L;
    private static final long VARIANT_2 = 0x8000_0000_0000_0000L;

    private Uuid7() {
    }

    /**
     * @return a new UUIDv7 stamped with the current UTC time
     */
    public static UUID generate() {
        return generate(Clock.systemUTC());
    }

    /**
     * @param clock where the timestamp comes from
     * @return a new UUIDv7 stamped with {@code clock.millis()}
     */
    public static UUID generate(final Clock clock) {
        final long timestamp = clock.millis() & 0xFFFF_FFFF_FFFFL;
        final long mostSignificant = timestamp << 16 | VERSION_7 | RANDOM.nextInt(0x1000);
        final long leastSignificant = VARIANT_2 | RANDOM.nextLong() >>> 2;
        return new UUID(mostSignificant, leastSignificant);
    }
}

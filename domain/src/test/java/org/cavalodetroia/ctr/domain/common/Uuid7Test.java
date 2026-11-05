package org.cavalodetroia.ctr.domain.common;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Uuid7Test {

    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00.123Z");

    @Test
    void should_haveVersion7AndVariant2_when_generated() {
        final UUID id = Uuid7.generate(Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void should_carryTheClockMillis_when_generated() {
        final UUID id = Uuid7.generate(Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(NOW.toEpochMilli());
    }

    @Test
    void should_sortByTimestamp_when_generatedOverIncreasingTime() {
        final List<UUID> ids = new ArrayList<>();
        for (int millis = 0; millis < 1000; millis++) {
            ids.add(Uuid7.generate(Clock.fixed(NOW.plusMillis(millis), ZoneOffset.UTC)));
        }

        assertThat(ids).extracting(id -> id.getMostSignificantBits() >>> 16).isSorted();
        assertThat(ids).doesNotHaveDuplicates();
    }
}

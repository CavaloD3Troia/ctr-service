package org.cavalodetroia.ctr.infra.persistence;

import org.cavalodetroia.ctr.domain.event.Event;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventRecordConverterTest {

    private final EventRecordConverter converter = new EventRecordConverter();

    @Test
    void should_preserveAllFields_when_roundTripped() {
        final Event event = new Event(UUID.randomUUID(), "Benfica vs Porto", "FOOTBALL",
                Instant.parse("2026-10-30T10:15:30.123Z"));

        assertThat(converter.toDomain(converter.toRecord(event))).isEqualTo(event);
    }
}

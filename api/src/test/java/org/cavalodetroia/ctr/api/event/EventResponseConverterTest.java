package org.cavalodetroia.ctr.api.event;

import org.cavalodetroia.ctr.domain.event.Event;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventResponseConverterTest {

    private final EventResponseConverter converter = new EventResponseConverter();

    @Test
    void should_mapAllFields_when_eventExists() {
        final Event event = new Event(UUID.randomUUID(), "Benfica vs Porto", "FOOTBALL",
                Instant.parse("2026-10-30T10:15:30.123Z"));

        assertThat(converter.toResponse(event))
                .isEqualTo(new EventResponse(event.id(), "Benfica vs Porto", "FOOTBALL", event.createdAt()));
    }
}

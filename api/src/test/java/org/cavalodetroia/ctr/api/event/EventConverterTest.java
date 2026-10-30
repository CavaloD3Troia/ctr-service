package org.cavalodetroia.ctr.api.event;

import org.cavalodetroia.ctr.domain.event.Event;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventConverterTest {

    private final EventConverter converter = new EventConverter();

    @Test
    void should_mapAllFields_when_convertingToResponse() {
        final Event event = new Event(UUID.randomUUID(), "Benfica vs Porto");

        assertThat(converter.toResponse(event)).isEqualTo(new EventResponse(event.id(), "Benfica vs Porto"));
    }
}

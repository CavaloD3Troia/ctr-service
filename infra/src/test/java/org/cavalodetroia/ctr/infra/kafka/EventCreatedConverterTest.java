package org.cavalodetroia.ctr.infra.kafka;

import org.cavalodetroia.ctr.domain.event.Event;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventCreatedConverterTest {

    private final EventCreatedConverter converter = new EventCreatedConverter();

    @Test
    void should_mapAllFields_when_convertingToMessage() {
        final Event event = new Event(UUID.randomUUID(), "Benfica vs Porto");

        final var message = converter.toMessage(event);

        assertThat(message.getEventId()).isEqualTo(event.id().toString());
        assertThat(message.getName()).isEqualTo("Benfica vs Porto");
    }
}

package org.cavalodetroia.ctr.infra.kafka.event;

import org.cavalodetroia.contracts.ctr.v1.EventLifecycle;
import org.cavalodetroia.ctr.domain.event.Event;
import org.cavalodetroia.ctr.infra.kafka.ProtoTimestamps;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventLifecycleConverterTest {

    private final EventLifecycleConverter converter = new EventLifecycleConverter();

    @Test
    void should_buildEventCreated_when_eventIsNew() {
        final Event event = new Event(UUID.randomUUID(), "Benfica vs Porto", "FOOTBALL",
                Instant.parse("2026-10-30T10:15:30.123Z"));

        final EventLifecycle message = converter.toEventCreated(event);

        assertThat(message.getEventId()).isEqualTo(event.id().toString());
        assertThat(ProtoTimestamps.toInstant(message.getOccurredAt())).isEqualTo(event.createdAt());
        assertThat(message.getChangeCase()).isEqualTo(EventLifecycle.ChangeCase.EVENT_CREATED);
        assertThat(message.getEventCreated().getName()).isEqualTo("Benfica vs Porto");
        assertThat(message.getEventCreated().getSport()).isEqualTo("FOOTBALL");
    }
}

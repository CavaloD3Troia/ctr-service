package org.cavalodetroia.ctr.infra.persistence;

import org.cavalodetroia.ctr.domain.event.Event;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventRecordConverterTest {

    private final EventRecordConverter converter = new EventRecordConverter();

    @Test
    void should_mapAllFields_when_convertingToRecord() {
        final Event event = new Event(UUID.randomUUID(), "Benfica vs Porto");

        final var record = converter.toRecord(event);

        assertThat(record.getId()).isEqualTo(event.id());
        assertThat(record.getName()).isEqualTo("Benfica vs Porto");
    }
}

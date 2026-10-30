package org.cavalodetroia.ctr.api.event;

import org.cavalodetroia.ctr.domain.event.NewEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventRequestConverterTest {

    private final EventRequestConverter converter = new EventRequestConverter();

    @Test
    void should_mapAllFields_when_requestIsComplete() {
        final NewEvent newEvent = converter.toDomain(new CreateEventRequest("Benfica vs Porto", "FOOTBALL"));

        assertThat(newEvent).isEqualTo(new NewEvent("Benfica vs Porto", "FOOTBALL"));
    }
}

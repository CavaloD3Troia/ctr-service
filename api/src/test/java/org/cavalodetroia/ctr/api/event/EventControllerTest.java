package org.cavalodetroia.ctr.api.event;

import org.cavalodetroia.ctr.domain.event.Event;
import org.cavalodetroia.ctr.domain.event.port.in.CreateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

    @Mock
    private CreateEvent createEvent;

    private EventController controller;

    @BeforeEach
    void setUp() {
        controller = new EventController(createEvent, new EventConverter());
    }

    @Test
    void should_delegateToTheUseCaseAndReturnItsEvent_when_creating() {
        final Event event = new Event(UUID.randomUUID(), "Benfica vs Porto");
        when(createEvent.create("Benfica vs Porto")).thenReturn(event);

        assertThat(controller.create(new CreateEventRequest("Benfica vs Porto")))
                .isEqualTo(new EventResponse(event.id(), "Benfica vs Porto"));
    }
}

package org.cavalodetroia.ctr.domain.event;

import org.cavalodetroia.ctr.domain.event.port.out.EventPublisher;
import org.cavalodetroia.ctr.domain.event.port.out.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-30T10:15:30.123456Z");

    @Mock
    private EventRepository repository;

    @Mock
    private EventPublisher publisher;

    private EventService service;

    @BeforeEach
    void setUp() {
        service = new EventService(repository, publisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void should_saveThenPublishEvent_when_created() {
        final Event event = service.create(new NewEvent(" Benfica vs Porto ", "football"));

        assertThat(event.id()).isNotNull();
        assertThat(event.name()).isEqualTo("Benfica vs Porto");
        assertThat(event.sport()).isEqualTo("FOOTBALL");
        assertThat(event.createdAt()).isEqualTo(Instant.parse("2026-10-30T10:15:30.123Z"));

        final InOrder inOrder = inOrder(repository, publisher);
        inOrder.verify(repository).save(event);
        inOrder.verify(publisher).eventCreated(event);
    }

    @Test
    void should_returnEvent_when_itExists() {
        final Event event = new Event(UUID.randomUUID(), "Benfica vs Porto", "FOOTBALL", NOW);
        when(repository.findById(event.id())).thenReturn(Optional.of(event));

        assertThat(service.byId(event.id())).isEqualTo(event);
    }

    @Test
    void should_throwNotFound_when_eventDoesNotExist() {
        final UUID missing = UUID.randomUUID();
        when(repository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.byId(missing))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessageContaining(missing.toString());
        verifyNoInteractions(publisher);
    }
}

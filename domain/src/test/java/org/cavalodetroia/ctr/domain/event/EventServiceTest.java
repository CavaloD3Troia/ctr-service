package org.cavalodetroia.ctr.domain.event;

import org.cavalodetroia.ctr.domain.event.port.out.EventPublisher;
import org.cavalodetroia.ctr.domain.event.port.out.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository repository;

    @Mock
    private EventPublisher publisher;

    @InjectMocks
    private EventService service;

    @Test
    void should_saveThenPublishEvent_when_created() {
        final Event event = service.create("Benfica vs Porto");

        assertThat(event.id()).isNotNull();
        assertThat(event.name()).isEqualTo("Benfica vs Porto");

        final InOrder inOrder = inOrder(repository, publisher);
        inOrder.verify(repository).save(event);
        inOrder.verify(publisher).publish(event);
    }
}

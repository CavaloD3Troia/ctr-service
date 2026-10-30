package org.cavalodetroia.ctr.domain.event;

import org.cavalodetroia.ctr.domain.event.port.in.CreateEvent;
import org.cavalodetroia.ctr.domain.event.port.out.EventPublisher;
import org.cavalodetroia.ctr.domain.event.port.out.EventRepository;

import java.util.UUID;

public class EventService implements CreateEvent {

    private final EventRepository repository;
    private final EventPublisher publisher;

    public EventService(final EventRepository repository, final EventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @Override
    public Event create(final String name) {
        final Event event = new Event(UUID.randomUUID(), name);

        repository.save(event);
        publisher.publish(event);
        return event;
    }
}

package org.cavalodetroia.ctr.domain.event;

import org.cavalodetroia.ctr.domain.event.port.in.CreateEvent;
import org.cavalodetroia.ctr.domain.event.port.in.GetEvent;
import org.cavalodetroia.ctr.domain.event.port.out.EventPublisher;
import org.cavalodetroia.ctr.domain.event.port.out.EventRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

public class EventService implements CreateEvent, GetEvent {

    private final EventRepository repository;
    private final EventPublisher publisher;
    private final Clock clock;

    public EventService(final EventRepository repository, final EventPublisher publisher, final Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    public Event create(final NewEvent newEvent) {
        final Event event = new Event(
                UUID.randomUUID(),
                newEvent.name().strip(),
                newEvent.sport().strip().toUpperCase(Locale.ROOT),
                // Millisecond precision survives every hop: Postgres, protobuf and JSON
                Instant.now(clock).truncatedTo(ChronoUnit.MILLIS));

        repository.save(event);
        publisher.eventCreated(event);
        return event;
    }

    @Override
    public Event byId(final UUID eventId) {
        return repository.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
    }
}

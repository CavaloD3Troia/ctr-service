package org.cavalodetroia.ctr.domain.event.port.out;

import org.cavalodetroia.ctr.domain.event.Event;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository {

    void save(Event event);

    Optional<Event> findById(UUID eventId);
}

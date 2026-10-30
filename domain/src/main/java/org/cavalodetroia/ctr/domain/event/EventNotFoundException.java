package org.cavalodetroia.ctr.domain.event;

import java.util.UUID;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(final UUID eventId) {
        super("Event " + eventId + " not found");
    }
}

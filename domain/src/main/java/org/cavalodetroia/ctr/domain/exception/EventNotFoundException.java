package org.cavalodetroia.ctr.domain.exception;

import java.util.UUID;

/**
 * The event is not known to ctr-service.
 */
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(final UUID eventId) {
        super("Event '" + eventId + "' not found");
    }
}

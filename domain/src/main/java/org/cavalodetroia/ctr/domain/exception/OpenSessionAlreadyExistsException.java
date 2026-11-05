package org.cavalodetroia.ctr.domain.exception;

import java.util.UUID;

/**
 * Another registration opened a session for the event first: two first cameras raced.
 */
public class OpenSessionAlreadyExistsException extends RuntimeException {

    public OpenSessionAlreadyExistsException(final UUID eventId, final Throwable cause) {
        super("Event '" + eventId + "' already has an open upload session", cause);
    }
}

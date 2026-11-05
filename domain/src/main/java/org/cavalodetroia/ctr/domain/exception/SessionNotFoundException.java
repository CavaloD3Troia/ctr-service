package org.cavalodetroia.ctr.domain.exception;

import java.util.UUID;

/**
 * The upload session is not known to ctr-service.
 */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(final UUID sessionId) {
        super("Upload session '" + sessionId + "' not found");
    }
}

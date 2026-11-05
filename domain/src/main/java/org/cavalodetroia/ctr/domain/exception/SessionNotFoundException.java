package org.cavalodetroia.ctr.domain.exception;

import java.util.UUID;

/**
 * No upload session has this id.
 */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(final UUID sessionId) {
        super("Upload session '" + sessionId + "' not found");
    }
}

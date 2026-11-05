package org.cavalodetroia.ctr.domain.exception;

import java.util.UUID;

/**
 * A camera cannot join the session: it is already registered, or the session would end up with neither
 * {@code {SINGLE}} nor {@code {LEFT, RIGHT}}.
 */
public class CameraSetConflictException extends RuntimeException {

    public CameraSetConflictException(final UUID sessionId, final String reason) {
        super("Session '" + sessionId + "': " + reason);
    }
}

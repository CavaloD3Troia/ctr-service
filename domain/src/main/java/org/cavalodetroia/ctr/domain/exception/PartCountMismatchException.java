package org.cavalodetroia.ctr.domain.exception;

import java.util.UUID;

/**
 * A camera joining a session brought a different number of parts than the cameras already registered.
 */
public class PartCountMismatchException extends RuntimeException {

    public PartCountMismatchException(final UUID sessionId, final int expected, final int received) {
        super("Session '" + sessionId + "' has " + expected + " parts per camera, the camera sent " + received);
    }
}

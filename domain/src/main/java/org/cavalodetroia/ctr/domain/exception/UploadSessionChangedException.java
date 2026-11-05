package org.cavalodetroia.ctr.domain.exception;

import java.util.UUID;

/**
 * The session changed between reading it and storing a camera that joined it: another camera joined first, or it is
 * no longer open. Read it again and join again.
 */
public class UploadSessionChangedException extends RuntimeException {

    public UploadSessionChangedException(final UUID sessionId) {
        super("Upload session '" + sessionId + "' changed since it was read");
    }
}

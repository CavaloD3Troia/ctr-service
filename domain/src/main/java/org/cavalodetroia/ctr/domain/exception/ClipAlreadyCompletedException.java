package org.cavalodetroia.ctr.domain.exception;

import org.cavalodetroia.ctr.domain.model.upload.Camera;

import java.util.UUID;

/**
 * A clip already uploaded was confirmed again with another size, etag, start or duration.
 */
public class ClipAlreadyCompletedException extends RuntimeException {

    public ClipAlreadyCompletedException(final UUID sessionId, final Camera camera, final int partIndex) {
        super("Clip " + camera + " " + partIndex + " of upload session '" + sessionId
                + "' is already uploaded with a different confirmation");
    }
}

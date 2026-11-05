package org.cavalodetroia.ctr.domain.exception;

import org.cavalodetroia.ctr.domain.model.upload.Camera;

import java.util.UUID;

/**
 * A clip was confirmed for a camera that is not registered on the session.
 */
public class UnknownCameraException extends RuntimeException {

    public UnknownCameraException(final UUID sessionId, final Camera camera) {
        super("Camera " + camera + " is not registered on upload session '" + sessionId + "'");
    }

    /**
     * @return the request field at fault
     */
    public String field() {
        return "camera";
    }

    /**
     * @return what is wrong with it
     */
    public String reason() {
        return "is not registered on the session";
    }
}

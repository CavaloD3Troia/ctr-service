package org.cavalodetroia.ctr.domain.exception;

import java.util.UUID;

/**
 * A part index outside {@code 0 .. partCount - 1} of the session.
 */
public class PartIndexOutOfRangeException extends RuntimeException {

    private final int partCount;

    public PartIndexOutOfRangeException(final UUID sessionId, final int partIndex, final int partCount) {
        super("Upload session '" + sessionId + "' has parts 0 to " + (partCount - 1) + ", not " + partIndex);
        this.partCount = partCount;
    }

    /**
     * @return the request field at fault
     */
    public String field() {
        return "partIndex";
    }

    /**
     * @return what is wrong with it
     */
    public String reason() {
        return "must be between 0 and " + (partCount - 1);
    }
}

package org.cavalodetroia.ctr.domain.model.upload;

import java.util.Objects;

/**
 * What a camera tells about a clip it has just uploaded: what S3 answered to its {@code PUT}, and where the clip
 * sits in the recording, which only the camera knows because clips have variable length.
 *
 * @param sizeBytes  the size of the object, positive
 * @param etag       the etag S3 gave the object, not blank, with or without its surrounding double quotes
 * @param startMs    where the clip starts in the recording, in milliseconds, zero or more
 * @param durationMs how long the clip is, in milliseconds, positive
 */
public record ClipConfirmation(long sizeBytes, String etag, long startMs, long durationMs) {

    public ClipConfirmation {
        Objects.requireNonNull(etag, "etag");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive, was " + sizeBytes);
        }
        if (etag.isBlank()) {
            throw new IllegalArgumentException("etag must not be blank");
        }
        if (startMs < 0) {
            throw new IllegalArgumentException("startMs must not be negative, was " + startMs);
        }
        if (durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be positive, was " + durationMs);
        }
    }

    /**
     * @return the stretch of the recording the clip covers
     */
    public Window window() {
        return new Window(startMs, durationMs);
    }
}

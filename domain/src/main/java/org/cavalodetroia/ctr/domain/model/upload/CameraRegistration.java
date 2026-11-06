package org.cavalodetroia.ctr.domain.model.upload;

import org.cavalodetroia.ctr.domain.model.venue.RequestedVenue;

import java.util.Objects;

/**
 * A camera asking to upload the clips of an event.
 *
 * @param camera      which camera this is
 * @param partCount   how many parts the match is cut into, between {@value #MIN_PART_COUNT} and {@value #MAX_PART_COUNT}
 * @param contentType the media type of its clips, for example {@code video/mp4}; published with each clip
 * @param venue       the venue as the camera sent it, or {@code null} when it sent none (only the first camera of a
 *                    session must)
 */
public record CameraRegistration(Camera camera, int partCount, String contentType, RequestedVenue venue) {

    public static final int MIN_PART_COUNT = 1;
    public static final int MAX_PART_COUNT = 1000;

    public CameraRegistration {
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(contentType, "contentType");
        if (partCount < MIN_PART_COUNT || partCount > MAX_PART_COUNT) {
            throw new IllegalArgumentException("partCount must be between " + MIN_PART_COUNT + " and "
                    + MAX_PART_COUNT + ", was " + partCount);
        }
        if (contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
    }

    public boolean hasVenue() {
        return venue != null;
    }
}

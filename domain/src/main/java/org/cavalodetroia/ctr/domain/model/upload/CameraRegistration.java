package org.cavalodetroia.ctr.domain.model.upload;

import org.cavalodetroia.ctr.domain.model.venue.RequestedVenue;

import java.util.Objects;

/**
 * A camera asking to upload the clips of an event.
 *
 * @param camera    which camera this is
 * @param partCount how many parts the match is cut into, between {@value #MIN_PART_COUNT} and {@value #MAX_PART_COUNT}
 * @param venue     the venue as the camera sent it, or {@code null} when it sent none (only the first camera of a
 *                  session must)
 */
public record CameraRegistration(Camera camera, int partCount, RequestedVenue venue) {

    public static final int MIN_PART_COUNT = 1;
    public static final int MAX_PART_COUNT = 1000;

    public CameraRegistration {
        Objects.requireNonNull(camera, "camera");
        if (partCount < MIN_PART_COUNT || partCount > MAX_PART_COUNT) {
            throw new IllegalArgumentException("partCount must be between " + MIN_PART_COUNT + " and "
                    + MAX_PART_COUNT + ", was " + partCount);
        }
    }

    public boolean hasVenue() {
        return venue != null;
    }
}

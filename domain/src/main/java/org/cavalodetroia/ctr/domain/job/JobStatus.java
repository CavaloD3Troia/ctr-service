package org.cavalodetroia.ctr.domain.job;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Where the processing pipeline is with one event.
 */
public record JobStatus(UUID eventId, JobState state, Instant updatedAt) {

    public JobStatus {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public boolean isNewerThan(final JobStatus other) {
        return updatedAt.isAfter(other.updatedAt());
    }
}

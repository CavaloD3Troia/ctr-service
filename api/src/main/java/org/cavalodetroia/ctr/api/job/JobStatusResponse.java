package org.cavalodetroia.ctr.api.job;

import java.time.Instant;
import java.util.UUID;

public record JobStatusResponse(UUID eventId, String state, Instant updatedAt) {
}

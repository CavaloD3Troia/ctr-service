package org.cavalodetroia.ctr.api.event;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(UUID id, String name, String sport, Instant createdAt) {
}

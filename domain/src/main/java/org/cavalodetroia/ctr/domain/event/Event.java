package org.cavalodetroia.ctr.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One occurrence of a sport: a football match, a tennis game, a race.
 */
public record Event(UUID id, String name, String sport, Instant createdAt) {

    public Event {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sport, "sport");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}

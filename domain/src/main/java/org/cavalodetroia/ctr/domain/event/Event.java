package org.cavalodetroia.ctr.domain.event;

import java.util.Objects;
import java.util.UUID;

/**
 * One occurrence of a sport: a football match, a tennis game, a race.
 */
public record Event(UUID id, String name) {

    public Event {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }
}

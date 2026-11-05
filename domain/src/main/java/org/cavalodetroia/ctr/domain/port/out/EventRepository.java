package org.cavalodetroia.ctr.domain.port.out;

import java.util.UUID;

/**
 * The events ctr-service knows about. Only existence is needed until the Event entity arrives (ADR-0002).
 */
public interface EventRepository {

    /**
     * @param id the event id
     * @return whether an event with this id exists
     */
    boolean exists(UUID id);
}

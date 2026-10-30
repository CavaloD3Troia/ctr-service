package org.cavalodetroia.ctr.domain.event.port.out;

import org.cavalodetroia.ctr.domain.event.Event;

/**
 * Tells the rest of the platform what happened to an event.
 */
public interface EventPublisher {

    void eventCreated(Event event);
}

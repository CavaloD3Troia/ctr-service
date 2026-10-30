package org.cavalodetroia.ctr.domain.event.port.out;

import org.cavalodetroia.ctr.domain.event.Event;

/**
 * Tells the rest of the platform that an event was created.
 */
public interface EventPublisher {

    void publish(Event event);
}

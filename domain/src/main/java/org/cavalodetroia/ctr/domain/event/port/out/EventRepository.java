package org.cavalodetroia.ctr.domain.event.port.out;

import org.cavalodetroia.ctr.domain.event.Event;

public interface EventRepository {

    void save(Event event);
}

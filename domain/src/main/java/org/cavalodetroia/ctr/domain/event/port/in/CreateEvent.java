package org.cavalodetroia.ctr.domain.event.port.in;

import org.cavalodetroia.ctr.domain.event.Event;

public interface CreateEvent {

    Event create(String name);
}

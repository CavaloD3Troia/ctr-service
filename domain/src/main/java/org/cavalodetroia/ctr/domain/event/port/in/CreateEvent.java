package org.cavalodetroia.ctr.domain.event.port.in;

import org.cavalodetroia.ctr.domain.event.Event;
import org.cavalodetroia.ctr.domain.event.NewEvent;

public interface CreateEvent {

    Event create(NewEvent newEvent);
}

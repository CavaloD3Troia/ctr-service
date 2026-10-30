package org.cavalodetroia.ctr.domain.event.port.in;

import org.cavalodetroia.ctr.domain.event.Event;
import org.cavalodetroia.ctr.domain.event.EventNotFoundException;

import java.util.UUID;

public interface GetEvent {

    /**
     * @throws EventNotFoundException when there is no event with that id
     */
    Event byId(UUID eventId);
}

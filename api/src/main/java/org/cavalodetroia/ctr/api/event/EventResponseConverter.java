package org.cavalodetroia.ctr.api.event;

import org.cavalodetroia.ctr.domain.event.Event;
import org.springframework.stereotype.Component;

@Component
public class EventResponseConverter {

    public EventResponse toResponse(final Event event) {
        return new EventResponse(event.id(), event.name(), event.sport(), event.createdAt());
    }
}

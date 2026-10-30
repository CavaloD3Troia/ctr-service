package org.cavalodetroia.ctr.api.event;

import org.cavalodetroia.ctr.domain.event.NewEvent;
import org.springframework.stereotype.Component;

@Component
public class EventRequestConverter {

    public NewEvent toDomain(final CreateEventRequest request) {
        return new NewEvent(request.name(), request.sport());
    }
}

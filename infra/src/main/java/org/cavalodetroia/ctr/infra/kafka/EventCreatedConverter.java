package org.cavalodetroia.ctr.infra.kafka;

import org.cavalodetroia.contracts.ctr.v1.EventCreated;
import org.cavalodetroia.ctr.domain.event.Event;
import org.springframework.stereotype.Component;

@Component
public class EventCreatedConverter {

    public EventCreated toMessage(final Event event) {
        return EventCreated.newBuilder()
                .setEventId(event.id().toString())
                .setName(event.name())
                .build();
    }
}

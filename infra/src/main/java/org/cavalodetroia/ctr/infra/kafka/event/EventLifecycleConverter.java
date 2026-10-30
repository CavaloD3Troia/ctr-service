package org.cavalodetroia.ctr.infra.kafka.event;

import org.cavalodetroia.contracts.ctr.v1.EventCreated;
import org.cavalodetroia.contracts.ctr.v1.EventLifecycle;
import org.cavalodetroia.ctr.domain.event.Event;
import org.cavalodetroia.ctr.infra.kafka.ProtoTimestamps;

public class EventLifecycleConverter {

    public EventLifecycle toEventCreated(final Event event) {
        return EventLifecycle.newBuilder()
                .setEventId(event.id().toString())
                .setOccurredAt(ProtoTimestamps.from(event.createdAt()))
                .setEventCreated(EventCreated.newBuilder()
                        .setName(event.name())
                        .setSport(event.sport()))
                .build();
    }
}

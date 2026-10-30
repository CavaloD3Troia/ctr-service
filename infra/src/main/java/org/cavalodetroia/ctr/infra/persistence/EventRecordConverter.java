package org.cavalodetroia.ctr.infra.persistence;

import org.cavalodetroia.ctr.domain.event.Event;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.EventRecord;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

@Component
public class EventRecordConverter {

    public EventRecord toRecord(final Event event) {
        return new EventRecord(event.id(), event.name(), event.sport(), event.createdAt().atOffset(ZoneOffset.UTC));
    }

    public Event toDomain(final EventRecord record) {
        return new Event(record.getId(), record.getName(), record.getSport(), record.getCreatedAt().toInstant());
    }
}

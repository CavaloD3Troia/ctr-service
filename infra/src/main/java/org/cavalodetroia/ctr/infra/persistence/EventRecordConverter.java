package org.cavalodetroia.ctr.infra.persistence;

import org.cavalodetroia.ctr.domain.event.Event;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.EventRecord;
import org.springframework.stereotype.Component;

@Component
public class EventRecordConverter {

    public EventRecord toRecord(final Event event) {
        return new EventRecord(event.id(), event.name());
    }
}

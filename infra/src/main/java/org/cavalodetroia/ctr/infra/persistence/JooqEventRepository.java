package org.cavalodetroia.ctr.infra.persistence;

import org.cavalodetroia.ctr.domain.event.Event;
import org.cavalodetroia.ctr.domain.event.port.out.EventRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqEventRepository implements EventRepository {

    private final DSLContext dsl;
    private final EventRecordConverter converter;

    public JooqEventRepository(final DSLContext dsl, final EventRecordConverter converter) {
        this.dsl = dsl;
        this.converter = converter;
    }

    @Override
    public void save(final Event event) {
        dsl.executeInsert(converter.toRecord(event));
    }
}

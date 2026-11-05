package org.cavalodetroia.ctr.infra.repository;

import org.cavalodetroia.ctr.domain.port.out.EventRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.EVENT;

@Repository
public class JooqEventRepository implements EventRepository {

    private final DSLContext dsl;

    public JooqEventRepository(final DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean exists(final UUID id) {
        return dsl.fetchExists(EVENT, EVENT.ID.eq(id));
    }
}

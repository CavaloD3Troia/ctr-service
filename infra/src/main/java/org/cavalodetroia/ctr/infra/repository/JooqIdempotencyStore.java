package org.cavalodetroia.ctr.infra.repository;

import org.cavalodetroia.ctr.domain.model.idempotency.StoredResponse;
import org.cavalodetroia.ctr.domain.port.out.IdempotencyStore;
import org.cavalodetroia.ctr.infra.converter.IdempotencyRecordConverter;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.IdempotencyKeyRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.IDEMPOTENCY_KEY;

@Repository
public class JooqIdempotencyStore implements IdempotencyStore {

    private static final Duration RETENTION = Duration.ofHours(24);

    private final DSLContext dsl;
    private final IdempotencyRecordConverter converter;
    private final Clock clock;

    public JooqIdempotencyStore(final DSLContext dsl, final IdempotencyRecordConverter converter, final Clock clock) {
        this.dsl = dsl;
        this.converter = converter;
        this.clock = clock;
    }

    @Override
    public Optional<StoredResponse> find(final String key) {
        return dsl.selectFrom(IDEMPOTENCY_KEY)
                .where(IDEMPOTENCY_KEY.KEY.eq(key))
                .and(IDEMPOTENCY_KEY.CREATED_AT.gt(expiredUpTo(clock.instant())))
                .fetchOptional()
                .map(converter::toDomain);
    }

    @Override
    public void save(final String key, final StoredResponse response) {
        final Instant now = clock.instant();
        final IdempotencyKeyRecord record = converter.toRecord(key, response, now);
        // A key past its retention is free again; a live one keeps the response it already has
        dsl.insertInto(IDEMPOTENCY_KEY)
                .set(record)
                .onConflict(IDEMPOTENCY_KEY.KEY)
                .doUpdate()
                .set(record)
                .where(IDEMPOTENCY_KEY.CREATED_AT.le(expiredUpTo(now)))
                .execute();
    }

    private static OffsetDateTime expiredUpTo(final Instant now) {
        return now.minus(RETENTION).atOffset(ZoneOffset.UTC);
    }
}

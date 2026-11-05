package org.cavalodetroia.ctr.infra.converter;

import org.cavalodetroia.ctr.domain.model.idempotency.StoredResponse;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.IdempotencyKeyRecord;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * A stored response and its {@code idempotency_key} row.
 */
@Component
public class IdempotencyRecordConverter {

    public IdempotencyKeyRecord toRecord(final String key, final StoredResponse response, final Instant createdAt) {
        final IdempotencyKeyRecord record = new IdempotencyKeyRecord();
        record.setKey(key);
        record.setRequestHash(response.requestHash());
        record.setResponseStatus(response.status());
        record.setResponseBody(response.body());
        record.setCreatedAt(createdAt.atOffset(ZoneOffset.UTC));
        return record;
    }

    public StoredResponse toDomain(final IdempotencyKeyRecord record) {
        return new StoredResponse(record.getRequestHash(), record.getResponseStatus(), record.getResponseBody());
    }
}

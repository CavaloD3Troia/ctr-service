package org.cavalodetroia.ctr.infra.converter;

import org.cavalodetroia.ctr.domain.model.idempotency.StoredResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-7: the stored response of an Idempotency-Key, to its row and back.
 */
class IdempotencyRecordConverterTest {

    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");

    private final IdempotencyRecordConverter converter = new IdempotencyRecordConverter();

    @Test
    void should_storeKeyHashStatusBodyAndTime_when_convertingToRecord() {
        final var record = converter.toRecord("9b1d", new StoredResponse("hash", 201, "{\"a\":1}"), NOW);

        assertThat(record.getKey()).isEqualTo("9b1d");
        assertThat(record.getRequestHash()).isEqualTo("hash");
        assertThat(record.getResponseStatus()).isEqualTo(201);
        assertThat(record.getResponseBody()).isEqualTo("{\"a\":1}");
        assertThat(record.getCreatedAt()).isEqualTo(OffsetDateTime.of(2026, 11, 5, 10, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void should_giveBackTheSameResponse_when_roundTripping() {
        final StoredResponse response = new StoredResponse("hash", 201, "{\"a\":1}");

        assertThat(converter.toDomain(converter.toRecord("9b1d", response, NOW))).isEqualTo(response);
    }
}

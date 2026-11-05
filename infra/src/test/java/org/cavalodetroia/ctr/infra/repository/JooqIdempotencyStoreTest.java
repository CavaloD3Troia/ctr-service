package org.cavalodetroia.ctr.infra.repository;

import org.cavalodetroia.ctr.domain.model.idempotency.StoredResponse;
import org.cavalodetroia.ctr.infra.converter.IdempotencyRecordConverter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.IDEMPOTENCY_KEY;

/**
 * The 24-hour memory of an {@code Idempotency-Key}, as the statements sent to Postgres through jOOQ's mock JDBC
 * connection: a read ignores rows past the retention, and a write only overwrites one past it. The component tests
 * prove the replay against a real Postgres.
 */
class JooqIdempotencyStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");
    // Timestamps as jOOQ binds them for Postgres: text, with the offset
    private static final String NOW_BOUND = "2026-11-05 10:00:00+00:00";
    private static final String A_DAY_AGO_BOUND = "2026-11-04 10:00:00+00:00";
    private static final StoredResponse RESPONSE = new StoredResponse("hash", 201, "{\"sessionId\":\"019a\"}");

    private final MockPostgres postgres = new MockPostgres(IDEMPOTENCY_KEY);
    private final IdempotencyRecordConverter converter = new IdempotencyRecordConverter();
    private final JooqIdempotencyStore store =
            new JooqIdempotencyStore(postgres.dsl, converter, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void should_giveTheStoredResponse_when_theKeyHasALiveRow() {
        postgres.rows(IDEMPOTENCY_KEY, List.of(converter.toRecord("9b1d", RESPONSE, NOW.minusSeconds(60))));

        assertThat(store.find("9b1d")).contains(RESPONSE);
    }

    @Test
    void should_askOnlyForRowsYoungerThanADay_when_finding() {
        store.find("9b1d");

        assertThat(postgres.statements).singleElement().satisfies(statement -> {
            assertThat(statement.sql()).containsIgnoringCase("from \"idempotency_key\"")
                    .containsIgnoringCase("\"created_at\" > ");
            assertThat(statement.bindings()).containsExactly("9b1d", A_DAY_AGO_BOUND);
        });
    }

    @Test
    void should_beEmpty_when_theKeyHasNoLiveRow() {
        assertThat(store.find("unknown")).isEmpty();
    }

    @Test
    void should_insertTheResponseAndOverwriteOnlyARowPastRetention_when_saving() {
        store.save("9b1d", RESPONSE);

        assertThat(postgres.statements).singleElement().satisfies(statement -> {
            assertThat(statement.sql()).containsIgnoringCase("insert into \"idempotency_key\"")
                    .containsIgnoringCase("on conflict (\"key\") do update")
                    .containsIgnoringCase("\"created_at\" <= ");
            assertThat(List.of(statement.bindings()))
                    .contains("9b1d", "hash", 201, "{\"sessionId\":\"019a\"}", NOW_BOUND)
                    .last().isEqualTo(A_DAY_AGO_BOUND);
        });
    }
}

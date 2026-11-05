package org.cavalodetroia.ctr.infra.repository;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whether an event exists, asked of Postgres through jOOQ's mock JDBC connection: the query names the id, and the
 * answer is whether a row came back. The component tests prove it against a real Postgres.
 */
class JooqEventRepositoryTest {

    private static final UUID EVENT_ID = UUID.fromString("0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11");
    private static final DSLContext RECORDS = DSL.using(SQLDialect.POSTGRES);

    private final List<MockExecuteContext> statements = new ArrayList<>();

    @Test
    void should_beTrue_when_theEventHasARow() {
        final JooqEventRepository repository = new JooqEventRepository(postgresAnswering(true));

        assertThat(repository.exists(EVENT_ID)).isTrue();
        assertThat(statements).singleElement().satisfies(statement -> {
            assertThat(statement.sql()).containsIgnoringCase("exists").containsIgnoringCase("from \"event\"");
            assertThat(statement.bindings()).containsExactly(EVENT_ID);
        });
    }

    @Test
    void should_beFalse_when_theEventHasNoRow() {
        final JooqEventRepository repository = new JooqEventRepository(postgresAnswering(false));

        assertThat(repository.exists(EVENT_ID)).isFalse();
    }

    /**
     * @param exists what Postgres answers to jOOQ's {@code select exists (select 1 from "event" where ...)}
     */
    private DSLContext postgresAnswering(final boolean exists) {
        return DSL.using(new MockConnection(context -> {
            statements.add(context);
            final Field<Boolean> answer = DSL.field("exists", Boolean.class);
            final Result<Record1<Boolean>> result = RECORDS.newResult(answer);
            result.add(RECORDS.newRecord(answer).values(exists));
            return new MockResult[]{new MockResult(1, result)};
        }), SQLDialect.POSTGRES);
    }
}

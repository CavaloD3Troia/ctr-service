package org.cavalodetroia.ctr.infra.persistence;

import org.cavalodetroia.ctr.domain.event.Event;
import org.jooq.DSLContext;
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
 * jOOQ's mock JDBC connection records what the repository would send to Postgres, so the statement and its bind
 * values are checked without a database. The component tests prove it against a real one.
 */
class JooqEventRepositoryTest {

    private final List<MockExecuteContext> executed = new ArrayList<>();

    private final DSLContext dsl = DSL.using(new MockConnection(context -> {
        executed.add(context);
        return new MockResult[]{new MockResult(1)};
    }), SQLDialect.POSTGRES);

    private final JooqEventRepository repository = new JooqEventRepository(dsl, new EventRecordConverter());

    @Test
    void should_insertIdAndNameIntoTheEventTable_when_saving() {
        final Event event = new Event(UUID.randomUUID(), "Benfica vs Porto");

        repository.save(event);

        assertThat(executed).singleElement().satisfies(statement -> {
            assertThat(statement.sql()).isEqualToIgnoringCase("insert into \"event\" (\"id\", \"name\") values (cast(? as uuid), ?)");
            assertThat(statement.bindings()).containsExactly(event.id(), "Benfica vs Porto");
        });
    }
}

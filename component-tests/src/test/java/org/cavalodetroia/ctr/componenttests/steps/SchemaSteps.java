package org.cavalodetroia.ctr.componenttests.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.EVENT;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_SESSION;

/**
 * Checks what Liquibase left in the database when the app started, that running the changelog again (the same
 * {@link SpringLiquibase} bean the app ran on start) changes nothing, and the one-open-session-per-event index.
 */
public class SchemaSteps {

    private static final String APPLIED_CHANGESETS =
            "select id, author, filename, exectype, dateexecuted from databasechangelog order by orderexecuted";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-05T10:00:00Z");

    private final DSLContext dsl;
    private final SpringLiquibase liquibase;
    // A new event per scenario, so the scenarios don't depend on each other or on their order
    private final UUID eventId = UUID.randomUUID();

    private Result<Record> appliedBefore;
    private UUID openSessionId;
    private UUID newSessionId;
    private Throwable rejection;

    public SchemaSteps(final DSLContext dsl, final SpringLiquibase liquibase) {
        this.dsl = dsl;
        this.liquibase = liquibase;
    }

    @Then("these changesets are applied, in order")
    public void theseChangesetsAreAppliedInOrder(final List<String> authorAndIds) {
        assertThat(dsl.fetch("select author || ':' || id as changeset from databasechangelog"
                        + " where exectype = 'EXECUTED' order by orderexecuted")
                .getValues("changeset", String.class))
                .containsExactlyElementsOf(authorAndIds);
    }

    @Then("these tables exist")
    public void theseTablesExist(final List<String> tables) {
        assertThat(dsl.fetch("select table_name from information_schema.tables where table_schema = current_schema()")
                .getValues("table_name", String.class))
                .containsAll(tables);
    }

    @Then("these tables have these primary keys")
    public void theseTablesHaveThesePrimaryKeys(final DataTable primaryKeys) {
        primaryKeys.asMap().forEach((table, columns) -> assertThat(dsl.fetch("""
                        select kcu.column_name
                        from information_schema.table_constraints tc
                        join information_schema.key_column_usage kcu
                          on kcu.constraint_schema = tc.constraint_schema and kcu.constraint_name = tc.constraint_name
                        where tc.constraint_type = 'PRIMARY KEY'
                          and tc.table_schema = current_schema() and tc.table_name = ?
                        order by kcu.ordinal_position""", table)
                .getValues("column_name", String.class))
                .as("primary key of %s", table)
                .containsExactlyElementsOf(Arrays.asList(columns.split(",\\s*"))));
    }

    @Then("these tables have these foreign keys")
    public void theseTablesHaveTheseForeignKeys(final DataTable foreignKeys) {
        for (final List<String> foreignKey : foreignKeys.asLists()) {
            final String table = foreignKey.get(0);
            assertThat(dsl.fetch("""
                            select kcu.column_name || ' -> ' || ccu.table_name as foreign_key
                            from information_schema.table_constraints tc
                            join information_schema.key_column_usage kcu
                              on kcu.constraint_schema = tc.constraint_schema and kcu.constraint_name = tc.constraint_name
                            join information_schema.constraint_column_usage ccu
                              on ccu.constraint_schema = tc.constraint_schema and ccu.constraint_name = tc.constraint_name
                            where tc.constraint_type = 'FOREIGN KEY'
                              and tc.table_schema = current_schema() and tc.table_name = ?""", table)
                    .getValues("foreign_key", String.class))
                    .as("foreign keys of %s", table)
                    .containsExactly(foreignKey.get(1) + " -> " + foreignKey.get(2));
        }
    }

    @Then("the index {string} is")
    public void theIndexIs(final String index, final String definition) {
        assertThat(dsl.fetchValue("select indexdef from pg_indexes where schemaname = current_schema() and indexname = ?",
                index))
                .isEqualTo(definition);
    }

    @When("the changelog runs again")
    public void theChangelogRunsAgain() throws LiquibaseException {
        appliedBefore = dsl.fetch(APPLIED_CHANGESETS);
        liquibase.afterPropertiesSet();
    }

    @Then("no changeset is applied again")
    public void noChangesetIsAppliedAgain() {
        // Same rows with the same execution time: nothing was added and nothing ran a second time
        assertThat(dsl.fetch(APPLIED_CHANGESETS)).isNotEmpty().isEqualTo(appliedBefore);
    }

    @Given("an event with an open upload session")
    public void anEventWithAnOpenUploadSession() {
        dsl.insertInto(EVENT).set(EVENT.ID, eventId).set(EVENT.NAME, "Benfica vs Porto").execute();
        openSessionId = UUID.randomUUID();
        insertOpenSession(openSessionId);
    }

    @When("another open upload session is created for the event")
    public void anotherOpenUploadSessionIsCreatedForTheEvent() {
        // A new id, so only the index can reject it, never the primary key
        newSessionId = UUID.randomUUID();
        rejection = catchThrowable(() -> insertOpenSession(newSessionId));
    }

    @Then("it is rejected by the index {string}")
    public void itIsRejectedByTheIndex(final String index) {
        assertThat(rejection)
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, cause -> {
                    assertThat(cause.getSQLState()).isEqualTo(UNIQUE_VIOLATION);
                    assertThat(cause.getMessage()).contains(index);
                });
        assertThat(dsl.fetchExists(UPLOAD_SESSION, UPLOAD_SESSION.ID.eq(newSessionId))).isFalse();
    }

    @When("the open upload session is completed")
    public void theOpenUploadSessionIsCompleted() {
        dsl.update(UPLOAD_SESSION)
                .set(UPLOAD_SESSION.STATUS, "COMPLETED")
                .where(UPLOAD_SESSION.ID.eq(openSessionId))
                .execute();
    }

    @Then("it is stored next to the completed one")
    public void itIsStoredNextToTheCompletedOne() {
        assertThat(rejection).isNull();
        assertThat(dsl.select(UPLOAD_SESSION.ID, UPLOAD_SESSION.STATUS)
                .from(UPLOAD_SESSION)
                .where(UPLOAD_SESSION.EVENT_ID.eq(eventId))
                .fetchMap(UPLOAD_SESSION.ID, UPLOAD_SESSION.STATUS))
                .containsOnly(entry(openSessionId, "COMPLETED"), entry(newSessionId, "OPEN"));
    }

    private void insertOpenSession(final UUID sessionId) {
        dsl.insertInto(UPLOAD_SESSION)
                .set(UPLOAD_SESSION.ID, sessionId)
                .set(UPLOAD_SESSION.EVENT_ID, eventId)
                .set(UPLOAD_SESSION.STATUS, "OPEN")
                .set(UPLOAD_SESSION.VENUE_FORMAT, "FUTSAL_5V5")
                .set(UPLOAD_SESSION.VENUE_LENGTH_M, 40f)
                .set(UPLOAD_SESSION.VENUE_WIDTH_M, 20f)
                .set(UPLOAD_SESSION.CREATED_AT, NOW)
                .set(UPLOAD_SESSION.UPDATED_AT, NOW)
                .execute();
    }
}

package org.cavalodetroia.ctr.infra.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * jOOQ's mock JDBC connection standing in for Postgres: records every statement a repository sends, answers a
 * {@code SELECT} on a table with the rows given for it (none unless said otherwise) and a write with the rows it
 * affected (one unless said otherwise), or fails every statement the way the driver would. Transactions, locks and
 * batches go through as statements. The component tests prove the same statements against a real Postgres.
 */
final class MockPostgres implements MockDataProvider {

    private static final Pattern TABLE = Pattern.compile("(?:from|into|update)\\s+\"(\\w+)\"");

    final List<MockExecuteContext> statements = new ArrayList<>();
    final DSLContext dsl = DSL.using(new MockConnection(this), SQLDialect.POSTGRES);

    private final Map<String, Result<?>> rows = new HashMap<>();
    private final Map<String, Integer> affected = new HashMap<>();
    private SQLException failure;

    /**
     * @param tables the tables the repository under test may read; each starts empty
     */
    MockPostgres(final Table<?>... tables) {
        for (final Table<?> table : tables) {
            rows.put(table.getName(), dsl.newResult(table));
        }
    }

    <R extends Record> void rows(final Table<R> table, final List<R> records) {
        final Result<R> result = dsl.newResult(table);
        result.addAll(records);
        rows.put(table.getName(), result);
    }

    void affects(final Table<?> table, final int count) {
        affected.put(table.getName(), count);
    }

    void failsWith(final SQLException failure) {
        this.failure = failure;
    }

    /** The statements sent, in order; a batch is its statement once, however many rows it carried. */
    List<String> sql() {
        return statements.stream().map(MockExecuteContext::sql).toList();
    }

    @Override
    public MockResult[] execute(final MockExecuteContext context) throws SQLException {
        statements.add(context);
        if (failure != null) {
            throw failure;
        }
        if (context.batch()) {
            final int size = context.batchSingle() ? context.batchBindings().length : context.batchSQL().length;
            final MockResult[] results = new MockResult[size];
            Arrays.fill(results, new MockResult(1));
            return results;
        }
        final String table = tableOf(context.sql());
        if (context.sql().toLowerCase(Locale.ROOT).startsWith("select")) {
            final Result<?> result = Objects.requireNonNull(rows.get(table), () -> "Not a table of this mock: " + table);
            return new MockResult[]{new MockResult(result.size(), result)};
        }
        return new MockResult[]{new MockResult(affected.getOrDefault(table, 1))};
    }

    private static String tableOf(final String sql) {
        final Matcher matcher = TABLE.matcher(sql);
        if (!matcher.find()) {
            throw new IllegalArgumentException("No table in: " + sql);
        }
        return matcher.group(1);
    }
}

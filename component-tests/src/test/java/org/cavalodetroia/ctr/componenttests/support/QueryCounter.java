package org.cavalodetroia.ctr.componenttests.support;

import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.ExecuteListenerProvider;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Every SQL statement the app runs through jOOQ, so a scenario can check how many a request needed and that it
 * only read. Scenarios run one at a time, so {@link #reset()} before the request leaves only the request's statements.
 */
@TestConfiguration(proxyBeanMethods = false)
public class QueryCounter {

    // The request runs on a server thread, the scenario reads on its own
    private final List<String> statements = new CopyOnWriteArrayList<>();

    @Bean
    ExecuteListenerProvider statementRecorder() {
        return new DefaultExecuteListenerProvider(new ExecuteListener() {
            @Override
            public void executeStart(final ExecuteContext context) {
                statements.add(context.sql());
            }
        });
    }

    public void reset() {
        statements.clear();
    }

    /**
     * @return the SQL of every statement since the last {@link #reset()}, oldest first
     */
    public List<String> statements() {
        return List.copyOf(statements);
    }
}

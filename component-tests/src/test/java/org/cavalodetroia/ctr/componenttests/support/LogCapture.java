package org.cavalodetroia.ctr.componenttests.support;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Every log event the app writes while it is started, with its MDC, from every logger (the root one).
 */
public final class LogCapture {

    private final Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    public void start() {
        appender.start();
        root.addAppender(appender);
    }

    public void stop() {
        root.detachAppender(appender);
        appender.stop();
    }

    /**
     * @return the events captured so far, oldest first
     */
    public List<ILoggingEvent> events() {
        // Other threads append too; the appender synchronizes on itself while doing so
        synchronized (appender) {
            return List.copyOf(appender.list);
        }
    }
}

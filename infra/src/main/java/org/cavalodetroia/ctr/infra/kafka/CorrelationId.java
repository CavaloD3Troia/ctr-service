package org.cavalodetroia.ctr.infra.kafka;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Moves the correlation id between the MDC and Kafka record headers, so one id follows a request across services.
 */
public final class CorrelationId {

    public static final String MDC_KEY = "correlationId";
    public static final String HEADER = "correlationId";

    private CorrelationId() {
    }

    /**
     * Uses the record's header when there is one, otherwise a new id.
     */
    public static void putInMdc(final Headers headers) {
        final Header header = headers.lastHeader(HEADER);
        final String correlationId = header != null
                ? new String(header.value(), StandardCharsets.UTF_8)
                : "ctr-service-" + UUID.randomUUID();
        MDC.put(MDC_KEY, correlationId);
    }

    public static List<Header> fromMdc() {
        final String correlationId = MDC.get(MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            return List.of();
        }
        return List.of(new RecordHeader(HEADER, correlationId.getBytes(StandardCharsets.UTF_8)));
    }
}

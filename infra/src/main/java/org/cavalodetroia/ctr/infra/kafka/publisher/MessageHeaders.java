package org.cavalodetroia.ctr.infra.kafka.publisher;

import com.google.protobuf.Message;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.cavalodetroia.ctr.domain.common.Uuid7;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The headers every message of ctr-service carries (ADR-0001 D29), as UTF-8 bytes: {@code message_id} a new UUIDv7,
 * {@code message_type} the full protobuf name of the payload (for example {@code cavalodetroia.clip.v1.ClipReady}),
 * {@code occurred_at} the time of publishing in ISO-8601 UTC ending in {@code Z}, {@code producer} always
 * {@code ctr-service}, and {@code correlation_id} the one of the request, from the MDC.
 */
@Component
public class MessageHeaders {

    static final String MESSAGE_ID = "message_id";
    static final String MESSAGE_TYPE = "message_type";
    static final String OCCURRED_AT = "occurred_at";
    static final String PRODUCER = "producer";
    static final String CORRELATION_ID = "correlation_id";
    static final String PRODUCER_NAME = "ctr-service";
    // Where api's CorrelationIdFilter puts the request's correlation id; infra can't import api, so it is repeated
    static final String MDC_CORRELATION_ID = "correlation_id";

    private final Clock clock;

    public MessageHeaders(final Clock clock) {
        this.clock = clock;
    }

    /**
     * @param payload the message the headers go with
     * @return the five headers, in a fixed order. Outside a request, which has none, the correlation id is a new
     *         {@code CTR-<uuidv7>}, the form the HTTP filter generates
     */
    public List<Header> of(final Message payload) {
        final String correlationId = MDC.get(MDC_CORRELATION_ID);
        return List.of(
                header(MESSAGE_ID, Uuid7.generate(clock).toString()),
                header(MESSAGE_TYPE, payload.getDescriptorForType().getFullName()),
                header(OCCURRED_AT, DateTimeFormatter.ISO_INSTANT.format(clock.instant())),
                header(PRODUCER, PRODUCER_NAME),
                header(CORRELATION_ID, correlationId != null ? correlationId : "CTR-" + Uuid7.generate(clock)));
    }

    private static Header header(final String key, final String value) {
        return new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8));
    }
}

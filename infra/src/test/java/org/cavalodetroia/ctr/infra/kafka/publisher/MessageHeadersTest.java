package org.cavalodetroia.ctr.infra.kafka.publisher;

import org.apache.kafka.common.header.Header;
import org.cavalodetroia.contracts.clip.v1.ClipPairUploaded;
import org.cavalodetroia.contracts.clip.v1.ClipReady;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TH-152 AC-3: the five headers of every message, as UTF-8 bytes.
 */
class MessageHeadersTest {

    private static final Instant NOW = Instant.parse("2026-11-05T11:00:00.123Z");

    private final MessageHeaders headers = new MessageHeaders(Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void should_carryTheFiveHeadersWithTheRequestsCorrelationId_when_buildingThem() {
        MDC.put("correlation_id", "gui-7f3c:retry.1");

        final Map<String, String> values = asText(headers.of(ClipPairUploaded.getDefaultInstance()));

        assertThat(values).containsOnlyKeys("message_id", "message_type", "occurred_at", "producer", "correlation_id");
        assertThat(values).containsEntry("message_type", "cavalodetroia.clip.v1.ClipPairUploaded")
                .containsEntry("occurred_at", "2026-11-05T11:00:00.123Z")
                .containsEntry("producer", "ctr-service")
                .containsEntry("correlation_id", "gui-7f3c:retry.1");
        final UUID messageId = UUID.fromString(values.get("message_id"));
        assertThat(messageId.version()).isEqualTo(7);
        assertThat(messageId.getMostSignificantBits() >>> 16).isEqualTo(NOW.toEpochMilli());
    }

    @Test
    void should_nameTheClipReadyType_when_thePayloadIsAClipReady() {
        assertThat(asText(headers.of(ClipReady.getDefaultInstance())))
                .containsEntry("message_type", "cavalodetroia.clip.v1.ClipReady");
    }

    @Test
    void should_giveEachMessageItsOwnId_when_buildingHeadersTwice() {
        assertThat(asText(headers.of(ClipReady.getDefaultInstance())).get("message_id"))
                .isNotEqualTo(asText(headers.of(ClipReady.getDefaultInstance())).get("message_id"));
    }

    // Only outside a request: every request has a correlation id in the MDC
    @Test
    void should_generateACorrelationId_when_theMdcHasNone() {
        final String correlationId = asText(headers.of(ClipReady.getDefaultInstance())).get("correlation_id");

        assertThat(correlationId).startsWith("CTR-");
        assertThat(UUID.fromString(correlationId.substring("CTR-".length())).version()).isEqualTo(7);
    }

    @Test
    void should_encodeTheValuesInUtf8() {
        MDC.put("correlation_id", "gui-ação");

        final Header correlation = headers.of(ClipReady.getDefaultInstance()).getLast();

        assertThat(correlation.value()).isEqualTo("gui-ação".getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> asText(final List<Header> headers) {
        return headers.stream().collect(Collectors.toMap(Header::key,
                header -> new String(header.value(), StandardCharsets.UTF_8)));
    }
}

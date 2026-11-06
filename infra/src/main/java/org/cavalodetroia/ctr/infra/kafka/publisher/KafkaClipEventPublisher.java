package org.cavalodetroia.ctr.infra.kafka.publisher;

import com.google.protobuf.Message;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.cavalodetroia.contracts.clip.v1.ClipPairUploaded;
import org.cavalodetroia.ctr.domain.exception.PublishFailedException;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.port.out.ClipEventPublisher;
import org.cavalodetroia.ctr.infra.converter.ClipMessageConverter;
import org.cavalodetroia.ctr.infra.kafka.config.KafkaProducerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A completed part to Kafka: {@code ClipPairUploaded} on {@code clip-pair-uploaded.v1} for LEFT and RIGHT,
 * {@code ClipReady} on {@code clip-ready.v1} for SINGLE, keyed {@code <session_id>:<part_index>}, with the
 * {@link MessageHeaders standard headers}. Records the bytes of every message it publishes in the distribution
 * summary {@value #BYTES_METRIC}, tagged with the topic.
 * <p>
 * It waits for the broker's acknowledgement, the one exception to the CLAUDE.md rule against {@code .get()} on the
 * send future, at most {@link #SEND_TIMEOUT} in all: the confirmation answers whether its part was published (TH-152 PD-1), and must
 * not hang when the broker does. Any failure, the producer's own timeouts included, becomes
 * {@link PublishFailedException}, so no client exception reaches the domain.
 */
@Component
public class KafkaClipEventPublisher implements ClipEventPublisher {

    static final String BYTES_METRIC = "ctr.kafka.message.bytes";
    static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaClipEventPublisher.class);

    private final KafkaTemplate<String, Message> kafka;
    private final ClipMessageConverter converter;
    private final MessageHeaders headers;
    private final KafkaProducerProperties.Topics topics;
    private final MeterRegistry meters;

    public KafkaClipEventPublisher(final KafkaTemplate<String, Message> kafka,
                                   final ClipMessageConverter converter,
                                   final MessageHeaders headers,
                                   final KafkaProducerProperties properties,
                                   final MeterRegistry meters) {
        this.kafka = kafka;
        this.converter = converter;
        this.headers = headers;
        this.topics = properties.topics();
        this.meters = meters;
    }

    @Override
    public void publish(final UploadSession session, final int partIndex) throws PublishFailedException {
        final Message message = converter.toMessage(session, partIndex);
        final String topic = message instanceof ClipPairUploaded ? topics.clipPairUploaded() : topics.clipReady();
        final String key = converter.toKey(session, partIndex);
        final List<Header> recordHeaders = headers.of(message);
        final long deadline = System.nanoTime() + SEND_TIMEOUT.toNanos();
        final RecordMetadata sent;
        try {
            // send() itself may block up to max.block.ms while it fetches the topic's metadata; the wait for the
            // acknowledgement gets what is left of the budget
            sent = kafka.send(new ProducerRecord<>(topic, null, key, message, recordHeaders))
                    .get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
                    .getRecordMetadata();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublishFailedException(topic, key, e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            // RuntimeException: what send() throws before it hands over a future (serialization, a closed producer)
            throw new PublishFailedException(topic, key, e);
        }
        final long bytes = bytes(sent, recordHeaders);
        DistributionSummary.builder(BYTES_METRIC)
                .description("Bytes of each message ctr-service publishes: key, value and headers")
                .baseUnit("bytes")
                .tag("topic", topic)
                .register(meters)
                .record(bytes);
        LOGGER.info("operation=publishPart, message=Message published topic='{}' key='{}' type='{}' bytes={}", topic,
                key, message.getDescriptorForType().getName(), bytes);
    }

    /**
     * What went over the wire for the record (TH-152 PD-6): the key and the value as the serializers wrote them, which
     * the producer reports in the record's metadata, plus every header's key and value.
     */
    static long bytes(final RecordMetadata sent, final List<Header> recordHeaders) {
        long bytes = Math.max(0, sent.serializedKeySize()) + (long) Math.max(0, sent.serializedValueSize());
        for (final Header header : recordHeaders) {
            bytes += header.key().getBytes(StandardCharsets.UTF_8).length + header.value().length;
        }
        return bytes;
    }
}

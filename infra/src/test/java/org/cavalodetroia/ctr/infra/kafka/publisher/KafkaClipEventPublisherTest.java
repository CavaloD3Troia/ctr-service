package org.cavalodetroia.ctr.infra.kafka.publisher;

import com.google.protobuf.Message;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;
import org.cavalodetroia.contracts.clip.v1.ClipPairUploaded;
import org.cavalodetroia.contracts.clip.v1.ClipReady;
import org.cavalodetroia.ctr.domain.exception.PublishFailedException;
import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.CameraRegistration;
import org.cavalodetroia.ctr.domain.model.upload.ClipConfirmation;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.model.venue.RequestedVenue;
import org.cavalodetroia.ctr.infra.converter.ClipMessageConverter;
import org.cavalodetroia.ctr.infra.kafka.config.KafkaProducerProperties;
import org.cavalodetroia.ctr.infra.kafka.serialization.ProtobufSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * TH-152 AC-1, AC-2, AC-6, AC-7: what the adapter sends and records, through Kafka's own {@link MockProducer} behind a
 * real {@link KafkaTemplate}. The exchange with a broker, and a broker that does not answer, are covered by the
 * component tests.
 */
class KafkaClipEventPublisherTest {

    private static final UUID EVENT_ID = UUID.fromString("0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11");
    private static final UUID SESSION_ID = UUID.fromString("019a5b2e-7f3c-7000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-11-05T11:00:00Z");
    private static final KafkaProducerProperties TOPICS = new KafkaProducerProperties(
            new KafkaProducerProperties.Topics("clip-pair-uploaded.v1", "clip-ready.v1"));

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final ClipMessageConverter converter = new ClipMessageConverter();

    @Test
    void should_sendTheClipPairUploadedKeyedBySessionAndPart_when_aLeftAndRightPartIsPublished() throws Exception {
        final MockProducer<String, Message> producer = producer(true);
        final UploadSession session = leftAndRightWithPart0Complete();

        publisher(producer).publish(session, 0);

        assertThat(producer.history()).singleElement().satisfies(record -> {
            assertThat(record.topic()).isEqualTo("clip-pair-uploaded.v1");
            assertThat(record.key()).isEqualTo(SESSION_ID + ":0");
            assertThat(record.value()).isInstanceOf(ClipPairUploaded.class)
                    .isEqualTo(converter.toMessage(session, 0));
            assertThat(Arrays.stream(record.headers().toArray()).map(Header::key))
                    .containsExactly("message_id", "message_type", "occurred_at", "producer", "correlation_id");
        });
    }

    @Test
    void should_sendTheClipReady_when_aSingleCamerasPartIsPublished() throws Exception {
        final MockProducer<String, Message> producer = producer(true);

        publisher(producer).publish(singleWithPart0Complete(), 0);

        assertThat(producer.history()).singleElement().satisfies(record -> {
            assertThat(record.topic()).isEqualTo("clip-ready.v1");
            assertThat(record.value()).isInstanceOf(ClipReady.class);
        });
    }

    // AC-7: one record per publish, of the key, value and header bytes, tagged with the topic (PD-6)
    @Test
    void should_recordTheBytesOfTheMessageUnderItsTopic_when_published() throws Exception {
        final MockProducer<String, Message> producer = producer(true);

        publisher(producer).publish(singleWithPart0Complete(), 0);

        final ProducerRecord<String, Message> sent = producer.history().getFirst();
        final DistributionSummary summary = meters.get("ctr.kafka.message.bytes").tag("topic", "clip-ready.v1")
                .summary();
        assertThat(summary.count()).isEqualTo(1);
        final long headerBytes = Arrays.stream(sent.headers().toArray())
                .mapToLong(header -> header.key().getBytes(StandardCharsets.UTF_8).length + header.value().length)
                .sum();
        // MockProducer reports no serialized key or value size (0), so only the headers are left here; the sizes a
        // real producer reports are compared with the consumed record in the component tests
        assertThat(summary.totalAmount()).isEqualTo(headerBytes);
        assertThat(meters.find("ctr.kafka.message.bytes").tag("topic", "clip-pair-uploaded.v1").summary()).isNull();
    }

    @Test
    void should_countTheSerializedKeyAndValueAndEveryHeader_when_measuringAMessage() {
        final RecordMetadata sent = new RecordMetadata(new TopicPartition("clip-ready.v1", 0), 0, 0, 0, 3, 40);
        final Header header = new RecordHeader("ab", new byte[]{1, 2, 3});

        assertThat(KafkaClipEventPublisher.bytes(sent, List.of(header))).isEqualTo(3 + 40 + 2 + 3);
    }

    @Test
    void should_countNothingForAnUnknownSize_when_measuringAMessage() {
        // RecordMetadata reports -1 for a size it does not know
        final RecordMetadata sent = new RecordMetadata(new TopicPartition("clip-ready.v1", 0), 0, 0, 0, -1, -1);

        assertThat(KafkaClipEventPublisher.bytes(sent, List.of())).isZero();
    }

    // AC-6: the broker's failure becomes the domain's, with the topic and the key, and nothing is recorded
    @Test
    void should_throwPublishFailedWithTheTopicAndKey_when_theBrokerFailsTheSend() {
        final MockProducer<String, Message> producer = producer(false);
        final KafkaClipEventPublisher publisher = publisher(producer);
        CompletableFuture.runAsync(() -> {
            await().atMost(Duration.ofSeconds(2)).until(() -> !producer.history().isEmpty());
            producer.errorNext(new KafkaException("Expiring 1 record(s): 5000 ms has passed since batch creation"));
        });

        assertThatThrownBy(() -> publisher.publish(singleWithPart0Complete(), 0))
                .isInstanceOfSatisfying(PublishFailedException.class, e -> {
                    assertThat(e.topic()).isEqualTo("clip-ready.v1");
                    assertThat(e).hasMessageContaining(SESSION_ID + ":0");
                })
                .hasRootCauseInstanceOf(KafkaException.class);
        assertThat(meters.find("ctr.kafka.message.bytes").summary()).isNull();
    }

    @Test
    void should_throwPublishFailed_when_theProducerRefusesTheRecordOutright() {
        final MockProducer<String, Message> producer = producer(true);
        producer.close();

        assertThatThrownBy(() -> publisher(producer).publish(singleWithPart0Complete(), 0))
                .isInstanceOf(PublishFailedException.class);
    }

    private KafkaClipEventPublisher publisher(final MockProducer<String, Message> producer) {
        return new KafkaClipEventPublisher(new KafkaTemplate<>(() -> producer), converter,
                new MessageHeaders(Clock.fixed(NOW, ZoneOffset.UTC)), TOPICS, meters);
    }

    private static MockProducer<String, Message> producer(final boolean autoComplete) {
        return new MockProducer<>(autoComplete, null, new StringSerializer(), new ProtobufSerializer<>());
    }

    private static UploadSession leftAndRightWithPart0Complete() {
        return UploadSession.open(SESSION_ID, EVENT_ID, new CameraRegistration(Camera.LEFT, 2, "video/mp4",
                        new RequestedVenue("FOOTBALL_11V11", null, null)), "cdt-videos", NOW).session()
                .join(new CameraRegistration(Camera.RIGHT, 2, "video/mp4", null), "cdt-videos", NOW).session()
                .completeClip(Camera.LEFT, 0, clip(), NOW).after()
                .completeClip(Camera.RIGHT, 0, clip(), NOW).after();
    }

    private static UploadSession singleWithPart0Complete() {
        return UploadSession.open(SESSION_ID, EVENT_ID, new CameraRegistration(Camera.SINGLE, 2, "video/mp4",
                        new RequestedVenue("FUTSAL_5V5", null, null)), "cdt-videos", NOW).session()
                .completeClip(Camera.SINGLE, 0, clip(), NOW).after();
    }

    private static ClipConfirmation clip() {
        return new ClipConfirmation(1_073_741_824L, "9b2cf535f27731c974343645a3985328", 900_000, 300_000);
    }
}

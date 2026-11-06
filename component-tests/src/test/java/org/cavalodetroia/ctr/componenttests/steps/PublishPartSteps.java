package org.cavalodetroia.ctr.componenttests.steps;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.cavalodetroia.contracts.clip.v1.Clip;
import org.cavalodetroia.contracts.clip.v1.ClipPairUploaded;
import org.cavalodetroia.contracts.clip.v1.ClipReady;
import org.cavalodetroia.contracts.venue.v1.VenueSpec;
import org.cavalodetroia.ctr.application.CtrApplication;
import org.cavalodetroia.ctr.componenttests.config.Containers;
import org.cavalodetroia.ctr.componenttests.support.KafkaSupport;
import org.cavalodetroia.ctr.componenttests.support.MinioSupport;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.UploadPartRecord;
import org.jooq.DSLContext;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.web.client.RestClient;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_PART;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_SESSION;

/**
 * What a confirmation publishes (TH-152): reads clip-pair-uploaded.v1 and clip-ready.v1 from the moment the scenario
 * starts, and checks the messages of the scenario's session, the answer, the part in Postgres, the error log, the
 * bytes metric and the topics. The cameras' side (sessions, uploads, confirmations) is {@link CompleteClipSteps}'s.
 */
public class PublishPartSteps {

    private static final String METRIC_PATH = "/actuator/metrics/ctr.kafka.message.bytes?tag=topic:{topic}";
    private static final String CORRELATION_ID = "X-Correlation-Id";
    private static final List<String> LOCAL_TOPIC_SUFFIXES = List.of("", ".retry", ".dlq");

    private final CompleteClipSteps confirmations;
    private final RestClient http;
    private final DSLContext dsl;
    private final ApplicationContext app;

    private KafkaSupport.Reader reader;
    private boolean kafkaPaused;
    private ConsumerRecord<String, byte[]> record;
    private Message message;
    private Measured before;
    private ConfigurableApplicationContext localInstance;

    public PublishPartSteps(final CompleteClipSteps confirmations, @LocalServerPort final int port,
                            final DSLContext dsl, final ApplicationContext app) {
        this.confirmations = confirmations;
        this.http = http(port);
        this.dsl = dsl;
        this.app = app;
    }

    /**
     * The topics exist before the app publishes, as the platform provides them; every confirmation that completes a
     * part publishes, in every feature.
     */
    @BeforeAll
    public static void createTheTopics() {
        KafkaSupport.createTopics(KafkaSupport.CLIP_PAIR_UPLOADED, KafkaSupport.CLIP_READY);
    }

    @Before("@publishing")
    public void readTheTopicsFromNow() {
        reader = KafkaSupport.readFromNow(KafkaSupport.CLIP_PAIR_UPLOADED, KafkaSupport.CLIP_READY);
    }

    @After("@publishing")
    public void restoreKafkaAndStopReading() {
        if (kafkaPaused) {
            KafkaSupport.unpause();
        }
        if (localInstance != null) {
            localInstance.close();
        }
        reader.close();
    }

    @Given("Kafka stops answering")
    public void kafkaStopsAnswering() {
        KafkaSupport.pause();
        kafkaPaused = true;
    }

    @When("Kafka answers again")
    public void kafkaAnswersAgain() {
        KafkaSupport.unpause();
        kafkaPaused = false;
        await().atMost(Duration.ofSeconds(30)).until(KafkaSupport::answers);
    }

    @Then("the answer says part {int} is published, {int} of {int} parts published")
    public void theAnswerSaysThePartIsPublished(final int partIndex, final int partsPublished, final int partCount) {
        assertThat(read("$.partIndex", Integer.class)).isEqualTo(partIndex);
        assertThat(read("$.part.status", String.class)).isEqualTo("COMPLETE");
        assertThat(read("$.part.published", Boolean.class)).isTrue();
        assertThat(read("$.session.partsPublished", Integer.class)).isEqualTo(partsPublished);
        assertThat(read("$.session.partCount", Integer.class)).isEqualTo(partCount);
    }

    @Then("the answer came within {int} seconds and says part {int} is {word} but not published")
    public void theAnswerCameWithinAndSaysNotPublished(final int seconds, final int partIndex, final String status) {
        assertThat(confirmations.answeredIn()).isLessThan(Duration.ofSeconds(seconds));
        assertThat(read("$.partIndex", Integer.class)).isEqualTo(partIndex);
        assertThat(read("$.part.status", String.class)).isEqualTo(status);
        assertThat(read("$.part.published", Boolean.class)).isFalse();
    }

    @Then("part {int} is published in the database")
    public void thePartIsPublishedInTheDatabase(final int partIndex) {
        final UploadPartRecord part = part(partIndex);
        assertThat(part.getStatus()).isEqualTo("COMPLETE");
        assertThat(part.getPublishedAt()).isBetween(OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now());
    }

    @Then("part {int} is {word} in the database but not published")
    public void thePartIsNotPublishedInTheDatabase(final int partIndex, final String status) {
        final UploadPartRecord part = part(partIndex);
        assertThat(part.getStatus()).isEqualTo(status);
        assertThat(part.getPublishedAt()).isNull();
    }

    @Then("an error says part {int} could not be published, carrying the session id and the part index")
    public void anErrorSaysThePartCouldNotBePublished(final int partIndex) {
        assertThat(confirmations.requestLogs())
                .filteredOn(event -> event.getFormattedMessage().contains("message=Publish failed"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel().toString()).isEqualTo("ERROR");
                    assertThat(event.getMDCPropertyMap())
                            .containsEntry("session_id", confirmations.sessionId().toString())
                            .containsEntry("part_index", String.valueOf(partIndex));
                });
    }

    @Then("one {word} for part {int} of {int} is on {word}, starting at {long} ms for {long} ms")
    public void oneMessageIsOnTheTopic(final String type, final int partIndex, final int partCount,
                                       final String topic, final long startMs, final long durationMs) {
        final String key = key(partIndex);
        await().atMost(Duration.ofSeconds(10)).pollInSameThread().until(() -> !recordsOf(key).isEmpty());
        final List<ConsumerRecord<String, byte[]>> sent = recordsOf(key);
        assertThat(sent).hasSize(1);
        record = sent.getFirst();
        assertThat(record.topic()).isEqualTo(topic);
        message = parse(type, record.value());
        final UUID eventId = dsl.select(UPLOAD_SESSION.EVENT_ID).from(UPLOAD_SESSION)
                .where(UPLOAD_SESSION.ID.eq(confirmations.sessionId()))
                .fetchSingle(UPLOAD_SESSION.EVENT_ID);
        assertThat(field("session_id")).isEqualTo(confirmations.sessionId().toString());
        assertThat(field("event_id")).isEqualTo(eventId.toString());
        assertThat(field("part_index")).isEqualTo(partIndex);
        assertThat(field("part_count")).isEqualTo(partCount);
        assertThat(field("start_ms")).isEqualTo(startMs);
        assertThat(field("duration_ms")).isEqualTo(durationMs);
    }

    @Then("its LEFT and RIGHT clips are the uploaded ones, of type {word}")
    public void itsLeftAndRightClipsAreTheUploadedOnes(final String contentType) {
        final ClipPairUploaded pair = (ClipPairUploaded) message;
        assertTheUploadedClip(pair.getLeft(), "LEFT", pair.getPartIndex(), contentType);
        assertTheUploadedClip(pair.getRight(), "RIGHT", pair.getPartIndex(), contentType);
    }

    @Then("its SINGLE clip is the uploaded one, of type {word}")
    public void itsSingleClipIsTheUploadedOne(final String contentType) {
        final ClipReady ready = (ClipReady) message;
        assertTheUploadedClip(ready.getClip(), "SINGLE", ready.getPartIndex(), contentType);
    }

    @Then("its venue is {word} {word} of {int} by {int} m")
    public void itsVenueIs(final String sport, final String format, final int lengthM, final int widthM) {
        final VenueSpec venue = message instanceof ClipPairUploaded pair
                ? pair.getVenue()
                : ((ClipReady) message).getVenue();
        assertThat(venue.getSport()).isEqualTo(sport);
        assertThat(venue.getFormat()).isEqualTo(format);
        assertThat(venue.getLengthM()).isEqualTo(lengthM);
        assertThat(venue.getWidthM()).isEqualTo(widthM);
    }

    @Then("nothing about the session is on {word}")
    public void nothingAboutTheSessionIsOn(final String topic) {
        final String session = confirmations.sessionId() + ":";
        assertThat(reader.records())
                .filteredOn(other -> other.topic().equals(topic) && other.key().startsWith(session))
                .isEmpty();
    }

    @Then("its key is the session id, a colon and the part index {int}")
    public void itsKeyIs(final int partIndex) {
        assertThat(record.key()).isEqualTo(confirmations.sessionId() + ":" + partIndex);
    }

    @Then("its headers are a UUIDv7 message_id, the message_type {word}, an occurred_at in UTC, the producer {word} and "
            + "the correlation id of the confirmation")
    public void itsHeadersAre(final String messageType, final String producer) {
        final Map<String, String> headers = headers(record);
        assertThat(headers).containsOnlyKeys("message_id", "message_type", "occurred_at", "producer", "correlation_id");
        assertThat(UUID.fromString(headers.get("message_id")).version()).isEqualTo(7);
        assertThat(headers).containsEntry("message_type", messageType);
        assertThat(headers.get("occurred_at")).endsWith("Z");
        assertThat(Instant.parse(headers.get("occurred_at")))
                .isBetween(confirmations.confirmationSentAt(), Instant.now());
        assertThat(headers).containsEntry("producer", producer);
        assertThat(headers.get("correlation_id"))
                .isNotBlank()
                .isEqualTo(confirmations.response().getHeaders().getFirst(CORRELATION_ID));
    }

    @Then("no (other )message about part {int} is published within {int} seconds")
    public void noOtherMessageIsPublished(final int partIndex, final int seconds) {
        final String key = key(partIndex);
        final int already = recordsOf(key).size();
        await().during(Duration.ofSeconds(seconds)).atMost(Duration.ofSeconds(seconds + 2L)).pollInSameThread()
                .until(() -> recordsOf(key).size() == already);
    }

    /**
     * A broker frozen mid-request may still append what it had received once it answers again, so an earlier copy
     * of the message is tolerated (TH-152 PD-3: consumers are idempotent). What the repeat must do is publish one.
     */
    @Then("the repeated confirmation published one {word} for part {int} on {word}")
    public void theRepeatedConfirmationPublishedOne(final String type, final int partIndex, final String topic) {
        final String key = key(partIndex);
        final Instant repeatedAt = confirmations.confirmationSentAt();
        await().atMost(Duration.ofSeconds(10)).pollInSameThread()
                .until(() -> recordsOf(key).stream().anyMatch(sent -> !occurredAt(sent).isBefore(repeatedAt)));
        final List<ConsumerRecord<String, byte[]>> all = recordsOf(key);
        final List<ConsumerRecord<String, byte[]>> byTheRepeat = all.stream()
                .filter(sent -> !occurredAt(sent).isBefore(repeatedAt))
                .toList();
        assertThat(byTheRepeat).hasSize(1);
        assertThat(byTheRepeat.getFirst().topic()).isEqualTo(topic);
        final Message repeated = parse(type, byTheRepeat.getFirst().value());
        assertThat(all).allSatisfy(sent -> assertThat(parse(type, sent.value())).isEqualTo(repeated));
    }

    @Given("the messages measured on {word} so far")
    public void theMessagesMeasuredSoFar(final String topic) throws Exception {
        before = measured(topic);
    }

    @Then("the actuator measured one more message on {word}, of the key, value and header bytes it carries")
    public void theActuatorMeasuredOneMore(final String topic) {
        final long bytes = record.serializedKeySize() + record.serializedValueSize() + headerBytes(record);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            final Measured now = measured(topic);
            assertThat(now.count()).isEqualTo(before.count() + 1);
            assertThat(now.totalBytes()).isEqualTo(before.totalBytes() + bytes);
        });
    }

    @Then("JMX measured one more message on {word}")
    public void jmxMeasuredOneMore(final String topic) throws Exception {
        assertThat(jmxCount(topic)).isEqualTo(before.jmxCount() + 1);
    }

    @Given("the running app declares no topic and serves no Jolokia")
    public void theRunningAppDeclaresNoTopic() {
        assertThat(app.getBeansOfType(NewTopic.class)).isEmpty();
        assertThat(app.getBeansOfType(KafkaAdmin.NewTopics.class)).isEmpty();
        assertThat(http.get().uri("/actuator/jolokia/version").retrieve().toBodilessEntity().getStatusCode().value())
                .isEqualTo(404);
    }

    @Given("there is no .retry or .dlq topic for {word} or {word}")
    public void thereIsNoRetryOrDlqTopic(final String first, final String second) {
        assertThat(KafkaSupport.topics()).doesNotContain(
                first + ".retry", first + ".dlq", second + ".retry", second + ".dlq");
    }

    @When("another instance of the app starts with the local profile added")
    public void anotherInstanceStartsWithTheLocalProfile() {
        localInstance = new SpringApplicationBuilder(CtrApplication.class)
                .profiles("test", "local")
                .run("--server.port=0",
                        "--spring.datasource.url=" + Containers.POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + Containers.POSTGRES.getUsername(),
                        "--spring.datasource.password=" + Containers.POSTGRES.getPassword(),
                        "--spring.kafka.bootstrap-servers=" + Containers.KAFKA.getBootstrapServers(),
                        "--ctr.storage.endpoint=" + Containers.MINIO.getS3URL(),
                        "--ctr.storage.access-key=" + Containers.MINIO.getUserName(),
                        "--ctr.storage.secret-key=" + Containers.MINIO.getPassword());
    }

    @Then("the topics {word}, {word} and their .retry and .dlq exist")
    public void theTopicsAndTheirRetryAndDlqExist(final String first, final String second) {
        final List<String> expected = LOCAL_TOPIC_SUFFIXES.stream()
                .flatMap(suffix -> List.of(first + suffix, second + suffix).stream())
                .toList();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(KafkaSupport.topics()).containsAll(expected));
    }

    @Then("that instance serves Jolokia for Hawtio")
    public void thatInstanceServesJolokia() {
        final int port = localInstance.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        final ResponseEntity<String> version = http(port).get().uri("/actuator/jolokia/version").retrieve()
                .toEntity(String.class);
        assertThat(version.getStatusCode().value()).as(version.getBody()).isEqualTo(200);
        assertThat(JsonPath.parse(version.getBody()).read("$.status", Integer.class)).isEqualTo(200);
        assertThat(JsonPath.parse(version.getBody()).read("$.value.agent", String.class)).isNotBlank();
    }

    private void assertTheUploadedClip(final Clip clip, final String camera, final int partIndex,
                                       final String contentType) {
        assertThat(clip.getUri())
                .isEqualTo("s3://" + MinioSupport.BUCKET + "/" + confirmations.objectKey(camera, partIndex));
        assertThat(clip.getSizeBytes()).isEqualTo(confirmations.uploaded(camera, partIndex).sizeBytes());
        assertThat(clip.getContentType()).isEqualTo(contentType);
    }

    private List<ConsumerRecord<String, byte[]>> recordsOf(final String key) {
        return reader.records().stream().filter(sent -> key.equals(sent.key())).toList();
    }

    private String key(final int partIndex) {
        return confirmations.sessionId() + ":" + partIndex;
    }

    private Object field(final String name) {
        return message.getField(message.getDescriptorForType().findFieldByName(name));
    }

    private UploadPartRecord part(final int partIndex) {
        return dsl.selectFrom(UPLOAD_PART)
                .where(UPLOAD_PART.SESSION_ID.eq(confirmations.sessionId()))
                .and(UPLOAD_PART.PART_INDEX.eq(partIndex))
                .fetchSingle();
    }

    private <T> T read(final String path, final Class<T> type) {
        return JsonPath.parse(confirmations.response().getBody()).read(path, type);
    }

    private Measured measured(final String topic) throws Exception {
        final ResponseEntity<String> metric = http.get().uri(METRIC_PATH, topic).retrieve().toEntity(String.class);
        final long jmxCount = jmxCount(topic);
        if (metric.getStatusCode().value() == 404) {
            // Nothing published on the topic yet: the summary is registered with the first message
            return new Measured(0, 0, jmxCount);
        }
        assertThat(metric.getStatusCode().value()).as(metric.getBody()).isEqualTo(200);
        return new Measured(statistic(metric.getBody(), "COUNT"), statistic(metric.getBody(), "TOTAL"), jmxCount);
    }

    private static long statistic(final String body, final String statistic) {
        final List<Double> values = JsonPath.parse(body)
                .read("$.measurements[?(@.statistic == '" + statistic + "')].value");
        return Math.round(values.getFirst());
    }

    /**
     * The JMX registry's MBean for the summary on the topic, in the platform MBean server the app shares with the
     * tests; 0 before the first message.
     */
    private static long jmxCount(final String topic) throws Exception {
        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        final List<ObjectName> names = server.queryNames(new ObjectName("metrics:*"), null).stream()
                .filter(name -> name.toString().contains("ctrKafkaMessageBytes") && name.toString().contains(topic))
                .toList();
        if (names.isEmpty()) {
            return 0;
        }
        assertThat(names).hasSize(1);
        return ((Number) server.getAttribute(names.getFirst(), "Count")).longValue();
    }

    private static Message parse(final String type, final byte[] value) {
        try {
            return switch (type) {
                case "ClipPairUploaded" -> ClipPairUploaded.parseFrom(value);
                case "ClipReady" -> ClipReady.parseFrom(value);
                default -> throw new IllegalArgumentException("Not a message ctr-service publishes: " + type);
            };
        } catch (InvalidProtocolBufferException e) {
            throw new AssertionError("Not a " + type, e);
        }
    }

    private static Map<String, String> headers(final ConsumerRecord<String, byte[]> sent) {
        return Arrays.stream(sent.headers().toArray()).collect(Collectors.toMap(Header::key,
                header -> new String(header.value(), StandardCharsets.UTF_8)));
    }

    private static Instant occurredAt(final ConsumerRecord<String, byte[]> sent) {
        return Instant.parse(headers(sent).get("occurred_at"));
    }

    private static long headerBytes(final ConsumerRecord<String, byte[]> sent) {
        return Arrays.stream(sent.headers().toArray())
                .mapToLong(header -> header.key().getBytes(StandardCharsets.UTF_8).length + header.value().length)
                .sum();
    }

    private static RestClient http(final int port) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                // A 4xx or 5xx is something to assert on, not an exception
                .defaultStatusHandler(status -> true, (request, ignored) -> { })
                .build();
    }

    private record Measured(long count, long totalBytes, long jmxCount) {
    }
}

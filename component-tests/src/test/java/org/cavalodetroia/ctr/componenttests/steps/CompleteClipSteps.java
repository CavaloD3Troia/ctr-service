package org.cavalodetroia.ctr.componenttests.steps;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.cavalodetroia.ctr.componenttests.support.LogCapture;
import org.cavalodetroia.ctr.componenttests.support.MinioSupport;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.UploadPartRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.EVENT;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_CLIP;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_PART;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_SESSION;

/**
 * Registers cameras over HTTP, puts their clips in the MinIO container as a camera would, confirms them over HTTP,
 * then checks the answer, the rows in Postgres and the log lines of the request.
 */
public class CompleteClipSteps {

    // Written out on purpose: the paths are part of the contract
    private static final String REGISTER_PATH = "/api/v1/events/{eventId}/upload-session/cameras";
    private static final String COMPLETE_PATH =
            "/api/v1/upload-sessions/{sessionId}/cameras/{camera}/parts/{partIndex}/complete";
    private static final String REGISTRATION = """
            {"camera": "%s", "partCount": %d, "contentType": "video/mp4", "venue": %s}""";
    private static final String STANDARD_PITCH = """
            {"format": "FOOTBALL_11V11"}""";
    private static final String OUR_LOGGERS = "org.cavalodetroia.ctr.";
    private static final int CLIP_BYTES = 1024;
    // For a clip never put in the bucket: any well-formed size and etag
    private static final UploadedClip NOT_UPLOADED = new UploadedClip(CLIP_BYTES, "\"0f343b0931126a20f133d67c2b018a3b\"");

    private final RestClient http;
    private final DSLContext dsl;
    private final LogCapture logs = new LogCapture();

    private UUID sessionId;
    private int partCount;
    private String venue = STANDARD_PITCH;
    private List<String> cameras = List.of();
    // What each PUT got back, by session, camera and part index: the size and the ETag, quoted as S3 sends it
    private final Map<String, UploadedClip> uploaded = new HashMap<>();
    private boolean storagePaused;
    private ResponseEntity<String> response;
    private Duration answeredIn;
    private Instant confirmationSentAt;
    private String firstConfirmation;
    private List<Map<String, Object>> rowsBefore;
    private List<ILoggingEvent> requestLogs;
    private final List<UUID> concurrentSessions = new ArrayList<>();
    private final Map<UUID, List<ResponseEntity<String>>> concurrentAnswers = new HashMap<>();

    public CompleteClipSteps(@LocalServerPort final int port, final DSLContext dsl) {
        this.http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                // A 4xx or 5xx is something to assert on, not an exception
                .defaultStatusHandler(status -> true, (request, ignored) -> { })
                .build();
        this.dsl = dsl;
    }

    @BeforeAll
    public static void createTheBucket() {
        MinioSupport.createBucket();
    }

    @Before
    public void captureLogs() {
        logs.start();
    }

    @After
    public void restoreTheStorageAndStopCapturingLogs() {
        if (storagePaused) {
            MinioSupport.unpause();
        }
        logs.stop();
    }

    @Given("a session with the cameras {string} and {int} part(s)")
    public void aSessionWithTheCameras(final String cameras, final int partCount) {
        this.cameras = names(cameras);
        this.partCount = partCount;
        sessionId = openSession(this.cameras, partCount);
    }

    @Given("a session with the cameras {string}, {int} part(s) and a {word} venue of {int} by {int} m")
    public void aSessionWithTheCamerasAndTheVenue(final String cameras, final int partCount, final String format,
                                                  final int lengthM, final int widthM) {
        venue = """
                {"format": "%s", "lengthM": %d, "widthM": %d}""".formatted(format, lengthM, widthM);
        aSessionWithTheCameras(cameras, partCount);
    }

    @Given("the session is unknown")
    public void theSessionIsUnknown() {
        sessionId = UUID.randomUUID();
    }

    @Given("the {word} camera has uploaded clip {int}")
    public void theCameraHasUploadedClip(final String camera, final int partIndex) {
        upload(sessionId, camera, partIndex);
    }

    @Given("every camera has uploaded every clip")
    public void everyCameraHasUploadedEveryClip() {
        for (final String camera : cameras) {
            for (int partIndex = 0; partIndex < partCount; partIndex++) {
                upload(sessionId, camera, partIndex);
            }
        }
    }

    @Given("clip {word} {int} is replaced in the bucket by another one of the same size")
    public void theClipIsReplacedInTheBucket(final String camera, final int partIndex) {
        final byte[] other = new byte[CLIP_BYTES];
        Arrays.fill(other, (byte) 'x');
        MinioSupport.put(objectKey(sessionId, camera, partIndex), other);
    }

    @Given("clip {word} {int} is deleted from the bucket")
    public void theClipIsDeletedFromTheBucket(final String camera, final int partIndex) {
        MinioSupport.delete(objectKey(sessionId, camera, partIndex));
    }

    @Given("the storage stops answering")
    public void theStorageStopsAnswering() {
        MinioSupport.pause();
        storagePaused = true;
    }

    @Given("the {word} camera confirmed clip {int} starting at {long} ms for {long} ms")
    public void theCameraConfirmedClip(final String camera, final int partIndex, final long startMs,
                                       final long durationMs) {
        theCameraConfirmsClip(camera, partIndex, startMs, durationMs);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        firstConfirmation = response.getBody();
    }

    @When("the {word} camera confirms clip {int} starting at {long} ms for {long} ms")
    public void theCameraConfirmsClip(final String camera, final int partIndex, final long startMs,
                                      final long durationMs) {
        final UploadedClip clip = uploaded.getOrDefault(key(sessionId, camera, partIndex), NOT_UPLOADED);
        confirm(camera, partIndex, body(clip.sizeBytes(), clip.etag(), startMs, durationMs));
    }

    @When("the {word} camera confirms clip {int} with one byte more than it uploaded")
    public void theCameraConfirmsClipWithOneByteMore(final String camera, final int partIndex) {
        final UploadedClip clip = uploaded.get(key(sessionId, camera, partIndex));
        confirm(camera, partIndex, body(clip.sizeBytes() + 1, clip.etag(), 900_000, 300_000));
    }

    @When("the {word} camera confirms clip {int} with this body")
    public void theCameraConfirmsClipWithThisBody(final String camera, final int partIndex, final String body) {
        confirm(camera, partIndex, body);
    }

    @Then("the confirmation is answered with {int}")
    public void theConfirmationIsAnsweredWith(final int status) {
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Then("the confirmation is rejected with {int} and the code {string}")
    public void theConfirmationIsRejectedWith(final int status, final String code) {
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(read("$.code", String.class)).isEqualTo(code);
    }

    @Then("the problem names the field {string}")
    public void theProblemNamesTheField(final String field) {
        final List<String> fields = JsonPath.parse(response.getBody()).read("$.errors[*].field");
        assertThat(fields).containsExactly(field);
    }

    @Then("the answer is clip {word} {int} {word}, its part {word} and not published, the session {word} with {int} "
            + "of {int} parts published")
    public void theAnswerIs(final String camera, final int partIndex, final String clipStatus, final String partStatus,
                            final String sessionStatus, final int partsPublished, final int parts) {
        assertThat(JsonMapper.shared().readTree(response.getBody())).isEqualTo(JsonMapper.shared().readTree("""
                {
                  "camera": "%s",
                  "partIndex": %d,
                  "status": "%s",
                  "part": {"status": "%s", "published": false},
                  "session": {"status": "%s", "partsPublished": %d, "partCount": %d}
                }""".formatted(camera, partIndex, clipStatus, partStatus, sessionStatus, partsPublished, parts)));
    }

    @Then("the answer says the part is {word} and the session is {word}")
    public void theAnswerSaysThePartAndTheSession(final String partStatus, final String sessionStatus) {
        assertThat(read("$.part.status", String.class)).isEqualTo(partStatus);
        // TH-152: a COMPLETE part is published before the answer; a NO_OVERLAP one never is
        assertThat(read("$.part.published", Boolean.class)).isEqualTo("COMPLETE".equals(partStatus));
        assertThat(read("$.session.status", String.class)).isEqualTo(sessionStatus);
    }

    @Then("the answer is the same as the first confirmation's")
    public void theAnswerIsTheSameAsTheFirstConfirmations() {
        assertThat(JsonMapper.shared().readTree(response.getBody()))
                .isEqualTo(JsonMapper.shared().readTree(firstConfirmation));
    }

    @Then("the answer came within {int} seconds and says to retry after {int} seconds")
    public void theAnswerCameWithin(final int seconds, final int retryAfter) {
        assertThat(answeredIn).isLessThan(Duration.ofSeconds(seconds));
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo(String.valueOf(retryAfter));
    }

    @Then("clip {word} {int} is UPLOADED in the database with the uploaded size and etag, start {long} ms, duration "
            + "{long} ms and the upload time")
    public void theClipIsUploadedInTheDatabase(final String camera, final int partIndex, final long startMs,
                                               final long durationMs) {
        final var clip = dsl.selectFrom(UPLOAD_CLIP)
                .where(UPLOAD_CLIP.SESSION_ID.eq(sessionId))
                .and(UPLOAD_CLIP.CAMERA.eq(camera))
                .and(UPLOAD_CLIP.PART_INDEX.eq(partIndex))
                .fetchSingle();
        final UploadedClip put = uploaded.get(key(sessionId, camera, partIndex));
        assertThat(clip.getStatus()).isEqualTo("UPLOADED");
        assertThat(clip.getSizeBytes()).isEqualTo(put.sizeBytes());
        assertThat(clip.getEtag()).isEqualTo(put.etag());
        assertThat(clip.getStartMs()).isEqualTo(startMs);
        assertThat(clip.getDurationMs()).isEqualTo(durationMs);
        assertThat(clip.getUploadedAt()).isBetween(OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now());
    }

    @Then("clips {int} of {string} are UPLOADED in the database")
    public void theClipsAreUploadedInTheDatabase(final int partIndex, final String uploadedBy) {
        assertThat(dsl.select(UPLOAD_CLIP.CAMERA)
                .from(UPLOAD_CLIP)
                .where(UPLOAD_CLIP.SESSION_ID.eq(sessionId))
                .and(UPLOAD_CLIP.PART_INDEX.eq(partIndex))
                .and(UPLOAD_CLIP.STATUS.eq("UPLOADED"))
                .orderBy(UPLOAD_CLIP.CAMERA)
                .fetch(UPLOAD_CLIP.CAMERA))
                .containsExactlyElementsOf(names(uploadedBy));
    }

    @Then("part {int} is WAITING in the database")
    public void thePartIsWaitingInTheDatabase(final int partIndex) {
        final UploadPartRecord part = part(sessionId, partIndex);
        assertThat(part.getStatus()).isEqualTo("WAITING");
        assertThat(part.getStartMs()).isNull();
        assertThat(part.getDurationMs()).isNull();
    }

    @Then("part {int} is {word} in the database, starting at {long} ms for {long} ms")
    public void thePartIsClosedInTheDatabase(final int partIndex, final String status, final long startMs,
                                             final long durationMs) {
        final UploadPartRecord part = part(sessionId, partIndex);
        assertThat(part.getStatus()).isEqualTo(status);
        assertThat(part.getStartMs()).isEqualTo(startMs);
        assertThat(part.getDurationMs()).isEqualTo(durationMs);
        // TH-152: published, and so stamped, only when COMPLETE
        if ("COMPLETE".equals(status)) {
            assertThat(part.getPublishedAt()).isBetween(OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now());
        } else {
            assertThat(part.getPublishedAt()).isNull();
        }
    }

    @Then("the session is COMPLETED in the database")
    public void theSessionIsCompletedInTheDatabase() {
        assertThat(dsl.select(UPLOAD_SESSION.STATUS)
                .from(UPLOAD_SESSION)
                .where(UPLOAD_SESSION.ID.eq(sessionId))
                .fetchSingle(UPLOAD_SESSION.STATUS))
                .isEqualTo("COMPLETED");
    }

    @Then("nothing changed in the database")
    public void nothingChangedInTheDatabase() {
        assertThat(rowsBefore).isNotEmpty();
        assertThat(rows(sessionId)).isEqualTo(rowsBefore);
    }

    @Then("every log line of the confirmation carries the session id, the camera {word} and the part index {int}")
    public void everyLogLineCarriesTheSessionCameraAndPartIndex(final String camera, final int partIndex) {
        assertThat(requestLogs).isNotEmpty().allSatisfy(event -> assertThat(event.getMDCPropertyMap())
                .as(event.getFormattedMessage())
                .containsEntry("session_id", sessionId.toString())
                .containsEntry("camera", camera)
                .containsEntry("part_index", String.valueOf(partIndex))
                .containsKey("correlation_id"));
    }

    @Then("a warning says part {int} has no overlap, carrying the session id and the part index")
    public void aWarningSaysThePartHasNoOverlap(final int partIndex) {
        assertThat(requestLogs)
                .filteredOn(event -> event.getFormattedMessage().contains("message=Part has no overlap"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel().toString()).isEqualTo("WARN");
                    assertThat(event.getMDCPropertyMap())
                            .containsEntry("session_id", sessionId.toString())
                            .containsEntry("part_index", String.valueOf(partIndex));
                });
    }

    @Given("{int} sessions with the cameras {string} and {int} part(s), each with both clips uploaded")
    public void sessionsWithBothClipsUploaded(final int count, final String sessionCameras, final int parts) {
        for (int i = 0; i < count; i++) {
            final UUID session = openSession(names(sessionCameras), parts);
            for (final String camera : names(sessionCameras)) {
                upload(session, camera, 0);
            }
            concurrentSessions.add(session);
        }
    }

    @When("on each session LEFT and RIGHT confirm clip {int} at the same time")
    public void onEachSessionBothCamerasConfirmAtTheSameTime(final int partIndex) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (final UUID session : concurrentSessions) {
                final CountDownLatch start = new CountDownLatch(1);
                final List<Future<ResponseEntity<String>>> answers = new ArrayList<>();
                for (final String camera : List.of("LEFT", "RIGHT")) {
                    final UploadedClip clip = uploaded.get(key(session, camera, partIndex));
                    answers.add(pool.submit(() -> {
                        start.await();
                        return post(session, camera, partIndex, body(clip.sizeBytes(), clip.etag(), 0, 300_000));
                    }));
                }
                start.countDown();
                final List<ResponseEntity<String>> both = new ArrayList<>();
                for (final Future<ResponseEntity<String>> answer : answers) {
                    both.add(answer.get());
                }
                concurrentAnswers.put(session, both);
            }
        }
    }

    @Then("on each session one answer waits for the other camera and the other says COMPLETE")
    public void onEachSessionOneAnswerWaitsAndTheOtherCompletes() {
        for (final UUID session : concurrentSessions) {
            final List<String> parts = new ArrayList<>();
            for (final ResponseEntity<String> answer : concurrentAnswers.get(session)) {
                assertThat(answer.getStatusCode().value()).as(answer.getBody()).isEqualTo(200);
                parts.add(JsonPath.parse(answer.getBody()).read("$.part.status", String.class));
            }
            assertThat(parts).as("session %s", session)
                    .containsOnlyOnce("COMPLETE")
                    .containsAnyOf("WAITING_LEFT", "WAITING_RIGHT");
        }
    }

    @Then("on each session both clips are UPLOADED and part {int} is COMPLETE in the database")
    public void onEachSessionBothClipsAreUploadedAndThePartComplete(final int partIndex) {
        for (final UUID session : concurrentSessions) {
            assertThat(dsl.fetchCount(UPLOAD_CLIP, UPLOAD_CLIP.SESSION_ID.eq(session)
                    .and(UPLOAD_CLIP.PART_INDEX.eq(partIndex))
                    .and(UPLOAD_CLIP.STATUS.eq("UPLOADED"))))
                    .as("uploaded clips of %s", session)
                    .isEqualTo(2);
            assertThat(part(session, partIndex).getStatus()).as("part of %s", session).isEqualTo("COMPLETE");
        }
    }

    // For the steps of the publishing scenarios (PublishPartSteps), which go on from this class's

    UUID sessionId() {
        return sessionId;
    }

    ResponseEntity<String> response() {
        return response;
    }

    Duration answeredIn() {
        return answeredIn;
    }

    Instant confirmationSentAt() {
        return confirmationSentAt;
    }

    List<ILoggingEvent> requestLogs() {
        return requestLogs;
    }

    UploadedClip uploaded(final String camera, final int partIndex) {
        return uploaded.get(key(sessionId, camera, partIndex));
    }

    String objectKey(final String camera, final int partIndex) {
        return objectKey(sessionId, camera, partIndex);
    }

    /**
     * A new event per session, so the scenarios don't depend on each other or on their order.
     */
    private UUID openSession(final List<String> sessionCameras, final int parts) {
        final UUID eventId = UUID.randomUUID();
        dsl.insertInto(EVENT).set(EVENT.ID, eventId).set(EVENT.NAME, "Benfica vs Porto").execute();
        UUID session = null;
        for (final String camera : sessionCameras) {
            final ResponseEntity<String> registered = http.post()
                    .uri(REGISTER_PATH, eventId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(REGISTRATION.formatted(camera, parts, venue))
                    .retrieve()
                    .toEntity(String.class);
            assertThat(registered.getStatusCode().value()).as(registered.getBody()).isEqualTo(201);
            session = UUID.fromString(JsonPath.parse(registered.getBody()).read("$.sessionId", String.class));
        }
        return session;
    }

    private void upload(final UUID session, final String camera, final int partIndex) {
        final byte[] content = ("clip " + session + " " + camera + " " + partIndex + " ")
                .repeat(CLIP_BYTES).substring(0, CLIP_BYTES).getBytes(StandardCharsets.US_ASCII);
        final String etag = MinioSupport.put(objectKey(session, camera, partIndex), content);
        uploaded.put(key(session, camera, partIndex), new UploadedClip(content.length, etag));
    }

    private void confirm(final String camera, final int partIndex, final String body) {
        rowsBefore = rows(sessionId);
        final int logsBefore = logs.events().size();
        confirmationSentAt = Instant.now();
        final long started = System.nanoTime();
        response = post(sessionId, camera, partIndex, body);
        answeredIn = Duration.ofNanos(System.nanoTime() - started);
        // The service logs on the request thread before it answers, so the request's lines are in by now
        final List<ILoggingEvent> all = logs.events();
        requestLogs = all.subList(logsBefore, all.size()).stream()
                .filter(event -> event.getLoggerName().startsWith(OUR_LOGGERS))
                .toList();
    }

    private ResponseEntity<String> post(final UUID session, final String camera, final int partIndex,
                                        final String body) {
        return http.post()
                .uri(COMPLETE_PATH, session, camera, partIndex)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
    }

    private String objectKey(final UUID session, final String camera, final int partIndex) {
        final UUID eventId = dsl.select(UPLOAD_SESSION.EVENT_ID)
                .from(UPLOAD_SESSION)
                .where(UPLOAD_SESSION.ID.eq(session))
                .fetchSingle(UPLOAD_SESSION.EVENT_ID);
        return "events/" + eventId + "/sessions/" + session + "/" + camera + "/" + partIndex + ".mp4";
    }

    private UploadPartRecord part(final UUID session, final int partIndex) {
        return dsl.selectFrom(UPLOAD_PART)
                .where(UPLOAD_PART.SESSION_ID.eq(session))
                .and(UPLOAD_PART.PART_INDEX.eq(partIndex))
                .fetchSingle();
    }

    /**
     * Every row of the session in the three tables a confirmation writes, in a fixed order.
     */
    private List<Map<String, Object>> rows(final UUID session) {
        final List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(dsl.selectFrom(UPLOAD_SESSION).where(UPLOAD_SESSION.ID.eq(session))
                .fetch().map(Record::intoMap));
        rows.addAll(dsl.selectFrom(UPLOAD_CLIP).where(UPLOAD_CLIP.SESSION_ID.eq(session))
                .orderBy(UPLOAD_CLIP.CAMERA, UPLOAD_CLIP.PART_INDEX).fetch().map(Record::intoMap));
        rows.addAll(dsl.selectFrom(UPLOAD_PART).where(UPLOAD_PART.SESSION_ID.eq(session))
                .orderBy(UPLOAD_PART.PART_INDEX).fetch().map(Record::intoMap));
        return rows;
    }

    private <T> T read(final String path, final Class<T> type) {
        return JsonPath.parse(response.getBody()).read(path, type);
    }

    private static String key(final UUID session, final String camera, final int partIndex) {
        return session + " " + camera + " " + partIndex;
    }

    private static String body(final long sizeBytes, final String etag, final long startMs, final long durationMs) {
        return """
                {"sizeBytes": %d, "etag": %s, "startMs": %d, "durationMs": %d}"""
                .formatted(sizeBytes, JsonMapper.shared().writeValueAsString(etag), startMs, durationMs);
    }

    private static List<String> names(final String commaSeparated) {
        return Arrays.stream(commaSeparated.split(",\\s*")).toList();
    }

    record UploadedClip(long sizeBytes, String etag) {
    }
}

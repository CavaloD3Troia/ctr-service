package org.cavalodetroia.ctr.componenttests.steps;

import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.jooq.DSLContext;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.EVENT;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_CAMERA;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_CLIP;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_PART;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_SESSION;

/**
 * Registers the cameras of many events at once, or one right after the other, then checks that each event ended up
 * with one session holding each camera once and its parts only once. A race shows up only now and then,
 * so each scenario runs it on many events.
 */
public class ConcurrentRegistrationSteps {

    private static final String PATH = "/api/v1/events/{eventId}/upload-session/cameras";
    // Both cameras send the venue: either may be the one that opens the session
    private static final String BODY = """
            {"camera": "%s", "partCount": %d, "contentType": "video/mp4", "venue": {"format": "FOOTBALL_11V11"}}""";

    private final RestClient http;
    private final DSLContext dsl;

    private final List<UUID> events = new ArrayList<>();
    private final Map<UUID, List<ResponseEntity<String>>> responses = new LinkedHashMap<>();

    public ConcurrentRegistrationSteps(@LocalServerPort final int port, final DSLContext dsl) {
        this.http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                // A 4xx or 5xx is something to assert on, not an exception
                .defaultStatusHandler(status -> true, (request, ignored) -> { })
                .build();
        this.dsl = dsl;
    }

    @Given("{int} events exist")
    public void eventsExist(final int count) {
        for (int i = 0; i < count; i++) {
            final UUID eventId = UUID.randomUUID();
            dsl.insertInto(EVENT).set(EVENT.ID, eventId).set(EVENT.NAME, "Event " + i).execute();
            events.add(eventId);
        }
    }

    @When("on each event the LEFT and RIGHT cameras register with {int} parts at the same time")
    public void theCamerasRegisterAtTheSameTime(final int partCount) throws Exception {
        for (final UUID eventId : events) {
            registerBoth(eventId, "LEFT", "RIGHT", partCount, Duration.ZERO);
        }
    }

    @When("on each event the LEFT camera registers with {int} parts, then two RIGHT cameras at the same time")
    public void twoRightCamerasJoinAtTheSameTime(final int partCount) throws Exception {
        for (final UUID eventId : events) {
            assertThat(register(eventId, "LEFT", partCount).getStatusCode().value()).isEqualTo(201);
            registerBoth(eventId, "RIGHT", "RIGHT", partCount, Duration.ZERO);
        }
    }

    @When("on each event the LEFT camera registers with {int} parts and the RIGHT camera up to {int} ms later, "
            + "without waiting for the answer")
    public void theRightCameraRegistersRightAfter(final int partCount, final int maxDelayMs) throws Exception {
        for (int i = 0; i < events.size(); i++) {
            // From 1 ms to the maximum, so some land while the first registration is still writing and some after
            final long delayMs = 1 + (long) i * (maxDelayMs - 1) / Math.max(1, events.size() - 1);
            registerBoth(events.get(i), "LEFT", "RIGHT", partCount, Duration.ofMillis(delayMs));
        }
    }

    @Then("every registration is answered 201, with the same session for both cameras of an event")
    public void everyRegistrationIsAnswered201WithTheSameSession() {
        responses.forEach((eventId, pair) -> {
            assertThat(pair).as("event %s", eventId)
                    .allSatisfy(response -> assertThat(response.getStatusCode().value())
                            .as(response.getBody()).isEqualTo(201));
            assertThat(pair.stream().map(ConcurrentRegistrationSteps::sessionIdOf).distinct())
                    .as("session ids of event %s", eventId)
                    .hasSize(1);
        });
    }

    @Then("on each event one RIGHT camera is answered 201 and the other 409 {string}")
    public void oneRightCameraIsAnswered201AndTheOther409(final String code) {
        responses.forEach((eventId, pair) -> assertThat(pair)
                .as("event %s", eventId)
                .extracting(response -> response.getStatusCode().value())
                .containsExactlyInAnyOrder(201, 409));
        responses.values().stream().flatMap(List::stream)
                .filter(response -> response.getStatusCode().value() == 409)
                .forEach(response -> assertThat(JsonPath.parse(response.getBody()).read("$.code", String.class))
                        .isEqualTo(code));
    }

    @Then("every event has one upload session, with the cameras {string}")
    public void everyEventHasOneUploadSessionWithTheCameras(final String cameras) {
        for (final UUID eventId : events) {
            final List<UUID> sessions = dsl.select(UPLOAD_SESSION.ID).from(UPLOAD_SESSION)
                    .where(UPLOAD_SESSION.EVENT_ID.eq(eventId))
                    .fetch(UPLOAD_SESSION.ID);
            assertThat(sessions).as("sessions of event %s", eventId).hasSize(1);
            assertThat(dsl.select(UPLOAD_CAMERA.CAMERA).from(UPLOAD_CAMERA)
                    .where(UPLOAD_CAMERA.SESSION_ID.eq(sessions.getFirst()))
                    .fetch(UPLOAD_CAMERA.CAMERA))
                    .as("cameras of event %s", eventId)
                    .containsExactlyInAnyOrderElementsOf(Arrays.asList(cameras.split(",\\s*")));
        }
    }

    @Then("every session has {int} waiting parts, created once, and {int} pending clips for each camera")
    public void everySessionHasItsPartsOnceAndItsClips(final int partCount, final int clipCount) {
        for (final UUID sessionId : sessionsOfTheEvents()) {
            assertThat(dsl.select(UPLOAD_PART.PART_INDEX).from(UPLOAD_PART)
                    .where(UPLOAD_PART.SESSION_ID.eq(sessionId))
                    .and(UPLOAD_PART.STATUS.eq("WAITING"))
                    .orderBy(UPLOAD_PART.PART_INDEX)
                    .fetch(UPLOAD_PART.PART_INDEX))
                    .as("parts of session %s", sessionId)
                    .containsExactlyElementsOf(IntStream.range(0, partCount).boxed().toList());
            for (final String camera : List.of("LEFT", "RIGHT")) {
                assertThat(dsl.fetchCount(UPLOAD_CLIP, UPLOAD_CLIP.SESSION_ID.eq(sessionId)
                        .and(UPLOAD_CLIP.CAMERA.eq(camera))
                        .and(UPLOAD_CLIP.STATUS.eq("PENDING"))))
                        .as("clips of %s in session %s", camera, sessionId)
                        .isEqualTo(clipCount);
            }
        }
    }

    @Then("every session is at version {int}")
    public void everySessionIsAtVersion(final int version) {
        assertThat(dsl.select(UPLOAD_SESSION.VERSION).from(UPLOAD_SESSION)
                .where(UPLOAD_SESSION.EVENT_ID.in(events))
                .fetch(UPLOAD_SESSION.VERSION))
                .hasSize(events.size())
                .containsOnly((long) version);
    }

    /**
     * Sends {@code first}, and {@code second} {@code delay} after it, without waiting for the first answer. Both
     * threads wait at the same gate, so with no delay the two requests leave together.
     */
    private void registerBoth(final UUID eventId,
                              final String first,
                              final String second,
                              final int partCount,
                              final Duration delay) throws Exception {
        final CountDownLatch gate = new CountDownLatch(1);
        try (ExecutorService cameras = Executors.newFixedThreadPool(2)) {
            final Future<ResponseEntity<String>> firstAnswer = cameras.submit(() -> {
                gate.await();
                return register(eventId, first, partCount);
            });
            final Future<ResponseEntity<String>> secondAnswer = cameras.submit(() -> {
                gate.await();
                Thread.sleep(delay);
                return register(eventId, second, partCount);
            });
            gate.countDown();
            responses.put(eventId, List.of(firstAnswer.get(), secondAnswer.get()));
        }
    }

    private ResponseEntity<String> register(final UUID eventId, final String camera, final int partCount) {
        return http.post()
                .uri(PATH, eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body(BODY.formatted(camera, partCount))
                .retrieve()
                .toEntity(String.class);
    }

    private List<UUID> sessionsOfTheEvents() {
        return dsl.select(UPLOAD_SESSION.ID).from(UPLOAD_SESSION)
                .where(UPLOAD_SESSION.EVENT_ID.in(events))
                .fetch(UPLOAD_SESSION.ID);
    }

    private static String sessionIdOf(final ResponseEntity<String> response) {
        return JsonPath.parse(response.getBody()).read("$.sessionId", String.class);
    }
}

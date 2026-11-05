package org.cavalodetroia.ctr.componenttests.steps;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.cavalodetroia.ctr.componenttests.support.LogCapture;
import org.jooq.DSLContext;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.EVENT;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.IDEMPOTENCY_KEY;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_CAMERA;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_CLIP;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_PART;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_SESSION;

/**
 * Registers cameras over HTTP, then checks the response, the rows in Postgres and the log lines of the request.
 */
public class RegisterCameraSteps {

    // Written out on purpose: the path, the headers and the bucket are part of the contract
    private static final String PATH = "/api/v1/events/{eventId}/upload-session/cameras";
    private static final String BUCKET = "cdt-videos";
    private static final String CORRELATION_ID = "X-Correlation-Id";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String OUR_LOGGERS = "org.cavalodetroia.ctr.";
    private static final TypeReference<Map<String, Object>> MAP_OF_FIELDS = new TypeReference<>() { };

    private final RestClient http;
    private final DSLContext dsl;
    private final LogCapture logs = new LogCapture();

    // A new event per scenario, so the scenarios don't depend on each other or on their order
    private UUID eventId = UUID.randomUUID();
    private String correlationId;
    private String body;
    private String idempotencyKey;
    private ResponseEntity<String> response;
    private String firstResponseBody;
    private UUID sessionId;
    private List<ILoggingEvent> requestLogs;

    public RegisterCameraSteps(@LocalServerPort final int port, final DSLContext dsl) {
        this.http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                // A 4xx is something to assert on, not an exception
                .defaultStatusHandler(status -> true, (request, ignored) -> { })
                .build();
        this.dsl = dsl;
    }

    @Before
    public void captureLogs() {
        logs.start();
    }

    @After
    public void stopCapturingLogs() {
        logs.stop();
    }

    @Given("an event exists")
    public void anEventExists() {
        dsl.insertInto(EVENT).set(EVENT.ID, eventId).set(EVENT.NAME, "Benfica vs Porto").execute();
    }

    @Given("the event is unknown")
    public void theEventIsUnknown() {
        eventId = UUID.randomUUID();
    }

    @Given("the requests carry the correlation id {string}")
    public void theRequestsCarryTheCorrelationId(final String correlationId) {
        this.correlationId = correlationId;
    }

    @When("the {word} camera registers with {int} parts on a {word} venue measuring {double} by {double} metres")
    public void theCameraRegistersOnAMeasuredVenue(final String camera, final int partCount, final String format,
                                                   final double lengthM, final double widthM) {
        final Map<String, Object> venue = new LinkedHashMap<>();
        venue.put("format", format);
        venue.put("lengthM", lengthM);
        venue.put("widthM", widthM);
        register(camera, partCount, venue);
    }

    @When("the {word} camera registers with {int} parts on a {word} venue without measures")
    public void theCameraRegistersOnAnUnmeasuredVenue(final String camera, final int partCount, final String format) {
        register(camera, partCount, Map.of("format", format));
    }

    @When("the {word} camera registers with {int} parts and the venue {string}")
    public void theCameraRegistersWithTheVenue(final String camera, final int partCount, final String venue) {
        register(camera, partCount, JsonMapper.shared().readValue(venue, MAP_OF_FIELDS));
    }

    @When("the {word} camera registers with {int} parts and no venue")
    public void theCameraRegistersWithoutAVenue(final String camera, final int partCount) {
        register(camera, partCount, null);
    }

    @When("a camera registers with this body")
    public void aCameraRegistersWithThisBody(final String body) {
        send(body, UUID.randomUUID().toString());
    }

    @When("the same request is sent again")
    public void theSameRequestIsSentAgain() {
        send(body, idempotencyKey);
    }

    @Then("the response status is {int}")
    public void theResponseStatusIs(final int status) {
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(status);
    }

    @Then("the response gives the {word} camera its prefix and the clip key {string}")
    public void theResponseGivesTheCameraItsPrefixAndTheClipKey(final String camera, final String clipKey) {
        assertThat(read("$.eventId", String.class)).isEqualTo(eventId.toString());
        assertThat(read("$.status", String.class)).isEqualTo("OPEN");
        assertThat(read("$.camera", String.class)).isEqualTo(camera);
        assertThat(read("$.prefix", String.class))
                .isEqualTo("s3://" + BUCKET + "/events/" + eventId + "/sessions/" + sessionId + "/" + camera + "/");
        assertThat(read("$.clipKey", String.class)).isEqualTo(clipKey);
    }

    @Then("the response has the session id of the first registration")
    public void theResponseHasTheSessionIdOfTheFirstRegistration() {
        assertThat(read("$.sessionId", String.class)).isEqualTo(sessionId.toString());
    }

    @Then("the response lists the cameras {string}, each with {int} parts and none uploaded")
    public void theResponseListsTheCameras(final String cameras, final int partCount) {
        final List<Map<String, Object>> listed = JsonPath.parse(response.getBody()).read("$.cameras");
        assertThat(listed)
                .extracting(camera -> camera.get("camera"), camera -> camera.get("partCount"),
                        camera -> camera.get("partsUploaded"))
                .containsExactlyElementsOf(names(cameras).stream().map(name -> tuple(name, partCount, 0)).toList());
    }

    @Then("the response is the same as the first one")
    public void theResponseIsTheSameAsTheFirstOne() {
        assertThat(JsonMapper.shared().readTree(response.getBody()))
                .isEqualTo(JsonMapper.shared().readTree(firstResponseBody));
    }

    @Then("the response is a problem with the code {string}")
    public void theResponseIsAProblemWithTheCode(final String code) {
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(read("$.code", String.class)).isEqualTo(code);
    }

    @Then("the problem lists the invalid field {string}")
    public void theProblemListsTheInvalidField(final String field) {
        final List<String> fields = JsonPath.parse(response.getBody()).read("$.errors[*].field");
        assertThat(fields).containsExactly(field);
    }

    @Then("the session has the cameras {string} in the database")
    public void theSessionHasTheCamerasInTheDatabase(final String cameras) {
        assertThat(dsl.select(UPLOAD_SESSION.ID)
                .from(UPLOAD_SESSION)
                .where(UPLOAD_SESSION.EVENT_ID.eq(eventId))
                .fetch(UPLOAD_SESSION.ID))
                .containsExactly(sessionId);
        assertThat(dsl.select(UPLOAD_CAMERA.CAMERA)
                .from(UPLOAD_CAMERA)
                .where(UPLOAD_CAMERA.SESSION_ID.eq(sessionId))
                .orderBy(UPLOAD_CAMERA.CREATED_AT)
                .fetch(UPLOAD_CAMERA.CAMERA))
                .containsExactlyElementsOf(names(cameras));
    }

    @Then("the database has {int} pending clips for each of the cameras {string}, keyed by camera and part index")
    public void theDatabaseHasPendingClipsForEachCamera(final int partCount, final String cameras) {
        for (final String camera : names(cameras)) {
            final String folder = "events/" + eventId + "/sessions/" + sessionId + "/" + camera + "/";
            assertThat(dsl.select(UPLOAD_CLIP.PART_INDEX, UPLOAD_CLIP.OBJECT_KEY, UPLOAD_CLIP.STATUS)
                    .from(UPLOAD_CLIP)
                    .where(UPLOAD_CLIP.SESSION_ID.eq(sessionId).and(UPLOAD_CLIP.CAMERA.eq(camera)))
                    .orderBy(UPLOAD_CLIP.PART_INDEX)
                    .fetch(clip -> tuple(clip.value1(), clip.value2(), clip.value3())))
                    .as("clips of %s", camera)
                    .containsExactlyElementsOf(IntStream.range(0, partCount)
                            .mapToObj(index -> tuple(index, folder + index + ".mp4", "PENDING"))
                            .toList());
        }
    }

    @Then("the database has {int} waiting parts for the session")
    public void theDatabaseHasWaitingPartsForTheSession(final int partCount) {
        assertThat(dsl.select(UPLOAD_PART.PART_INDEX, UPLOAD_PART.STATUS)
                .from(UPLOAD_PART)
                .where(UPLOAD_PART.SESSION_ID.eq(sessionId))
                .orderBy(UPLOAD_PART.PART_INDEX)
                .fetch(part -> tuple(part.value1(), part.value2())))
                .containsExactlyElementsOf(IntStream.range(0, partCount)
                        .mapToObj(index -> tuple(index, "WAITING"))
                        .toList());
    }

    @Then("the session venue in the database is {word} measuring {double} by {double} metres")
    public void theSessionVenueInTheDatabaseIs(final String format, final double lengthM, final double widthM) {
        final var venue = dsl.select(UPLOAD_SESSION.VENUE_FORMAT, UPLOAD_SESSION.VENUE_LENGTH_M,
                        UPLOAD_SESSION.VENUE_WIDTH_M)
                .from(UPLOAD_SESSION)
                .where(UPLOAD_SESSION.ID.eq(sessionId))
                .fetchSingle();
        assertThat(venue.value1()).isEqualTo(format);
        assertThat(venue.value2()).isEqualTo((float) lengthM);
        assertThat(venue.value3()).isEqualTo((float) widthM);
    }

    @Then("the event has no upload session")
    public void theEventHasNoUploadSession() {
        assertThat(dsl.fetchCount(UPLOAD_SESSION, UPLOAD_SESSION.EVENT_ID.eq(eventId))).isZero();
    }

    @Then("one response is stored for the Idempotency-Key")
    public void oneResponseIsStoredForTheIdempotencyKey() {
        assertThat(dsl.fetchCount(IDEMPOTENCY_KEY, IDEMPOTENCY_KEY.KEY.eq(idempotencyKey))).isEqualTo(1);
    }

    @Then("the response carries the correlation id {string}")
    public void theResponseCarriesTheCorrelationId(final String expected) {
        assertThat(response.getHeaders().getFirst(CORRELATION_ID)).isEqualTo(expected);
    }

    @Then("the response carries a generated correlation id starting with {string}")
    public void theResponseCarriesAGeneratedCorrelationIdStartingWith(final String prefix) {
        final String generated = response.getHeaders().getFirst(CORRELATION_ID);
        assertThat(generated).startsWith(prefix);
        assertThat(UUID.fromString(generated.substring(prefix.length())).version()).isEqualTo(7);
    }

    @Then("every log line of the request carries that correlation id and the camera {word}")
    public void everyLogLineOfTheRequestCarriesThatCorrelationIdAndTheCamera(final String camera) {
        final String expected = response.getHeaders().getFirst(CORRELATION_ID);
        assertThat(requestLogs).isNotEmpty().allSatisfy(event -> assertThat(event.getMDCPropertyMap())
                .as(event.getFormattedMessage())
                .containsEntry("correlation_id", expected)
                .containsEntry("camera", camera));
    }

    @Then("the log line of the registration carries the session id")
    public void theLogLineOfTheRegistrationCarriesTheSessionId() {
        assertThat(requestLogs)
                .filteredOn(event -> event.getFormattedMessage().contains("message=Camera registered"))
                .singleElement()
                .satisfies(event -> assertThat(event.getMDCPropertyMap())
                        .containsEntry("session_id", sessionId.toString()));
    }

    @Then("a warning says the venue was ignored")
    public void aWarningSaysTheVenueWasIgnored() {
        assertThat(requestLogs)
                .filteredOn(event -> event.getFormattedMessage().contains("message=Venue ignored"))
                .singleElement()
                .satisfies(event -> assertThat(event.getLevel().toString()).isEqualTo("WARN"));
    }

    private void register(final String camera, final int partCount, final Map<String, Object> venue) {
        final Map<String, Object> request = new LinkedHashMap<>();
        request.put("camera", camera);
        request.put("partCount", partCount);
        request.put("contentType", "video/mp4");
        if (venue != null) {
            request.put("venue", venue);
        }
        send(JsonMapper.shared().writeValueAsString(request), UUID.randomUUID().toString());
    }

    private void send(final String body, final String idempotencyKey) {
        this.body = body;
        this.idempotencyKey = idempotencyKey;
        final int logsBefore = logs.events().size();
        response = http.post()
                .uri(PATH, eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .headers(headers -> {
                    if (correlationId != null) {
                        headers.set(CORRELATION_ID, correlationId);
                    }
                })
                .body(body)
                .retrieve()
                .toEntity(String.class);
        // The service logs on the request thread before it answers, so the request's lines are in by now
        final List<ILoggingEvent> all = logs.events();
        requestLogs = all.subList(logsBefore, all.size()).stream()
                .filter(event -> event.getLoggerName().startsWith(OUR_LOGGERS))
                .toList();
        if (response.getStatusCode().value() == 201 && sessionId == null) {
            sessionId = UUID.fromString(read("$.sessionId", String.class));
            firstResponseBody = response.getBody();
        }
    }

    private <T> T read(final String path, final Class<T> type) {
        return JsonPath.parse(response.getBody()).read(path, type);
    }

    private static List<String> names(final String commaSeparated) {
        return Arrays.stream(commaSeparated.split(",\\s*")).toList();
    }
}

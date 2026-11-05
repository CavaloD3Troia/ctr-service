package org.cavalodetroia.ctr.componenttests.steps;

import com.jayway.jsonpath.JsonPath;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.cavalodetroia.ctr.componenttests.support.QueryCounter;
import org.cavalodetroia.ctr.componenttests.support.UploadFixtures;
import org.jooq.DSLContext;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Writes upload rows straight into Postgres, reads the session over HTTP, then checks the body against those rows and
 * the SQL the read ran.
 */
public class GetUploadSessionSteps {

    // Written out on purpose: the path is part of the contract
    private static final String PATH = "/api/v1/upload-sessions/{sessionId}";
    private static final List<String> PART_FIELDS =
            List.of("partIndex", "status", "published", "startMs", "durationMs", "clips");

    private final RestClient http;
    private final UploadFixtures fixtures;
    private final QueryCounter queries;

    // A new event and session per scenario, so the scenarios don't depend on each other or on their order
    private final UUID eventId = UUID.randomUUID();
    private UUID sessionId = UUID.randomUUID();
    private ResponseEntity<String> response;
    private Duration took;
    private List<String> statements;

    public GetUploadSessionSteps(@LocalServerPort final int port, final DSLContext dsl, final QueryCounter queries) {
        this.http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                // A 4xx is something to assert on, not an exception
                .defaultStatusHandler(status -> true, (request, ignored) -> { })
                .build();
        this.fixtures = new UploadFixtures(dsl);
        this.queries = queries;
    }

    @Given("an open upload session with the cameras {string} and {int} parts")
    public void anOpenUploadSession(final String cameras, final int partCount) {
        fixtures.openSession(eventId, sessionId, names(cameras), partCount);
    }

    @Given("these clips are uploaded")
    public void theseClipsAreUploaded(final DataTable clips) {
        for (final Map<String, String> clip : clips.asMaps()) {
            fixtures.uploadClip(sessionId, clip.get("camera"), Integer.parseInt(clip.get("partIndex")),
                    Long.parseLong(clip.get("sizeBytes")), Long.parseLong(clip.get("startMs")),
                    Long.parseLong(clip.get("durationMs")), Instant.parse(clip.get("uploadedAt")));
        }
    }

    @Given("every clip of the {word} camera is uploaded")
    public void everyClipOfTheCameraIsUploaded(final String camera) {
        fixtures.uploadEveryClip(sessionId, camera);
    }

    @Given("these parts are done")
    public void thesePartsAreDone(final DataTable parts) {
        for (final Map<String, String> part : parts.asMaps()) {
            final String publishedAt = part.get("publishedAt");
            fixtures.finishPart(sessionId, Integer.parseInt(part.get("partIndex")), part.get("status"),
                    Long.parseLong(part.get("startMs")), Long.parseLong(part.get("durationMs")),
                    publishedAt == null ? null : Instant.parse(publishedAt));
        }
    }

    @Given("the upload session is unknown")
    public void theUploadSessionIsUnknown() {
        sessionId = UUID.randomUUID();
    }

    @When("the upload session is read")
    public void theUploadSessionIsRead() {
        queries.reset();
        final long start = System.nanoTime();
        response = http.get().uri(PATH, sessionId).retrieve().toEntity(String.class);
        took = Duration.ofNanos(System.nanoTime() - start);
        statements = queries.statements();
    }

    @Then("the read answers {int}")
    public void theReadAnswers(final int status) {
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(status);
    }

    @Then("the read gives the session on its event, {word}, with {int} parts and {int} published")
    public void theReadGivesTheSession(final String status, final int partCount, final int partsPublished) {
        assertThat(read("$.sessionId", String.class)).isEqualTo(sessionId.toString());
        assertThat(read("$.eventId", String.class)).isEqualTo(eventId.toString());
        assertThat(read("$.status", String.class)).isEqualTo(status);
        assertThat(read("$.partCount", Integer.class)).isEqualTo(partCount);
        assertThat(read("$.partsPublished", Integer.class)).isEqualTo(partsPublished);
    }

    @Then("the read gives the venue {word} measuring {double} by {double} metres")
    public void theReadGivesTheVenue(final String format, final double lengthM, final double widthM) {
        assertThat(read("$.venue.format", String.class)).isEqualTo(format);
        assertThat(read("$.venue.lengthM", Double.class)).isEqualTo(lengthM);
        assertThat(read("$.venue.widthM", Double.class)).isEqualTo(widthM);
    }

    @Then("the read lists the cameras")
    public void theReadListsTheCameras(final DataTable cameras) {
        final List<Map<String, Object>> listed = JsonPath.parse(response.getBody()).read("$.cameras");
        final List<Map<String, String>> expected = cameras.asMaps();
        assertThat(listed).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            final Map<String, String> camera = expected.get(i);
            assertThat(asText(listed.get(i))).as("camera %d", i).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "camera", camera.get("camera"),
                    "partCount", camera.get("partCount"),
                    "partsUploaded", camera.get("partsUploaded"),
                    "prefix", UploadFixtures.prefix(eventId, sessionId, camera.get("camera"))));
        }
    }

    // Every part field is there; the window is null where the table leaves it blank
    @Then("the read lists the parts")
    public void theReadListsTheParts(final DataTable parts) {
        final List<Map<String, Object>> listed = JsonPath.parse(response.getBody()).read("$.parts");
        final List<Map<String, String>> expected = parts.asMaps();
        assertThat(listed).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            final Map<String, Object> part = listed.get(i);
            assertThat(part).as("part %d", i).containsOnlyKeys(PART_FIELDS);
            for (final Map.Entry<String, String> field : expected.get(i).entrySet()) {
                assertThat(text(part.get(field.getKey()))).as("part %d %s", i, field.getKey())
                        .isEqualTo(field.getValue());
            }
        }
    }

    // A blank cell is a field the clip leaves out
    @Then("the read gives part {int} the clips")
    public void theReadGivesPartTheClips(final int partIndex, final DataTable clips) {
        final List<Map<String, Object>> listed =
                JsonPath.parse(response.getBody()).read("$.parts[" + partIndex + "].clips");
        final List<Map<String, String>> expected = clips.asMaps();
        assertThat(listed).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            final Map<String, String> given = expected.get(i).entrySet().stream()
                    .filter(field -> field.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            assertThat(asText(listed.get(i))).as("clip %d of part %d", i, partIndex)
                    .containsExactlyInAnyOrderEntriesOf(given);
        }
    }

    @Then("every part has only a pending clip of the camera {word}")
    public void everyPartHasOnlyAPendingClipOf(final String camera) {
        final List<List<Map<String, Object>>> clips = JsonPath.parse(response.getBody()).read("$.parts[*].clips");
        assertThat(clips).isNotEmpty().allSatisfy(partClips -> assertThat(partClips)
                .singleElement()
                .isEqualTo(Map.of("camera", camera, "status", "PENDING")));
    }

    @Then("the read lists {int} parts by index, each waiting for RIGHT with the clips of {string}")
    public void theReadListsPartsByIndex(final int partCount, final String cameras) {
        final List<Integer> indexes = JsonPath.parse(response.getBody()).read("$.parts[*].partIndex");
        assertThat(indexes).hasSize(partCount).isSorted().doesNotHaveDuplicates()
                .startsWith(0).endsWith(partCount - 1);
        final List<String> states = JsonPath.parse(response.getBody()).read("$.parts[*].status");
        assertThat(states).containsOnly("WAITING_RIGHT");
        for (final String camera : names(cameras)) {
            final List<String> clips = JsonPath.parse(response.getBody())
                    .read("$.parts[*].clips[?(@.camera == '" + camera + "')].camera");
            assertThat(clips).as("clips of %s", camera).hasSize(partCount);
        }
    }

    @Then("the read is a problem with the code {string}")
    public void theReadIsAProblemWithTheCode(final String code) {
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(read("$.code", String.class)).isEqualTo(code);
    }

    // Nothing but SELECTs: the read writes nothing
    @Then("the read ran at most {int} SQL statements, all of them SELECTs")
    public void theReadRanAtMostStatements(final int max) {
        assertThat(statements).isNotEmpty().hasSizeLessThanOrEqualTo(max)
                .allSatisfy(sql -> assertThat(sql.stripLeading().toLowerCase(Locale.ROOT)).startsWith("select"));
    }

    @Then("the read took less than {int} seconds")
    public void theReadTookLessThan(final int seconds) {
        assertThat(took).isLessThan(Duration.ofSeconds(seconds));
    }

    private <T> T read(final String path, final Class<T> type) {
        return JsonPath.parse(response.getBody()).read(path, type);
    }

    // Clip and camera objects have no null fields: a clip leaves out what it doesn't know
    private static Map<String, String> asText(final Map<String, Object> json) {
        return json.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, field -> text(field.getValue())));
    }

    private static String text(final Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> names(final String commaSeparated) {
        return Arrays.stream(commaSeparated.split(",\\s*")).toList();
    }
}

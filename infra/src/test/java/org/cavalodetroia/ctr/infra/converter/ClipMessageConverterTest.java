package org.cavalodetroia.ctr.infra.converter;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import org.cavalodetroia.contracts.clip.v1.ClipPairUploaded;
import org.cavalodetroia.contracts.clip.v1.ClipReady;
import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.CameraRegistration;
import org.cavalodetroia.ctr.domain.model.upload.ClipConfirmation;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.model.venue.RequestedVenue;
import org.cavalodetroia.ctr.domain.model.venue.VenueFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TH-152 AC-1, AC-2, AC-10: a completed part to its message. The LEFT and RIGHT case is checked against the
 * ClipPairUploaded fixture of GlobalProtobufModels (TH-133), with the fixture's placeholder ids replaced by real
 * UUIDs; its ClipReady fixture is a stitched clip, which ctr-service never sends, so the SINGLE case is checked
 * against the ticket's example.
 */
class ClipMessageConverterTest {

    private static final UUID EVENT_ID = UUID.fromString("0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11");
    private static final UUID SESSION_ID = UUID.fromString("019a5b2e-7f3c-7000-8000-000000000001");
    private static final String BUCKET = "cdt-videos";
    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");

    private final ClipMessageConverter converter = new ClipMessageConverter();

    @Test
    void should_matchTheContractsFixture_when_convertingALeftAndRightPart() throws IOException {
        // Part 3 of 40 on a standard 11-a-side pitch. RIGHT runs 2 s longer than LEFT, so the window is the overlap,
        // not either clip's own
        final UploadSession left = UploadSession.open(SESSION_ID, EVENT_ID, new CameraRegistration(Camera.LEFT, 40,
                "video/mp4", new RequestedVenue("FOOTBALL_11V11", null, null)), BUCKET, NOW).session();
        final UploadSession session = left
                .join(new CameraRegistration(Camera.RIGHT, 40, "video/mp4", null), BUCKET, NOW).session()
                .completeClip(Camera.LEFT, 3, clip(1_073_741_824L, 900_000, 300_000), NOW).after()
                .completeClip(Camera.RIGHT, 3, clip(1_052_000_000L, 898_000, 302_000), NOW).after();

        final Message message = converter.toMessage(session, 3);

        assertThat(message).isEqualTo(fixture("fixtures/clip_pair_uploaded.json", ClipPairUploaded.newBuilder()));
    }

    @Test
    void should_giveTheClipReadyOfTheTicket_when_convertingASingleCamerasPart() throws IOException {
        final UploadSession session = UploadSession.open(SESSION_ID, EVENT_ID, new CameraRegistration(Camera.SINGLE,
                        12, "video/mp4", new RequestedVenue("FUTSAL_5V5", null, null)), BUCKET, NOW).session()
                .completeClip(Camera.SINGLE, 0, clip(640_000_000L, 0, 180_000), NOW).after();

        final Message message = converter.toMessage(session, 0);

        final ClipReady.Builder expected = ClipReady.newBuilder();
        JsonFormat.parser().merge(withRealIds("""
                {
                  "sessionId": "sess-7f3c",
                  "eventId": "evt-1",
                  "partIndex": 0,
                  "partCount": 12,
                  "startMs": 0,
                  "durationMs": 180000,
                  "clip": {
                    "uri": "s3://cdt-videos/events/evt-1/sessions/sess-7f3c/SINGLE/0.mp4",
                    "sizeBytes": 640000000,
                    "contentType": "video/mp4"
                  },
                  "venue": {"sport": "futsal", "format": "FUTSAL_5V5", "lengthM": 40, "widthM": 20}
                }"""), expected);
        assertThat(message).isEqualTo(expected.build());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"FUTSAL_5V5, futsal", "FOOTBALL_7V7, football", "FOOTBALL_11V11, football"})
    void should_deriveTheSportFromTheFormat(final VenueFormat format, final String sport) {
        assertThat(ClipMessageConverter.sport(format)).isEqualTo(sport);
    }

    // A format added to VenueFormat breaks the build here until the converter knows its sport
    @ParameterizedTest(name = "{0}")
    @EnumSource(VenueFormat.class)
    void should_knowTheSportOfEveryFormat(final VenueFormat format) {
        assertThat(ClipMessageConverter.sport(format)).isIn("futsal", "football");
    }

    // The content type is the one the camera registered, not a constant
    @Test
    void should_carryTheContentTypeTheCameraRegistered_when_convertingAClip() {
        final UploadSession session = UploadSession.open(SESSION_ID, EVENT_ID, new CameraRegistration(Camera.SINGLE,
                        1, "video/quicktime", new RequestedVenue("FOOTBALL_7V7", 60.5, 40.25)), "other-bucket", NOW)
                .session()
                .completeClip(Camera.SINGLE, 0, clip(10L, 0, 1_000), NOW).after();

        final ClipReady message = (ClipReady) converter.toMessage(session, 0);

        assertThat(message.getClip().getContentType()).isEqualTo("video/quicktime");
        assertThat(message.getClip().getUri()).isEqualTo("s3://other-bucket/events/" + EVENT_ID + "/sessions/"
                + SESSION_ID + "/SINGLE/0.mp4");
        assertThat(message.getVenue().getLengthM()).isEqualTo(60.5f);
        assertThat(message.getVenue().getWidthM()).isEqualTo(40.25f);
    }

    @Test
    void should_keyThePartBySessionAndPartIndex() {
        final UploadSession session = UploadSession.open(SESSION_ID, EVENT_ID, new CameraRegistration(Camera.SINGLE,
                12, "video/mp4", new RequestedVenue("FUTSAL_5V5", null, null)), BUCKET, NOW).session();

        assertThat(converter.toKey(session, 3)).isEqualTo(SESSION_ID + ":3");
    }

    // AC-5: a NO_OVERLAP part, like a waiting one, has nothing to publish
    @Test
    void should_reject_when_thePartIsNotComplete() {
        final UploadSession session = UploadSession.open(SESSION_ID, EVENT_ID, new CameraRegistration(Camera.LEFT, 2,
                        "video/mp4", new RequestedVenue("FOOTBALL_11V11", null, null)), BUCKET, NOW).session()
                .join(new CameraRegistration(Camera.RIGHT, 2, "video/mp4", null), BUCKET, NOW).session()
                .completeClip(Camera.LEFT, 0, clip(10L, 0, 1_000), NOW).after()
                .completeClip(Camera.RIGHT, 0, clip(10L, 5_000, 1_000), NOW).after();

        assertThatThrownBy(() -> converter.toMessage(session, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NO_OVERLAP");
        assertThatThrownBy(() -> converter.toMessage(session, 1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WAITING");
    }

    private static ClipConfirmation clip(final long sizeBytes, final long startMs, final long durationMs) {
        return new ClipConfirmation(sizeBytes, "9b2cf535f27731c974343645a3985328", startMs, durationMs);
    }

    private static <B extends Message.Builder> Message fixture(final String resource, final B builder)
            throws IOException {
        try (InputStream json = ClipMessageConverterTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(json).as(resource).isNotNull();
            JsonFormat.parser().merge(withRealIds(new String(json.readAllBytes(), StandardCharsets.UTF_8)), builder);
        }
        return builder.build();
    }

    // The fixtures' ids are placeholders; ctr-service's are UUIDs, in the ids and in every uri
    private static String withRealIds(final String json) {
        return json.replace("sess-7f3c", SESSION_ID.toString()).replace("evt-1", EVENT_ID.toString());
    }
}

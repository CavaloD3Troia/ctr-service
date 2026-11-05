package org.cavalodetroia.ctr.api.converter;

import org.cavalodetroia.ctr.api.domain.upload.CameraResponse;
import org.cavalodetroia.ctr.api.domain.upload.ClipResponse;
import org.cavalodetroia.ctr.api.domain.upload.PartResponse;
import org.cavalodetroia.ctr.api.domain.upload.UploadSessionResponse;
import org.cavalodetroia.ctr.api.domain.venue.VenueResponse;
import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.ClipStatus;
import org.cavalodetroia.ctr.domain.model.upload.PartState;
import org.cavalodetroia.ctr.domain.model.upload.SessionStatus;
import org.cavalodetroia.ctr.domain.model.venue.VenueFormat;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.cavalodetroia.ctr.api.Registrations.EVENT_ID;
import static org.cavalodetroia.ctr.api.Registrations.LEFT_UPLOADED_AT;
import static org.cavalodetroia.ctr.api.Registrations.RIGHT_UPLOADED_AT;
import static org.cavalodetroia.ctr.api.Registrations.SESSION_ID;
import static org.cavalodetroia.ctr.api.Registrations.leftOpened;
import static org.cavalodetroia.ctr.api.Registrations.mixedRightThenLeft;
import static org.cavalodetroia.ctr.api.Registrations.singleOpened;

/**
 * AC-2: every part once, by index, with its state, publication, window and one clip per registered camera; AC-3:
 * partsPublished and each camera's partsUploaded.
 */
class UploadSessionConverterGetTest {

    private static final String SESSION_FOLDER = "s3://cdt-videos/events/" + EVENT_ID + "/sessions/" + SESSION_ID + "/";

    private final UploadSessionConverter converter = new UploadSessionConverter();

    @Test
    void should_giveTheSessionItsVenueAndCounts_when_partsAreMixed() {
        final UploadSessionResponse response = converter.toResponse(mixedRightThenLeft());

        assertThat(response.sessionId()).isEqualTo(SESSION_ID);
        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(response.status()).isEqualTo(SessionStatus.OPEN);
        assertThat(response.partCount()).isEqualTo(4);
        assertThat(response.venue()).isEqualTo(new VenueResponse(VenueFormat.FOOTBALL_11V11, 105, 68));
        // published_at, not COMPLETE: part 1 is complete but not published
        assertThat(response.partsPublished()).isEqualTo(1);
    }

    // RIGHT registered first, yet LEFT is listed first
    @Test
    void should_listTheCamerasLeftBeforeRightWithTheirUploadsAndPrefix_when_partsAreMixed() {
        final UploadSessionResponse response = converter.toResponse(mixedRightThenLeft());

        assertThat(response.cameras()).containsExactly(
                new CameraResponse(Camera.LEFT, 4, 4, SESSION_FOLDER + "LEFT/"),
                new CameraResponse(Camera.RIGHT, 4, 3, SESSION_FOLDER + "RIGHT/"));
    }

    @Test
    void should_giveEveryPartOnceWithItsStateAndWindow_when_partsAreMixed() {
        final UploadSessionResponse response = converter.toResponse(mixedRightThenLeft());

        assertThat(response.parts())
                .extracting(PartResponse::partIndex, PartResponse::status, PartResponse::published,
                        PartResponse::startMs, PartResponse::durationMs)
                .containsExactly(
                        tuple(0, PartState.COMPLETE, true, 2_000L, 298_000L),
                        tuple(1, PartState.COMPLETE, false, 300_000L, 300_000L),
                        tuple(2, PartState.WAITING_RIGHT, false, null, null),
                        // The stored window of a NO_OVERLAP part is not a window to cut
                        tuple(3, PartState.NO_OVERLAP, false, null, null));
    }

    @Test
    void should_giveOneClipPerCameraLeftBeforeRight_when_aPartWaitsForRight() {
        final PartResponse waiting = converter.toResponse(mixedRightThenLeft()).parts().get(2);

        assertThat(waiting.clips()).containsExactly(
                new ClipResponse(Camera.LEFT, ClipStatus.UPLOADED, 1_073_741_824L, 600_000L, 300_000L,
                        LEFT_UPLOADED_AT),
                new ClipResponse(Camera.RIGHT, ClipStatus.PENDING, null, null, null, null));
    }

    @Test
    void should_keepEachClipsOwnWindow_when_thePartHasNoOverlap() {
        final PartResponse noOverlap = converter.toResponse(mixedRightThenLeft()).parts().get(3);

        assertThat(noOverlap.clips())
                .extracting(ClipResponse::camera, ClipResponse::startMs, ClipResponse::durationMs,
                        ClipResponse::uploadedAt)
                .containsExactly(
                        tuple(Camera.LEFT, 900_000L, 300_000L, LEFT_UPLOADED_AT),
                        tuple(Camera.RIGHT, 1_202_000L, 300_000L, RIGHT_UPLOADED_AT));
    }

    @Test
    void should_waitWithOnePendingClipPerPart_when_aSingleCameraHasUploadedNothing() {
        final UploadSessionResponse response = converter.toResponse(singleOpened(3));

        assertThat(response.cameras())
                .containsExactly(new CameraResponse(Camera.SINGLE, 3, 0, SESSION_FOLDER + "SINGLE/"));
        assertThat(response.partsPublished()).isZero();
        assertThat(response.parts()).containsExactly(waitingAlone(0), waitingAlone(1), waitingAlone(2));
    }

    // A LEFT-only session lists one clip per part, and still waits for RIGHT
    @Test
    void should_listOnlyTheRegisteredCamerasClips_when_onlyLeftIsRegistered() {
        final PartResponse first = converter.toResponse(leftOpened().session()).parts().getFirst();

        assertThat(first.status()).isEqualTo(PartState.WAITING_LEFT);
        assertThat(first.clips()).extracting(ClipResponse::camera).containsExactly(Camera.LEFT);
    }

    // PD-3: a part always carries its window, null or not; a clip leaves out what it doesn't know yet
    @Test
    void should_writeNullWindowsButLeaveOutUnknownClipFields_when_serialised() {
        final PartResponse waiting = converter.toResponse(mixedRightThenLeft()).parts().get(2);

        assertThat(JsonMapper.shared().writeValueAsString(waiting)).isEqualTo("""
                {"partIndex":2,"status":"WAITING_RIGHT","published":false,"startMs":null,"durationMs":null,\
                "clips":[{"camera":"LEFT","status":"UPLOADED","sizeBytes":1073741824,"startMs":600000,\
                "durationMs":300000,"uploadedAt":"2026-11-04T18:07:02Z"},{"camera":"RIGHT","status":"PENDING"}]}""");
    }

    private static PartResponse waitingAlone(final int partIndex) {
        return new PartResponse(partIndex, PartState.WAITING, false, null, null,
                List.of(new ClipResponse(Camera.SINGLE, ClipStatus.PENDING, null, null, null, null)));
    }
}

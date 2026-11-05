package org.cavalodetroia.ctr.domain.model.upload;

import org.cavalodetroia.ctr.domain.exception.CameraSetConflictException;
import org.cavalodetroia.ctr.domain.exception.PartCountMismatchException;
import org.cavalodetroia.ctr.domain.exception.VenueSpecException;
import org.cavalodetroia.ctr.domain.model.venue.RequestedVenue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.BUCKET;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.EVENT_ID;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.JOINED_AT;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.OPENED_AT;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.REQUESTED_VENUE;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.SESSION_ID;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.VENUE;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.first;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.joining;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.leftAndRight;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.openedBy;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.withUploaded;

/**
 * AC-11: the camera set, part count and venue rules of a session, and what the first and later cameras create
 * (AC-1, AC-2, AC-3, AC-4, AC-5).
 */
class UploadSessionTest {

    private static final String KEY_PREFIX = "events/" + EVENT_ID + "/sessions/" + SESSION_ID + "/";

    @Test
    void should_openSessionWithPendingClipsAndWaitingParts_when_firstCameraRegisters() {
        final RegistrationResult result = UploadSession.open(SESSION_ID, EVENT_ID, first(Camera.LEFT), BUCKET, OPENED_AT);

        final UploadSession session = result.session();
        assertThat(session.id()).isEqualTo(SESSION_ID);
        assertThat(session.eventId()).isEqualTo(EVENT_ID);
        assertThat(session.status()).isEqualTo(SessionStatus.OPEN);
        assertThat(session.venue()).isEqualTo(VENUE);
        assertThat(session.createdAt()).isEqualTo(OPENED_AT);
        assertThat(session.cameras()).containsExactly(result.camera());
        assertThat(result.camera()).isEqualTo(new UploadCamera(Camera.LEFT, 3,
                "s3://cdt-videos/" + KEY_PREFIX + "LEFT/", OPENED_AT));
        assertThat(session.clips())
                .extracting(UploadClip::camera, UploadClip::partIndex, UploadClip::objectKey, UploadClip::status)
                .containsExactly(
                        tuple(Camera.LEFT, 0, KEY_PREFIX + "LEFT/0.mp4", ClipStatus.PENDING),
                        tuple(Camera.LEFT, 1, KEY_PREFIX + "LEFT/1.mp4", ClipStatus.PENDING),
                        tuple(Camera.LEFT, 2, KEY_PREFIX + "LEFT/2.mp4", ClipStatus.PENDING));
        assertThat(session.parts()).containsExactly(
                UploadPart.waiting(0),
                UploadPart.waiting(1),
                UploadPart.waiting(2));
        assertThat(result.venueIgnored()).isFalse();
        assertThat(session.version()).isZero();
    }

    // The repository stores the joined session only over the version it was read at, one below the new one
    @Test
    void should_comeBackOneVersionUp_when_aCameraJoins() {
        final UploadSession left = openedBy(Camera.LEFT);

        final UploadSession joined = left.join(joining(Camera.RIGHT), BUCKET, JOINED_AT).session();

        assertThat(joined.version()).isEqualTo(left.version() + 1);
        assertThat(joined.updatedAt()).isEqualTo(JOINED_AT);
    }

    @Test
    void should_rejectOpening_when_theFirstCameraHasNoVenue() {
        assertThatThrownBy(() -> UploadSession.open(SESSION_ID, EVENT_ID, joining(Camera.LEFT), BUCKET, OPENED_AT))
                .isInstanceOfSatisfying(VenueSpecException.class,
                        e -> assertThat(e.field()).isEqualTo("venue"));
    }

    @ParameterizedTest(name = "format {0}")
    @CsvSource(value = {"NULL", "HOCKEY", "football_11v11"}, nullValues = "NULL")
    void should_rejectOpening_when_theFirstCameraHasNoValidVenueFormat(final String format) {
        final CameraRegistration first = new CameraRegistration(Camera.LEFT, 3, new RequestedVenue(format, null, null));

        assertThatThrownBy(() -> UploadSession.open(SESSION_ID, EVENT_ID, first, BUCKET, OPENED_AT))
                .isInstanceOfSatisfying(VenueSpecException.class,
                        e -> assertThat(e.field()).isEqualTo("venue.format"));
    }

    @Test
    void should_joinWithItsOwnPrefixAndClipsButNoNewParts_when_rightFollowsLeftWithTheSamePartCount() {
        final UploadSession left = openedBy(Camera.LEFT);

        final RegistrationResult result = left.join(joining(Camera.RIGHT), BUCKET, JOINED_AT);

        final UploadSession session = result.session();
        assertThat(session.id()).isEqualTo(SESSION_ID);
        assertThat(session.updatedAt()).isEqualTo(JOINED_AT);
        assertThat(session.createdAt()).isEqualTo(OPENED_AT);
        assertThat(session.cameras()).extracting(UploadCamera::camera).containsExactly(Camera.LEFT, Camera.RIGHT);
        assertThat(result.camera().prefix()).isEqualTo("s3://cdt-videos/" + KEY_PREFIX + "RIGHT/");
        assertThat(session.clips()).filteredOn(clip -> clip.camera() == Camera.RIGHT)
                .extracting(UploadClip::objectKey)
                .containsExactly(KEY_PREFIX + "RIGHT/0.mp4", KEY_PREFIX + "RIGHT/1.mp4", KEY_PREFIX + "RIGHT/2.mp4");
        assertThat(session.clips()).hasSize(6).allMatch(clip -> clip.status() == ClipStatus.PENDING);
        assertThat(session.parts()).isEqualTo(left.parts());
    }

    @Test
    void should_joinLeft_when_rightOpenedTheSession() {
        final RegistrationResult result = openedBy(Camera.RIGHT).join(joining(Camera.LEFT), BUCKET, JOINED_AT);

        assertThat(result.session().cameras()).extracting(UploadCamera::camera)
                .containsExactly(Camera.RIGHT, Camera.LEFT);
    }

    @ParameterizedTest(name = "{1} after {0}")
    @CsvSource({
            "LEFT,   SINGLE",
            "RIGHT,  SINGLE",
            "SINGLE, LEFT",
            "SINGLE, RIGHT",
            "SINGLE, SINGLE",
            "LEFT,   LEFT",
            "RIGHT,  RIGHT"})
    void should_rejectTheCamera_when_theSessionWouldEndWithAnotherCameraSet(final Camera registered,
                                                                           final Camera next) {
        final UploadSession session = openedBy(registered);

        assertThatThrownBy(() -> session.join(joining(next), BUCKET, JOINED_AT))
                .isInstanceOf(CameraSetConflictException.class)
                .hasMessageContaining(next.name());
    }

    @Test
    void should_rejectAThirdCamera_when_leftAndRightAreRegistered() {
        final UploadSession both = openedBy(Camera.LEFT).join(joining(Camera.RIGHT), BUCKET, JOINED_AT).session();

        assertThatThrownBy(() -> both.join(joining(Camera.SINGLE), BUCKET, JOINED_AT))
                .isInstanceOf(CameraSetConflictException.class);
    }

    @Test
    void should_rejectTheCamera_when_itBringsADifferentPartCount() {
        final UploadSession left = openedBy(Camera.LEFT);

        assertThatThrownBy(() -> left.join(joining(Camera.RIGHT, 4), BUCKET, JOINED_AT))
                .isInstanceOf(PartCountMismatchException.class)
                .hasMessageContaining("3")
                .hasMessageContaining("4");
    }

    @Test
    void should_reportTheCameraSetConflict_when_theCameraAlsoBringsADifferentPartCount() {
        final UploadSession left = openedBy(Camera.LEFT);

        assertThatThrownBy(() -> left.join(joining(Camera.SINGLE, 4), BUCKET, JOINED_AT))
                .isInstanceOf(CameraSetConflictException.class);
    }

    // A later camera's venue is ignored whatever it holds: the format is only checked on the first camera
    @ParameterizedTest(name = "format {0}, {1} x {2}")
    @CsvSource(value = {"FUTSAL_5V5, NULL, NULL", "NULL, NULL, NULL", "HOCKEY, NULL, NULL", "NULL, 100, 60"},
            nullValues = "NULL")
    void should_keepTheFirstVenueAndFlagTheIgnoredOne_when_aJoiningCameraSendsAVenue(final String format,
                                                                                    final Double lengthM,
                                                                                    final Double widthM) {
        final UploadSession left = openedBy(Camera.LEFT);
        final RequestedVenue other = new RequestedVenue(format, lengthM, widthM);

        final RegistrationResult result = left.join(new CameraRegistration(Camera.RIGHT, 3, other), BUCKET, JOINED_AT);

        assertThat(result.session().venue()).isEqualTo(VENUE);
        assertThat(result.venueIgnored()).isTrue();
    }

    @Test
    void should_notFlagTheVenue_when_aJoiningCameraSendsNone() {
        final RegistrationResult result = openedBy(Camera.LEFT).join(joining(Camera.RIGHT), BUCKET, JOINED_AT);

        assertThat(result.venueIgnored()).isFalse();
    }

    @Test
    void should_rejectJoining_when_theSessionIsCompleted() {
        final UploadSession open = openedBy(Camera.LEFT);
        final UploadSession completed = new UploadSession(open.id(), open.eventId(), SessionStatus.COMPLETED,
                open.venue(), open.createdAt(), open.updatedAt(), open.version(), open.cameras(), open.clips(),
                open.parts());

        assertThatThrownBy(() -> completed.join(joining(Camera.RIGHT), BUCKET, JOINED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "uploaded clips {0} -> {1}")
    @CsvSource({"'', 0", "'0', 1", "'0,2', 2"})
    void should_countTheUploadedClipsOfTheCamera_when_askedForPartsUploaded(final String uploaded,
                                                                          final int expected) {
        final int[] partIndexes = Arrays.stream(uploaded.split(","))
                .filter(index -> !index.isEmpty())
                .mapToInt(Integer::parseInt)
                .toArray();

        final UploadSession session = withUploaded(leftAndRight(), Camera.LEFT, partIndexes);

        assertThat(session.partsUploaded(Camera.LEFT)).isEqualTo(expected);
        assertThat(session.partsUploaded(Camera.RIGHT)).isZero();
    }

    @Test
    void should_rejectTheRegistration_when_partCountIsOutsideOneToAThousand() {
        assertThatThrownBy(() -> new CameraRegistration(Camera.LEFT, 0, REQUESTED_VENUE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CameraRegistration(Camera.LEFT, 1001, REQUESTED_VENUE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new CameraRegistration(Camera.LEFT, 1, REQUESTED_VENUE).partCount()).isEqualTo(1);
        assertThat(new CameraRegistration(Camera.LEFT, 1000, REQUESTED_VENUE).partCount()).isEqualTo(1000);
    }
}

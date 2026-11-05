package org.cavalodetroia.ctr.domain.model.upload;

import org.cavalodetroia.ctr.domain.exception.ClipAlreadyCompletedException;
import org.cavalodetroia.ctr.domain.exception.PartIndexOutOfRangeException;
import org.cavalodetroia.ctr.domain.exception.UnknownCameraException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.BUCKET;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.EVENT_ID;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.JOINED_AT;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.OPENED_AT;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.REQUESTED_VENUE;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.SESSION_ID;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.joining;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.openedBy;
import static org.cavalodetroia.ctr.domain.model.upload.ClipConfirmations.UPLOADED_AT;
import static org.cavalodetroia.ctr.domain.model.upload.ClipConfirmations.allUploadedBy;
import static org.cavalodetroia.ctr.domain.model.upload.ClipConfirmations.confirmation;

/**
 * AC-10: the transitions of clip, part and session when a camera confirms a clip, without Spring. AC-1 (the clip
 * becomes UPLOADED), AC-2 (the part closes with its window), AC-3 (NO_OVERLAP), AC-6 (unknown camera, index out of
 * range, a conflicting repeat), AC-7 (the same repeat changes nothing), AC-8 (the last clip completes the session).
 */
class UploadSessionCompleteClipTest {

    private static UploadSession leftAndRight() {
        return openedBy(Camera.LEFT).join(joining(Camera.RIGHT), BUCKET, JOINED_AT).session();
    }

    @Test
    void should_uploadTheClipAndWaitForRight_when_leftConfirmsFirst() {
        final UploadSession session = leftAndRight();

        final ClipCompletion completion = session.completeClip(Camera.LEFT, 1, confirmation(), UPLOADED_AT);

        assertThat(completion.part()).isEqualTo(PartTransition.NONE);
        assertThat(completion.noop()).isFalse();
        assertThat(completion.sessionCompleted()).isFalse();
        assertThat(completion.clip()).isEqualTo(session.clip(Camera.LEFT, 1).uploaded(confirmation(), UPLOADED_AT));
        assertThat(completion.clip().status()).isEqualTo(ClipStatus.UPLOADED);
        assertThat(completion.partState()).isEqualTo(PartState.WAITING_RIGHT);
        assertThat(completion.after().parts()).isEqualTo(session.parts());
        assertThat(completion.after().status()).isEqualTo(SessionStatus.OPEN);
    }

    @Test
    void should_waitForLeft_when_rightConfirmsFirst() {
        final ClipCompletion completion = leftAndRight().completeClip(Camera.RIGHT, 0, confirmation(), UPLOADED_AT);

        assertThat(completion.partState()).isEqualTo(PartState.WAITING_LEFT);
    }

    @Test
    void should_completeThePartWithTheOverlap_when_rightConfirmsAfterLeft() {
        final UploadSession left = leftAndRight()
                .completeClip(Camera.LEFT, 1, confirmation(900_000, 300_000), UPLOADED_AT).after();

        final ClipCompletion completion = left.completeClip(Camera.RIGHT, 1, confirmation(898_000, 300_000),
                UPLOADED_AT);

        final Window overlap = new Window(900_000, 298_000);
        assertThat(completion.part()).isEqualTo(new PartTransition.Complete(overlap));
        assertThat(completion.partState()).isEqualTo(PartState.COMPLETE);
        assertThat(completion.after().parts().get(1))
                .isEqualTo(new UploadPart(1, PartStatus.COMPLETE, overlap, null));
        assertThat(completion.after().parts().get(0)).isEqualTo(UploadPart.waiting(0));
        assertThat(completion.sessionCompleted()).isFalse();
    }

    @Test
    void should_markThePartNoOverlap_when_theTwoClipsShareNoStretch() {
        final UploadSession left = leftAndRight()
                .completeClip(Camera.LEFT, 0, confirmation(0, 100_000), UPLOADED_AT).after();

        final ClipCompletion completion = left.completeClip(Camera.RIGHT, 0, confirmation(400_000, 100_000),
                UPLOADED_AT);

        final Window empty = new Window(400_000, -300_000);
        assertThat(completion.part()).isEqualTo(new PartTransition.NoOverlap(empty));
        assertThat(completion.partState()).isEqualTo(PartState.NO_OVERLAP);
        assertThat(completion.after().parts().getFirst())
                .isEqualTo(new UploadPart(0, PartStatus.NO_OVERLAP, empty, null));
    }

    @Test
    void should_markThePartNoOverlap_when_oneClipEndsWhereTheOtherStarts() {
        final UploadSession left = leftAndRight()
                .completeClip(Camera.LEFT, 0, confirmation(0, 300_000), UPLOADED_AT).after();

        final ClipCompletion completion = left.completeClip(Camera.RIGHT, 0, confirmation(300_000, 300_000),
                UPLOADED_AT);

        assertThat(completion.part()).isEqualTo(new PartTransition.NoOverlap(new Window(300_000, 0)));
    }

    @Test
    void should_completeThePartWithTheClipsOwnWindow_when_theSingleCameraConfirms() {
        final ClipCompletion completion = openedBy(Camera.SINGLE)
                .completeClip(Camera.SINGLE, 2, confirmation(1_200_000, 280_000), UPLOADED_AT);

        assertThat(completion.part()).isEqualTo(new PartTransition.Complete(new Window(1_200_000, 280_000)));
        assertThat(completion.partState()).isEqualTo(PartState.COMPLETE);
        assertThat(completion.sessionCompleted()).isFalse();
        assertThat(completion.after().status()).isEqualTo(SessionStatus.OPEN);
    }

    @Test
    void should_closeThePartAndTheSessionInOneConfirmation_when_aSingleCameraHasOnePart() {
        final UploadSession single = UploadSession.open(SESSION_ID, EVENT_ID,
                new CameraRegistration(Camera.SINGLE, 1, REQUESTED_VENUE), BUCKET, OPENED_AT).session();

        final ClipCompletion completion = single.completeClip(Camera.SINGLE, 0, confirmation(), UPLOADED_AT);

        assertThat(completion.part()).isInstanceOf(PartTransition.Complete.class);
        assertThat(completion.sessionCompleted()).isTrue();
        assertThat(completion.after().status()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    void should_completeTheSession_when_theLastClipOfTheLastCameraIsConfirmed() {
        UploadSession session = allUploadedBy(leftAndRight(), Camera.LEFT);
        session = session.completeClip(Camera.RIGHT, 0, confirmation(), UPLOADED_AT).after();
        session = session.completeClip(Camera.RIGHT, 2, confirmation(), UPLOADED_AT).after();
        assertThat(session.status()).isEqualTo(SessionStatus.OPEN);

        final ClipCompletion last = session.completeClip(Camera.RIGHT, 1, confirmation(), UPLOADED_AT);

        assertThat(last.sessionCompleted()).isTrue();
        assertThat(last.after().status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(last.after().parts()).extracting(UploadPart::status).containsOnly(PartStatus.COMPLETE);
    }

    // PD-2: a LEFT-only session waits for RIGHT, so it completes neither a part nor itself
    @Test
    void should_closeNoPartAndNotComplete_when_onlyLeftIsRegisteredAndHasUploadedEverything() {
        final UploadSession leftOnly = openedBy(Camera.LEFT);
        final UploadSession allButTheLast = leftOnly
                .completeClip(Camera.LEFT, 0, confirmation(), UPLOADED_AT).after()
                .completeClip(Camera.LEFT, 1, confirmation(), UPLOADED_AT).after();

        final ClipCompletion last = allButTheLast.completeClip(Camera.LEFT, 2, confirmation(), UPLOADED_AT);

        final UploadSession all = last.after();
        assertThat(last.part()).isEqualTo(PartTransition.NONE);
        assertThat(last.sessionCompleted()).isFalse();
        assertThat(all).isEqualTo(allUploadedBy(leftOnly, Camera.LEFT));
        assertThat(all.status()).isEqualTo(SessionStatus.OPEN);
        assertThat(all.parts()).extracting(UploadPart::status).containsOnly(PartStatus.WAITING);
        assertThat(List.of(all.partState(0), all.partState(1), all.partState(2)))
                .containsOnly(PartState.WAITING_RIGHT);
    }

    @Test
    void should_closeEachPartOnItsOwn_when_theCamerasConfirmPartsOutOfOrder() {
        UploadSession session = leftAndRight();
        session = session.completeClip(Camera.LEFT, 2, confirmation(), UPLOADED_AT).after();
        session = session.completeClip(Camera.RIGHT, 1, confirmation(), UPLOADED_AT).after();

        final ClipCompletion completion = session.completeClip(Camera.RIGHT, 2, confirmation(), UPLOADED_AT);

        assertThat(completion.part()).isInstanceOf(PartTransition.Complete.class);
        assertThat(completion.after().partState(0)).isEqualTo(PartState.WAITING);
        assertThat(completion.after().partState(1)).isEqualTo(PartState.WAITING_LEFT);
        assertThat(completion.after().partState(2)).isEqualTo(PartState.COMPLETE);
    }

    @Test
    void should_changeNothing_when_anUploadedClipIsConfirmedAgainWithTheSameConfirmation() {
        final UploadSession uploaded = leftAndRight()
                .completeClip(Camera.LEFT, 1, confirmation(), UPLOADED_AT).after();

        final ClipCompletion repeat = uploaded.completeClip(Camera.LEFT, 1, confirmation(),
                UPLOADED_AT.plusSeconds(60));

        assertThat(repeat.noop()).isTrue();
        assertThat(repeat.after()).isEqualTo(uploaded);
        assertThat(repeat.part()).isEqualTo(PartTransition.NONE);
        assertThat(repeat.sessionCompleted()).isFalse();
        assertThat(repeat.clip().uploadedAt()).isEqualTo(UPLOADED_AT);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "another size,     1073741823, 9b2cf535f27731c974343645a3985328, 900000, 300000",
            "another etag,     1073741824, 0000f535f27731c974343645a3985328, 900000, 300000",
            "another start,    1073741824, 9b2cf535f27731c974343645a3985328, 900001, 300000",
            "another duration, 1073741824, 9b2cf535f27731c974343645a3985328, 900000, 299999"})
    void should_rejectTheConfirmation_when_anUploadedClipIsConfirmedAgainDifferently(final String change,
                                                                                    final long sizeBytes,
                                                                                    final String etag,
                                                                                    final long startMs,
                                                                                    final long durationMs) {
        final UploadSession uploaded = leftAndRight()
                .completeClip(Camera.LEFT, 1, confirmation(), UPLOADED_AT).after();
        final ClipConfirmation other = new ClipConfirmation(sizeBytes, etag, startMs, durationMs);

        assertThatThrownBy(() -> uploaded.completeClip(Camera.LEFT, 1, other, UPLOADED_AT))
                .isInstanceOf(ClipAlreadyCompletedException.class)
                .hasMessageContaining("LEFT 1");
    }

    @ParameterizedTest(name = "{1} on a session opened by {0}")
    @CsvSource({"LEFT, RIGHT", "LEFT, SINGLE", "SINGLE, LEFT"})
    void should_rejectTheConfirmation_when_theCameraIsNotRegistered(final Camera registered, final Camera other) {
        final UploadSession session = openedBy(registered);

        assertThatThrownBy(() -> session.completeClip(other, 0, confirmation(), UPLOADED_AT))
                .isInstanceOfSatisfying(UnknownCameraException.class, e -> {
                    assertThat(e.field()).isEqualTo("camera");
                    assertThat(e.reason()).isEqualTo("is not registered on the session");
                });
    }

    @ParameterizedTest(name = "part {0} of 3")
    @ValueSource(ints = {-1, 3, 40})
    void should_rejectTheConfirmation_when_thePartIndexIsOutOfRange(final int partIndex) {
        final UploadSession session = leftAndRight();

        assertThatThrownBy(() -> session.completeClip(Camera.LEFT, partIndex, confirmation(), UPLOADED_AT))
                .isInstanceOfSatisfying(PartIndexOutOfRangeException.class, e -> {
                    assertThat(e.field()).isEqualTo("partIndex");
                    assertThat(e.reason()).isEqualTo("must be between 0 and 2");
                });
        assertThatThrownBy(() -> session.partState(partIndex)).isInstanceOf(PartIndexOutOfRangeException.class);
    }

    @Test
    void should_waitForEveryCamera_when_noClipOfThePartIsUploaded() {
        assertThat(leftAndRight().partState(0)).isEqualTo(PartState.WAITING);
        assertThat(openedBy(Camera.SINGLE).partState(0)).isEqualTo(PartState.WAITING);
    }

    @ParameterizedTest(name = "registered {0}")
    @CsvSource(delimiter = '|', value = {"SINGLE | SINGLE", "LEFT | LEFT,RIGHT", "RIGHT | LEFT,RIGHT"})
    void should_expectTheCamerasOfTheSessionsCameraSet_when_askedForTheExpectedCameras(final Camera first,
                                                                                      final String expected) {
        assertThat(openedBy(first).expectedCameras())
                .isEqualTo(Set.copyOf(List.of(expected.split(",")).stream().map(Camera::valueOf).toList()));
    }

    @Test
    void should_countThePublishedParts_when_askedForPartsPublished() {
        final UploadSession session = openedBy(Camera.SINGLE);
        final UploadPart published = new UploadPart(0, PartStatus.COMPLETE, new Window(0, 1),
                Instant.parse("2026-11-05T12:00:00Z"));
        final UploadSession withOnePublished = new UploadSession(session.id(), session.eventId(), session.status(),
                session.venue(), session.createdAt(), session.updatedAt(), session.version(), session.cameras(),
                session.clips(), List.of(published, session.parts().get(1), session.parts().get(2)));

        assertThat(withOnePublished.partsPublished()).isEqualTo(1);
        assertThat(withOnePublished.partCount()).isEqualTo(3);
        assertThat(published.isPublished()).isTrue();
        assertThat(session.partsPublished()).isZero();
    }
}

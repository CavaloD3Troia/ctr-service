package org.cavalodetroia.ctr.domain.model.upload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.leftAndRight;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.openedBy;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.withParts;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.withUploaded;

/**
 * AC-2: the state of each part as the app shows it, from the part and the clips of the expected cameras; AC-3: the
 * published parts are counted by {@code publishedAt}.
 */
class UploadSessionPartStateTest {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-11-04T18:10:00Z");

    @Test
    void should_beWaiting_when_aSingleCameraHasNotUploadedThePart() {
        final UploadSession session = openedBy(Camera.SINGLE);

        assertThat(session.partState(0)).isEqualTo(PartState.WAITING);
        assertThat(session.partState(2)).isEqualTo(PartState.WAITING);
    }

    // Both missing names the first expected camera
    @Test
    void should_waitForLeft_when_neitherCameraHasUploadedThePart() {
        assertThat(leftAndRight().partState(0)).isEqualTo(PartState.WAITING_LEFT);
    }

    @ParameterizedTest(name = "{0} uploaded -> {1}")
    @CsvSource({"LEFT, WAITING_RIGHT", "RIGHT, WAITING_LEFT"})
    void should_waitForTheOtherCamera_when_oneCameraHasUploadedThePart(final Camera uploaded,
                                                                      final PartState expected) {
        final UploadSession session = withUploaded(leftAndRight(), uploaded, 1);

        assertThat(session.partState(1)).isEqualTo(expected);
        assertThat(session.partState(0)).isEqualTo(PartState.WAITING_LEFT);
    }

    // A LEFT-only session still expects RIGHT: the part is not WAITING, which is for a single camera
    @Test
    void should_waitForRight_when_onlyLeftIsRegisteredAndHasUploadedThePart() {
        final UploadSession session = withUploaded(openedBy(Camera.LEFT), Camera.LEFT, 0);

        assertThat(session.partState(0)).isEqualTo(PartState.WAITING_RIGHT);
        assertThat(session.partState(1)).isEqualTo(PartState.WAITING_LEFT);
    }

    @Test
    void should_waitForLeft_when_onlyRightIsRegistered() {
        final UploadSession session = withUploaded(openedBy(Camera.RIGHT), Camera.RIGHT, 0);

        assertThat(session.partState(0)).isEqualTo(PartState.WAITING_LEFT);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"COMPLETE, COMPLETE", "NO_OVERLAP, NO_OVERLAP"})
    void should_giveTheStoredOutcome_when_thePartIsDone(final PartStatus status, final PartState expected) {
        final UploadSession session = withParts(withUploaded(withUploaded(leftAndRight(), Camera.LEFT, 2),
                Camera.RIGHT, 2), new UploadPart(2, status, 600_000L, 298_000L, null));

        assertThat(session.partState(2)).isEqualTo(expected);
    }

    @Test
    void should_failLoudly_when_aPartWaitsWithEveryExpectedClipUploaded() {
        final UploadSession session = withUploaded(withUploaded(leftAndRight(), Camera.LEFT, 0), Camera.RIGHT, 0);

        assertThatThrownBy(() -> session.partState(0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Part 0");
    }

    @Test
    void should_rejectThePartIndex_when_theSessionHasNoSuchPart() {
        assertThatThrownBy(() -> leftAndRight().partState(3)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} registered")
    @CsvSource({"SINGLE, SINGLE", "LEFT, LEFT RIGHT", "RIGHT, LEFT RIGHT"})
    void should_expectLeftAndRightUnlessSingleIsRegistered(final Camera registered, final String expected) {
        assertThat(openedBy(registered).expectedCameras())
                .map(Camera::name)
                .containsExactly(expected.split(" "));
    }

    // A COMPLETE part is not published until published_at is set
    @Test
    void should_countPartsWithAPublicationTime_when_askedForPartsPublished() {
        final UploadSession session = withParts(leftAndRight(),
                new UploadPart(0, PartStatus.COMPLETE, 0L, 300_000L, PUBLISHED_AT),
                new UploadPart(1, PartStatus.COMPLETE, 300_000L, 300_000L, null));

        assertThat(session.partsPublished()).isEqualTo(1);
        assertThat(leftAndRight().partsPublished()).isZero();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"WAITING, false", "COMPLETE, true", "NO_OVERLAP, false"})
    void should_haveAWindowOnlyWhenComplete(final PartStatus status, final boolean hasWindow) {
        assertThat(new UploadPart(0, status, 900_000L, -2_000L, null).hasWindow()).isEqualTo(hasWindow);
    }

    @Test
    void should_bePublished_when_thePartHasAPublicationTime() {
        assertThat(new UploadPart(0, PartStatus.COMPLETE, 0L, 1L, PUBLISHED_AT).published()).isTrue();
        assertThat(UploadPart.waiting(0).published()).isFalse();
    }

    @Test
    void should_rejectAnUploadedClip_when_itLacksWhatTheCameraReported() {
        assertThatThrownBy(() -> new UploadClip(Camera.LEFT, 0, "k", ClipStatus.UPLOADED, 1L, 0L, 1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UPLOADED");
        assertThat(UploadClip.pending(Camera.LEFT, 0, "k").sizeBytes()).isNull();
    }
}

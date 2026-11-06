package org.cavalodetroia.ctr.domain.service;

import org.cavalodetroia.ctr.domain.exception.ClipAlreadyCompletedException;
import org.cavalodetroia.ctr.domain.exception.EtagMismatchException;
import org.cavalodetroia.ctr.domain.exception.ObjectNotFoundException;
import org.cavalodetroia.ctr.domain.exception.PartIndexOutOfRangeException;
import org.cavalodetroia.ctr.domain.exception.SessionNotFoundException;
import org.cavalodetroia.ctr.domain.exception.SizeMismatchException;
import org.cavalodetroia.ctr.domain.exception.StorageUnavailableException;
import org.cavalodetroia.ctr.domain.exception.UnknownCameraException;
import org.cavalodetroia.ctr.domain.model.storage.ObjectMetadata;
import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.ClipCompletion;
import org.cavalodetroia.ctr.domain.model.upload.ClipConfirmation;
import org.cavalodetroia.ctr.domain.model.upload.ClipStatus;
import org.cavalodetroia.ctr.domain.model.upload.PartState;
import org.cavalodetroia.ctr.domain.model.upload.PartTransition;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.model.upload.Window;
import org.cavalodetroia.ctr.domain.port.out.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.BUCKET;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.JOINED_AT;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.joining;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.openedBy;
import static org.cavalodetroia.ctr.domain.model.upload.ClipConfirmations.DURATION_MS;
import static org.cavalodetroia.ctr.domain.model.upload.ClipConfirmations.ETAG;
import static org.cavalodetroia.ctr.domain.model.upload.ClipConfirmations.SIZE_BYTES;
import static org.cavalodetroia.ctr.domain.model.upload.ClipConfirmations.START_MS;
import static org.cavalodetroia.ctr.domain.model.upload.ClipConfirmations.confirmation;
import static org.mockito.Mockito.mock;

/**
 * The clip confirmation use case against a bucket and sessions in memory. AC-4, AC-5: nothing is written unless the
 * bucket agrees with the confirmation; AC-6: unknown session, camera or part fail before the bucket is asked; AC-7:
 * a repeat with the same body neither asks the bucket nor writes; NFR-1: the bucket is only ever asked with a HEAD,
 * once per confirmation.
 */
class UploadSessionServiceCompleteTest {

    private static final Instant NOW = Instant.parse("2026-11-05T11:00:00Z");

    private final FakeClipStorage storage = new FakeClipStorage();
    private final InMemoryUploadSessionRepository sessions = new InMemoryUploadSessionRepository();
    private final UploadSessionService service = new UploadSessionService(mock(EventRepository.class), sessions,
            storage, new FakeClipEventPublisher(sessions), Clock.fixed(NOW, ZoneOffset.UTC), BUCKET);

    private UploadSession session;

    @BeforeEach
    void storeALeftAndRightSession() {
        session = openedBy(Camera.LEFT).join(joining(Camera.RIGHT), BUCKET, JOINED_AT).session();
        sessions.store(session);
    }

    @Test
    void should_askTheBucketOnceAndStoreTheClip_when_theObjectMatchesTheConfirmation() {
        putTheObject(Camera.LEFT, 1, SIZE_BYTES, "\"" + ETAG + "\"");

        final ClipCompletion completion = service.complete(session.id(), Camera.LEFT, 1, confirmation());

        assertThat(storage.heads()).isEqualTo(1);
        assertThat(sessions.writes()).isEqualTo(1);
        assertThat(completion.after()).isEqualTo(sessions.stored(session.id()));
        assertThat(completion.clip().status()).isEqualTo(ClipStatus.UPLOADED);
        assertThat(completion.clip().confirmation()).isEqualTo(confirmation());
        assertThat(completion.clip().uploadedAt()).isEqualTo(NOW);
        assertThat(completion.partState()).isEqualTo(PartState.WAITING_RIGHT);
    }

    @Test
    void should_returnTheClosedPart_when_theSecondCameraConfirms() {
        putTheObject(Camera.LEFT, 1, SIZE_BYTES, ETAG);
        putTheObject(Camera.RIGHT, 1, SIZE_BYTES, ETAG);
        service.complete(session.id(), Camera.LEFT, 1, confirmation(900_000, 300_000));

        final ClipCompletion completion = service.complete(session.id(), Camera.RIGHT, 1,
                confirmation(898_000, 300_000));

        assertThat(completion.part()).isEqualTo(new PartTransition.Complete(new Window(900_000, 298_000)));
        assertThat(sessions.stored(session.id()).partState(1)).isEqualTo(PartState.COMPLETE);
    }

    @Test
    void should_throwSessionNotFoundWithoutAskingTheBucket_when_theSessionIsUnknown() {
        final UUID unknown = UUID.fromString("019a5b2e-7f3c-7000-8000-0000000000ff");

        assertThatThrownBy(() -> service.complete(unknown, Camera.LEFT, 1, confirmation()))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining(unknown.toString());
        assertThat(storage.heads()).isZero();
    }

    @Test
    void should_throwUnknownCameraWithoutAskingTheBucket_when_theCameraIsNotRegistered() {
        assertThatThrownBy(() -> service.complete(session.id(), Camera.SINGLE, 1, confirmation()))
                .isInstanceOf(UnknownCameraException.class);
        assertThat(storage.heads()).isZero();
        assertThat(sessions.writes()).isZero();
    }

    @Test
    void should_throwPartIndexOutOfRangeWithoutAskingTheBucket_when_thePartIsNotInTheSession() {
        assertThatThrownBy(() -> service.complete(session.id(), Camera.LEFT, 3, confirmation()))
                .isInstanceOf(PartIndexOutOfRangeException.class);
        assertThat(storage.heads()).isZero();
        assertThat(sessions.writes()).isZero();
    }

    @Test
    void should_answerFromTheStoredSessionWithoutAskingTheBucketOrWriting_when_theSameConfirmationComesAgain() {
        putTheObject(Camera.LEFT, 1, SIZE_BYTES, ETAG);
        final ClipCompletion first = service.complete(session.id(), Camera.LEFT, 1, confirmation());

        final ClipCompletion repeat = service.complete(session.id(), Camera.LEFT, 1, confirmation());

        assertThat(repeat.noop()).isTrue();
        assertThat(repeat.after()).isEqualTo(first.after());
        assertThat(storage.heads()).isEqualTo(1);
        assertThat(sessions.writes()).isEqualTo(1);
    }

    @Test
    void should_throwClipAlreadyCompletedWithoutAskingTheBucket_when_anotherConfirmationComesForAnUploadedClip() {
        putTheObject(Camera.LEFT, 1, SIZE_BYTES, ETAG);
        service.complete(session.id(), Camera.LEFT, 1, confirmation());

        assertThatThrownBy(() -> service.complete(session.id(), Camera.LEFT, 1, confirmation(START_MS, 1)))
                .isInstanceOf(ClipAlreadyCompletedException.class);
        assertThat(storage.heads()).isEqualTo(1);
        assertThat(sessions.writes()).isEqualTo(1);
    }

    @Test
    void should_throwObjectNotFoundAndWriteNothing_when_theObjectIsNotInTheBucket() {
        assertThatThrownBy(() -> service.complete(session.id(), Camera.LEFT, 1, confirmation()))
                .isInstanceOf(ObjectNotFoundException.class)
                .hasMessageContaining(session.clip(Camera.LEFT, 1).objectKey());
        assertThat(sessions.writes()).isZero();
        assertThat(sessions.stored(session.id())).isEqualTo(session);
    }

    @Test
    void should_throwSizeMismatchWithBothSizesAndWriteNothing_when_theObjectHasAnotherSize() {
        putTheObject(Camera.LEFT, 1, SIZE_BYTES - 1, ETAG);

        assertThatThrownBy(() -> service.complete(session.id(), Camera.LEFT, 1, confirmation()))
                .isInstanceOf(SizeMismatchException.class)
                .hasMessageContaining(String.valueOf(SIZE_BYTES))
                .hasMessageContaining(String.valueOf(SIZE_BYTES - 1));
        assertThat(sessions.writes()).isZero();
    }

    // PD-3: the size is checked first, so an object with both wrong reports the size
    @Test
    void should_reportTheSize_when_bothTheSizeAndTheEtagDiffer() {
        putTheObject(Camera.LEFT, 1, SIZE_BYTES - 1, "other");

        assertThatThrownBy(() -> service.complete(session.id(), Camera.LEFT, 1, confirmation()))
                .isInstanceOf(SizeMismatchException.class);
    }

    @ParameterizedTest(name = "stored {0}, confirmed {1}")
    @CsvSource(delimiter = '|', value = {
            "9b2cf5     | 9b2cf6",
            "\"abc-3\"  | abc-2",
            "abc-3      | abc"})
    void should_throwEtagMismatchAndWriteNothing_when_theObjectHasAnotherEtag(final String stored,
                                                                             final String confirmed) {
        putTheObject(Camera.LEFT, 1, SIZE_BYTES, stored);

        assertThatThrownBy(() -> service.complete(session.id(), Camera.LEFT, 1,
                new ClipConfirmation(SIZE_BYTES, confirmed, START_MS, DURATION_MS)))
                .isInstanceOf(EtagMismatchException.class);
        assertThat(sessions.writes()).isZero();
    }

    @ParameterizedTest(name = "stored {0}, confirmed {1}")
    @CsvSource(delimiter = '|', value = {
            "\"9b2cf5\"   | 9b2cf5",
            "9b2cf5       | \"9b2cf5\"",
            "\"abc-3\"    | \"abc-3\""})
    void should_storeTheClip_when_theEtagsOnlyDifferInTheirQuotes(final String stored, final String confirmed) {
        putTheObject(Camera.LEFT, 1, SIZE_BYTES, stored);

        service.complete(session.id(), Camera.LEFT, 1, new ClipConfirmation(SIZE_BYTES, confirmed, START_MS,
                DURATION_MS));

        assertThat(sessions.writes()).isEqualTo(1);
    }

    @Test
    void should_propagateStorageUnavailableAndWriteNothing_when_theBucketDoesNotAnswer() {
        storage.becomeUnavailable();

        assertThatThrownBy(() -> service.complete(session.id(), Camera.LEFT, 1, confirmation()))
                .isInstanceOf(StorageUnavailableException.class);
        assertThat(sessions.writes()).isZero();
    }

    // The same confirmation may race itself: the second one finds the clip uploaded once it holds the lock
    @Test
    void should_writeNothing_when_theClipWasUploadedBetweenTheReadAndTheLockedUpdate() {
        putTheObject(Camera.LEFT, 1, SIZE_BYTES, ETAG);
        sessions.store(session.completeClip(Camera.LEFT, 1, confirmation(), NOW).after());
        sessions.readStale(session);

        final ClipCompletion completion = service.complete(session.id(), Camera.LEFT, 1, confirmation());

        assertThat(completion.noop()).isTrue();
        assertThat(sessions.writes()).isZero();
    }

    private void putTheObject(final Camera camera, final int partIndex, final long sizeBytes, final String etag) {
        storage.put(session.clip(camera, partIndex).objectKey(), new ObjectMetadata(sizeBytes, etag));
    }
}

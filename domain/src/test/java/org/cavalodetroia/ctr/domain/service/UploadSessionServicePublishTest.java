package org.cavalodetroia.ctr.domain.service;

import org.cavalodetroia.ctr.domain.model.storage.ObjectMetadata;
import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.ClipCompletion;
import org.cavalodetroia.ctr.domain.model.upload.PartState;
import org.cavalodetroia.ctr.domain.model.upload.PartStatus;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.port.out.EventRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.BUCKET;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.leftAndRight;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.openedBy;
import static org.cavalodetroia.ctr.domain.model.upload.ClipConfirmations.ETAG;
import static org.cavalodetroia.ctr.domain.model.upload.ClipConfirmations.SIZE_BYTES;
import static org.cavalodetroia.ctr.domain.model.upload.ClipConfirmations.confirmation;
import static org.mockito.Mockito.mock;

/**
 * TH-152 AC-10: when the confirmation publishes its part, without Spring. A broker and the sessions in memory share
 * one call log, so the order of commit, publish and mark is read off it. AC-1, AC-2: a part that closes COMPLETE is
 * published once, after its transaction, then marked; AC-4: a repeat publishes only a part whose publish failed;
 * AC-5: NO_OVERLAP never; AC-6, NFR-2: a failed publish answers with the part unpublished instead of failing; NFR-1:
 * the part is COMPLETE in the store whenever it is published.
 */
class UploadSessionServicePublishTest {

    private static final Instant NOW = Instant.parse("2026-11-05T11:00:00Z");

    private final FakeClipStorage storage = new FakeClipStorage();
    private final InMemoryUploadSessionRepository sessions = new InMemoryUploadSessionRepository();
    private final FakeClipEventPublisher publisher = new FakeClipEventPublisher(sessions);
    private final UploadSessionService service = new UploadSessionService(mock(EventRepository.class), sessions,
            storage, publisher, Clock.fixed(NOW, ZoneOffset.UTC), BUCKET);

    @Test
    void should_publishAfterTheCommitThenMarkThePartPublished_when_theSecondCameraCompletesThePart() {
        final UploadSession session = stored(leftAndRight());
        service.complete(session.id(), Camera.LEFT, 1, confirmation(900_000, 300_000));

        final ClipCompletion completion = service.complete(session.id(), Camera.RIGHT, 1,
                confirmation(898_000, 300_000));

        assertThat(sessions.calls()).containsExactly("update", "update", "publish 1", "markPublished 1");
        assertThat(publisher.storedStatusAtPublish()).containsExactly(PartStatus.COMPLETE);
        assertThat(completion.published()).isTrue();
        assertThat(completion.after().part(1).publishedAt()).isEqualTo(NOW);
        assertThat(completion.after().partsPublished()).isEqualTo(1);
        assertThat(sessions.stored(session.id()).part(1).publishedAt()).isEqualTo(NOW);
    }

    @Test
    void should_publishTheSingleCamerasPart_when_itsClipIsConfirmed() {
        final UploadSession session = stored(openedBy(Camera.SINGLE));

        final ClipCompletion completion = service.complete(session.id(), Camera.SINGLE, 0, confirmation());

        assertThat(sessions.calls()).containsExactly("update", "publish 0", "markPublished 0");
        assertThat(completion.published()).isTrue();
    }

    @Test
    void should_publishNothing_when_thePartStillWaitsForTheOtherCamera() {
        final UploadSession session = stored(leftAndRight());

        final ClipCompletion completion = service.complete(session.id(), Camera.LEFT, 1, confirmation());

        assertThat(sessions.calls()).containsExactly("update");
        assertThat(completion.published()).isFalse();
        assertThat(completion.partState()).isEqualTo(PartState.WAITING_RIGHT);
    }

    // AC-5
    @Test
    void should_publishNothing_when_thePartHasNoOverlap() {
        final UploadSession session = stored(leftAndRight());
        service.complete(session.id(), Camera.LEFT, 1, confirmation(0, 100_000));

        final ClipCompletion completion = service.complete(session.id(), Camera.RIGHT, 1,
                confirmation(400_000, 100_000));

        assertThat(completion.partState()).isEqualTo(PartState.NO_OVERLAP);
        assertThat(sessions.calls()).containsExactly("update", "update");
        assertThat(completion.published()).isFalse();
    }

    // AC-6, NFR-2: the part is committed, so the confirmation is answered; only publishedAt stays empty
    @Test
    void should_answerWithThePartCompleteAndUnpublished_when_thePublishFails() {
        final UploadSession session = stored(openedBy(Camera.SINGLE));
        publisher.becomeUnreachable();

        final ClipCompletion completion = service.complete(session.id(), Camera.SINGLE, 0, confirmation());

        assertThat(sessions.calls()).containsExactly("update", "publish 0");
        assertThat(completion.partState()).isEqualTo(PartState.COMPLETE);
        assertThat(completion.published()).isFalse();
        assertThat(sessions.stored(session.id()).part(0).status()).isEqualTo(PartStatus.COMPLETE);
        assertThat(sessions.stored(session.id()).part(0).publishedAt()).isNull();
    }

    // AC-4: also what happens when the process dies between the commit and the publish
    @Test
    void should_publishOnTheRepeatWithoutAskingTheBucket_when_theFirstPublishFailed() {
        final UploadSession session = stored(openedBy(Camera.SINGLE));
        publisher.becomeUnreachable();
        service.complete(session.id(), Camera.SINGLE, 0, confirmation());
        publisher.becomeReachable();

        final ClipCompletion repeat = service.complete(session.id(), Camera.SINGLE, 0, confirmation());

        assertThat(repeat.noop()).isTrue();
        assertThat(repeat.published()).isTrue();
        assertThat(sessions.calls()).containsExactly("update", "publish 0", "publish 0", "markPublished 0");
        assertThat(publisher.storedStatusAtPublish()).containsOnly(PartStatus.COMPLETE);
        assertThat(sessions.stored(session.id()).part(0).publishedAt()).isEqualTo(NOW);
        assertThat(storage.heads()).isEqualTo(1);
    }

    // AC-4
    @Test
    void should_publishNothingMore_when_aPublishedPartIsConfirmedAgain() {
        final UploadSession session = stored(openedBy(Camera.SINGLE));
        service.complete(session.id(), Camera.SINGLE, 0, confirmation());

        final ClipCompletion repeat = service.complete(session.id(), Camera.SINGLE, 0, confirmation());

        assertThat(repeat.published()).isTrue();
        assertThat(sessions.calls()).containsExactly("update", "publish 0", "markPublished 0");
    }

    /**
     * Stores the session and puts every one of its clips in the bucket with the size and etag of
     * {@link org.cavalodetroia.ctr.domain.model.upload.ClipConfirmations#confirmation()}.
     */
    private UploadSession stored(final UploadSession session) {
        sessions.store(session);
        session.clips().forEach(clip -> storage.put(clip.objectKey(), new ObjectMetadata(SIZE_BYTES, ETAG)));
        return session;
    }
}

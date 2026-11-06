package org.cavalodetroia.ctr.domain.service;

import org.cavalodetroia.ctr.domain.exception.PublishFailedException;
import org.cavalodetroia.ctr.domain.model.upload.PartStatus;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.port.out.ClipEventPublisher;

import java.util.ArrayList;
import java.util.List;

/**
 * A broker in memory. Each publish is recorded in the repository's call log, with the status the repository holds
 * for the part at that moment, so a test sees what was committed when the message went out. It can be made to fail
 * like an unreachable broker.
 */
final class FakeClipEventPublisher implements ClipEventPublisher {

    private final InMemoryUploadSessionRepository sessions;
    private final List<PartStatus> storedStatusAtPublish = new ArrayList<>();
    private boolean unreachable;

    FakeClipEventPublisher(final InMemoryUploadSessionRepository sessions) {
        this.sessions = sessions;
    }

    void becomeUnreachable() {
        unreachable = true;
    }

    void becomeReachable() {
        unreachable = false;
    }

    /**
     * @return for each publish attempt, the status of the part as stored when it was attempted
     */
    List<PartStatus> storedStatusAtPublish() {
        return List.copyOf(storedStatusAtPublish);
    }

    @Override
    public void publish(final UploadSession session, final int partIndex) throws PublishFailedException {
        sessions.record("publish " + partIndex);
        storedStatusAtPublish.add(sessions.stored(session.id()).part(partIndex).status());
        if (unreachable) {
            throw new PublishFailedException("clip-pair-uploaded.v1", session.id() + ":" + partIndex,
                    new IllegalStateException("broker unreachable"));
        }
    }
}

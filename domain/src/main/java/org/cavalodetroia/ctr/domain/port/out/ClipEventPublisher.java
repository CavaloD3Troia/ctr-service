package org.cavalodetroia.ctr.domain.port.out;

import org.cavalodetroia.ctr.domain.exception.PublishFailedException;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;

/**
 * Hands a completed part over to extraction: one message per part, for the cameras that filmed it. Two cameras give
 * their pair of clips to stitch, a single camera its clip, ready as is.
 */
public interface ClipEventPublisher {

    /**
     * Publishes the part's message and returns once the broker has acknowledged it, within a bounded time. A call
     * that fails may still have reached the broker, so a retry can publish the part twice; consumers dedupe by key.
     *
     * @param session   the session as stored, with the part {@code COMPLETE}
     * @param partIndex the part, 0-based
     * @throws PublishFailedException when the broker did not acknowledge the message in time, or refused it
     */
    void publish(UploadSession session, int partIndex) throws PublishFailedException;
}

package org.cavalodetroia.ctr.domain.port.out;

import org.cavalodetroia.ctr.domain.exception.OpenSessionAlreadyExistsException;
import org.cavalodetroia.ctr.domain.exception.SessionNotFoundException;
import org.cavalodetroia.ctr.domain.exception.UploadSessionChangedException;
import org.cavalodetroia.ctr.domain.model.upload.ClipCompletion;
import org.cavalodetroia.ctr.domain.model.upload.UploadCamera;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Where upload sessions are kept. Each write is all or nothing.
 */
public interface UploadSessionRepository {

    /**
     * @param eventId the event
     * @return the event's open session, with its cameras, clips and parts, if it has one
     */
    Optional<UploadSession> findOpenByEvent(UUID eventId);

    /**
     * Reads the whole session in a fixed number of queries, however many parts it has.
     *
     * @param sessionId the session
     * @return the session, open or completed, with its cameras, clips (by camera, then part index) and parts (by
     *         part index), if there is one with this id
     */
    Optional<UploadSession> findById(UUID sessionId);

    /**
     * Confirms a clip against the stored session: reads the session while no other confirmation of it can run,
     * applies {@code completion} to it, and stores what the completion changed (the clip, the part when it closed,
     * the session when it completed), all in one transaction. A {@link ClipCompletion#noop() noop} stores nothing,
     * and nothing is stored when {@code completion} throws. Two confirmations of the same session therefore never
     * decide over the same state.
     *
     * @param sessionId  the session
     * @param completion the domain's decision over the session as stored, for example {@link
     *                   UploadSession#completeClip}
     * @return what {@code completion} returned
     * @throws SessionNotFoundException when the session is unknown
     */
    ClipCompletion update(UUID sessionId, Function<UploadSession, ClipCompletion> completion);

    /**
     * Records that the part's message is out, in one short transaction of its own. Only a part not published yet is
     * changed, so a part published twice keeps its first publication time.
     *
     * @param sessionId   the session
     * @param partIndex   the part, 0-based
     * @param publishedAt when the message was published
     */
    void markPublished(UUID sessionId, int partIndex, Instant publishedAt);

    /**
     * Stores a session the first camera just opened: the session, its camera, its clips and its parts.
     *
     * @param session the session returned by {@link UploadSession#open}
     * @throws OpenSessionAlreadyExistsException when the event got an open session in the meantime
     */
    void create(UploadSession session);

    /**
     * Stores a camera that joined: the camera and its clips, and the session's new {@code updatedAt} and
     * {@code version}. Stored only while the session is still open and still at the version it was read at, one
     * below {@code session.version()}; otherwise nothing is written.
     *
     * @param session the session returned by {@link UploadSession#join}
     * @param camera  the camera that joined
     * @throws UploadSessionChangedException when the session changed or closed since it was read
     */
    void addCamera(UploadSession session, UploadCamera camera);
}

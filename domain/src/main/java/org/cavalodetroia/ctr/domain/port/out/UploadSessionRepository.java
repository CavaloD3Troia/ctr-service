package org.cavalodetroia.ctr.domain.port.out;

import org.cavalodetroia.ctr.domain.exception.CameraSetConflictException;
import org.cavalodetroia.ctr.domain.exception.OpenSessionAlreadyExistsException;
import org.cavalodetroia.ctr.domain.model.upload.UploadCamera;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;

import java.util.Optional;
import java.util.UUID;

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
     * Stores a session the first camera just opened: the session, its camera, its clips and its parts.
     *
     * @param session the session returned by {@link UploadSession#open}
     * @throws OpenSessionAlreadyExistsException when the event got an open session in the meantime
     */
    void create(UploadSession session);

    /**
     * Stores a camera that joined: the camera and its clips, and the session's new {@code updatedAt}.
     *
     * @param session the session returned by {@link UploadSession#join}
     * @param camera  the camera that joined
     * @throws CameraSetConflictException when the same camera was registered in the meantime
     */
    void addCamera(UploadSession session, UploadCamera camera);
}

package org.cavalodetroia.ctr.domain.port.in;

import org.cavalodetroia.ctr.domain.exception.SessionNotFoundException;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;

import java.util.UUID;

/**
 * Where an upload stands: the session with its cameras, clips and parts. Reads only; writes nothing and calls no
 * storage.
 */
public interface GetUploadSession {

    /**
     * @param sessionId the session
     * @return the session as stored, with every camera, clip and part
     * @throws SessionNotFoundException when no session has this id
     */
    UploadSession get(UUID sessionId);
}

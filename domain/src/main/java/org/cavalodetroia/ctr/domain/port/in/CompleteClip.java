package org.cavalodetroia.ctr.domain.port.in;

import org.cavalodetroia.ctr.domain.exception.ClipAlreadyCompletedException;
import org.cavalodetroia.ctr.domain.exception.EtagMismatchException;
import org.cavalodetroia.ctr.domain.exception.ObjectNotFoundException;
import org.cavalodetroia.ctr.domain.exception.PartIndexOutOfRangeException;
import org.cavalodetroia.ctr.domain.exception.SessionNotFoundException;
import org.cavalodetroia.ctr.domain.exception.SizeMismatchException;
import org.cavalodetroia.ctr.domain.exception.StorageUnavailableException;
import org.cavalodetroia.ctr.domain.exception.UnknownCameraException;
import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.ClipCompletion;
import org.cavalodetroia.ctr.domain.model.upload.ClipConfirmation;

import java.util.UUID;

/**
 * A camera confirms a clip it uploaded straight to the bucket. ctr-service checks that the object is there, with the
 * confirmed size and etag, without downloading it, and only then records the clip, closes its part when every
 * camera has uploaded it, and completes the session when every clip is uploaded. When a check fails nothing is
 * written. Confirming an uploaded clip again with the same confirmation changes nothing and checks nothing.
 */
public interface CompleteClip {

    /**
     * @param sessionId    the upload session
     * @param camera       the camera whose clip it is
     * @param partIndex    the part of the clip, 0-based
     * @param confirmation what the camera says about the clip
     * @return the session after the confirmation and what it did to the part, for the response and for publishing
     * @throws SessionNotFoundException       when the session is unknown
     * @throws UnknownCameraException         when the camera is not registered on the session
     * @throws PartIndexOutOfRangeException   when the part index is outside {@code 0 .. partCount - 1}
     * @throws ClipAlreadyCompletedException  when the clip is already uploaded with another confirmation
     * @throws ObjectNotFoundException        when the clip is not in the bucket
     * @throws SizeMismatchException          when the object in the bucket has another size
     * @throws EtagMismatchException          when the object in the bucket has another etag
     * @throws StorageUnavailableException    when the bucket can't be asked
     */
    ClipCompletion complete(UUID sessionId, Camera camera, int partIndex, ClipConfirmation confirmation);
}

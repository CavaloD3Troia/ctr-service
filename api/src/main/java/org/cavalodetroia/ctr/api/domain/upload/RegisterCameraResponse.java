package org.cavalodetroia.ctr.api.domain.upload;

import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.SessionStatus;

import java.util.List;
import java.util.UUID;

/**
 * Where the camera just registered writes its clips: {@code prefix} followed by {@code clipKey} with
 * {@code {partIndex}} filled in, 0-based.
 *
 * @param sessionId the event's upload session, the same for every camera of the event
 * @param eventId   the event
 * @param status    the session's status
 * @param camera    the camera just registered
 * @param partCount how many clips it uploads
 * @param prefix    {@code s3://<bucket>/events/<eventId>/sessions/<sessionId>/<CAMERA>/}
 * @param clipKey   {@code {partIndex}.mp4}
 * @param cameras   every camera of the session, LEFT before RIGHT
 */
public record RegisterCameraResponse(UUID sessionId,
                                     UUID eventId,
                                     SessionStatus status,
                                     Camera camera,
                                     int partCount,
                                     String prefix,
                                     String clipKey,
                                     List<CameraSummaryResponse> cameras) {
}

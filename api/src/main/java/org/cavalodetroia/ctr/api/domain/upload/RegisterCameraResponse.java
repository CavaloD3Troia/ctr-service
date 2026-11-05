package org.cavalodetroia.ctr.api.domain.upload;

import io.swagger.v3.oas.annotations.media.Schema;
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
public record RegisterCameraResponse(
        @Schema(description = "The event's upload session, the same for every camera of the event (UUIDv7)")
        UUID sessionId,

        UUID eventId,

        SessionStatus status,

        @Schema(description = "The camera just registered")
        Camera camera,

        @Schema(description = "How many clips the camera uploads", example = "40")
        int partCount,

        @Schema(description = "The folder of the camera's clips in the bucket",
                example = "s3://cdt-videos/events/0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11/sessions/"
                        + "019a5b2e-7f3c-7000-8000-000000000001/LEFT/")
        String prefix,

        @Schema(description = "The key of each clip inside prefix; {partIndex} is 0-based, so 40 parts are 0.mp4 to "
                + "39.mp4", example = "{partIndex}.mp4")
        String clipKey,

        @Schema(description = "Every camera of the session, LEFT before RIGHT")
        List<CameraSummaryResponse> cameras) {
}

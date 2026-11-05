package org.cavalodetroia.ctr.api.domain.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.ClipStatus;

/**
 * The clip just confirmed, its part and its session, as stored: a repeated confirmation gets the same body.
 *
 * @param camera    the camera of the clip
 * @param partIndex the part of the clip, 0-based
 * @param status    the clip's status, {@code UPLOADED}
 * @param part      where the clip's part stands
 * @param session   how far the session is
 */
public record CompleteClipResponse(
        Camera camera,

        @Schema(description = "The part of the clip, 0-based", example = "3")
        int partIndex,

        @Schema(description = "The clip's status, UPLOADED once confirmed")
        ClipStatus status,

        PartSummary part,

        SessionSummary session) {
}

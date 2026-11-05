package org.cavalodetroia.ctr.api.domain.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import org.cavalodetroia.ctr.domain.model.upload.Camera;

/**
 * One camera of the session and how far its upload is.
 *
 * @param camera        which camera
 * @param partsUploaded how many of its clips are in the bucket
 * @param partCount     how many clips it uploads
 */
public record CameraSummaryResponse(
        Camera camera,

        @Schema(description = "How many of the camera's clips are in the bucket", example = "0")
        int partsUploaded,

        @Schema(description = "How many clips the camera uploads", example = "40")
        int partCount) {
}

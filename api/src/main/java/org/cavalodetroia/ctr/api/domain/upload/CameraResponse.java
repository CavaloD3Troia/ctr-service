package org.cavalodetroia.ctr.api.domain.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import org.cavalodetroia.ctr.domain.model.upload.Camera;

/**
 * One registered camera of the session and how far its upload is.
 *
 * @param camera        which camera
 * @param partCount     how many clips it uploads
 * @param partsUploaded how many of its clips are in the bucket
 * @param prefix        the folder of its clips in the bucket
 */
public record CameraResponse(
        Camera camera,

        @Schema(description = "How many clips the camera uploads", example = "40")
        int partCount,

        @Schema(description = "How many of the camera's clips are UPLOADED", example = "3")
        int partsUploaded,

        @Schema(description = "The folder of the camera's clips in the bucket",
                example = "s3://cdt-videos/events/0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11/sessions/"
                        + "019a5b2e-7f3c-7000-8000-000000000001/LEFT/")
        String prefix) {
}

package org.cavalodetroia.ctr.api.domain.upload;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.ClipStatus;

import java.time.Instant;

/**
 * One camera's clip of a part. What the camera reported is only known once the clip is {@code UPLOADED}, so a
 * {@code PENDING} clip is just {@code camera} and {@code status}: absent fields are left out, not {@code null}.
 *
 * @param camera     the camera that films it
 * @param status     whether it is in the bucket yet
 * @param sizeBytes  the size of the object
 * @param startMs    where the clip starts on the match clock, in milliseconds
 * @param durationMs how long it lasts, in milliseconds
 * @param uploadedAt when the upload was confirmed
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClipResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Camera camera,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ClipStatus status,

        @Schema(description = "Size of the object in bytes; left out while PENDING", example = "1073741824",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Long sizeBytes,

        @Schema(description = "Where the clip starts on the match clock, in milliseconds; left out while PENDING",
                example = "900000", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Long startMs,

        @Schema(description = "How long the clip lasts, in milliseconds; left out while PENDING", example = "300000",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Long durationMs,

        @Schema(description = "When the upload was confirmed; left out while PENDING",
                example = "2026-11-04T18:07:02Z", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant uploadedAt) {
}

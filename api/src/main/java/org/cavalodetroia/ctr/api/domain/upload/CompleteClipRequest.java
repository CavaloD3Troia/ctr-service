package org.cavalodetroia.ctr.api.domain.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/upload-sessions/{sessionId}/cameras/{camera}/parts/{partIndex}/complete}: what S3
 * answered to the camera's {@code PUT}, and where the clip sits in the recording.
 *
 * @param sizeBytes  the size of the uploaded object
 * @param etag       the {@code ETag} S3 returned on the {@code PUT}
 * @param startMs    where the clip starts in the recording
 * @param durationMs how long the clip is
 */
public record CompleteClipRequest(
        @Schema(description = "Size of the uploaded object in bytes, as S3 stored it", example = "1073741824",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Positive Long sizeBytes,

        @Schema(description = "The ETag S3 returned on the PUT, with or without its double quotes; a multipart "
                + "etag such as abc-3 is compared as a string", example = "9b2cf535f27731c974343645a3985328",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = MAX_ETAG_LENGTH) String etag,

        @Schema(description = "Where the clip starts in the recording, in milliseconds", example = "900000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @PositiveOrZero Long startMs,

        @Schema(description = "How long the clip is, in milliseconds", example = "300000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Positive Long durationMs) {

    // An S3 etag is an MD5 in hex, plus a part count for multipart uploads; this leaves room for any S3 clone
    static final int MAX_ETAG_LENGTH = 255;
}

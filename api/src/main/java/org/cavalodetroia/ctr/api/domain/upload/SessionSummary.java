package org.cavalodetroia.ctr.api.domain.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import org.cavalodetroia.ctr.domain.model.upload.SessionStatus;

/**
 * How far an upload session is.
 *
 * @param status         {@code COMPLETED} once every clip of every camera is uploaded
 * @param partsPublished how many parts have been published
 * @param partCount      how many parts the match is cut into
 */
public record SessionSummary(
        @Schema(description = "OPEN while clips are missing; COMPLETED once every clip of every camera is uploaded")
        SessionStatus status,

        @Schema(description = "How many parts have been published", example = "2")
        int partsPublished,

        @Schema(description = "How many parts the match is cut into", example = "40")
        int partCount) {
}

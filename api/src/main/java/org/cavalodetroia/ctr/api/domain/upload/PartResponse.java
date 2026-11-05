package org.cavalodetroia.ctr.api.domain.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import org.cavalodetroia.ctr.domain.model.upload.PartState;

import java.util.List;

/**
 * One part of the match across the session's cameras. Every field is always there; the window is {@code null}
 * until the part is {@code COMPLETE}, and stays {@code null} for {@code NO_OVERLAP}.
 *
 * @param partIndex  the part, 0-based
 * @param status     which camera it waits for, or how it ended
 * @param published  whether it was published for extraction
 * @param startMs    where the part's window starts on the match clock, in milliseconds
 * @param durationMs how long the window lasts, in milliseconds
 * @param clips      one per registered camera, LEFT before RIGHT
 */
public record PartResponse(
        @Schema(description = "The part, 0-based", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        int partIndex,

        @Schema(description = "WAITING_LEFT or WAITING_RIGHT: two cameras, that camera's clip is missing (LEFT when "
                + "both are). WAITING: a single camera, its clip is missing. COMPLETE: every clip is in and they "
                + "overlap. NO_OVERLAP: every clip is in but they don't overlap.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        PartState status,

        @Schema(description = "Whether the part was published for extraction", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean published,

        @Schema(description = "Where the window shared by the clips starts on the match clock, in milliseconds; null "
                + "until COMPLETE and for NO_OVERLAP", example = "600000", nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long startMs,

        @Schema(description = "How long the window shared by the clips lasts, in milliseconds; null until COMPLETE "
                + "and for NO_OVERLAP", example = "298000", nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long durationMs,

        @Schema(description = "One per registered camera, LEFT before RIGHT", requiredMode = Schema.RequiredMode.REQUIRED)
        List<ClipResponse> clips) {
}

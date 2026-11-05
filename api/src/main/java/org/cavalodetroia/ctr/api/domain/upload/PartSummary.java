package org.cavalodetroia.ctr.api.domain.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import org.cavalodetroia.ctr.domain.model.upload.PartState;

/**
 * Where one part of the match stands.
 *
 * @param status    which camera it waits for, or how it closed
 * @param published whether the part has been published
 */
public record PartSummary(
        @Schema(description = "WAITING_LEFT or WAITING_RIGHT while that camera's clip is missing (or it has not "
                + "registered yet); COMPLETE once every expected clip is uploaded and they overlap; NO_OVERLAP when "
                + "they share no stretch of the recording, so nothing is published for it. WAITING (a single "
                + "camera's clip missing) never comes back here: a SINGLE confirmation closes its part")
        PartState status,

        @Schema(description = "Whether the part has been published", example = "false")
        boolean published) {
}

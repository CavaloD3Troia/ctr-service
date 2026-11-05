package org.cavalodetroia.ctr.api.domain.venue;

import io.swagger.v3.oas.annotations.media.Schema;
import org.cavalodetroia.ctr.domain.model.venue.VenueFormat;

/**
 * The pitch a session is filmed on, as the first camera set it.
 *
 * @param format  the kind of pitch
 * @param lengthM the length in metres: measured, or the standard of the format
 * @param widthM  the width in metres: measured, or the standard of the format
 */
public record VenueResponse(
        VenueFormat format,

        @Schema(description = "Length in metres: as measured, or the standard of the format", example = "105")
        double lengthM,

        @Schema(description = "Width in metres: as measured, or the standard of the format", example = "68")
        double widthM) {
}

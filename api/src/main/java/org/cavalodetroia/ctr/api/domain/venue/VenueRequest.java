package org.cavalodetroia.ctr.api.domain.venue;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import org.cavalodetroia.ctr.domain.model.venue.VenueFormat;

/**
 * The pitch as the user describes it. {@code format} is required on the first camera of a session; the measures are
 * both given or both left out (then the standard of the format is used). Those two rules need the domain and come
 * back as {@code VALIDATION_FAILED} from there.
 *
 * @param format  the kind of pitch
 * @param lengthM the measured length in metres
 * @param widthM  the measured width in metres
 */
public record VenueRequest(
        @Schema(description = "The kind of pitch. Standard dimensions: FUTSAL_5V5 40 x 20 m, FOOTBALL_7V7 65 x 45 m, "
                + "FOOTBALL_11V11 105 x 68 m")
        VenueFormat format,

        @Schema(description = "Measured length in metres; give it with widthM, or leave both out for the standard",
                example = "105")
        @Positive Double lengthM,

        @Schema(description = "Measured width in metres; give it with lengthM, or leave both out for the standard",
                example = "68")
        @Positive Double widthM) {
}

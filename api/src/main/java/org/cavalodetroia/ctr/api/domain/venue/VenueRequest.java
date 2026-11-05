package org.cavalodetroia.ctr.api.domain.venue;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import org.cavalodetroia.ctr.domain.model.venue.VenueFormat;

/**
 * The pitch as the user describes it. {@code format} is required, and must be a {@link VenueFormat}, on the first
 * camera of a session only: a later camera's venue is ignored, so its format is not checked. It stays a string here
 * and the domain checks it when the session opens. The measures are both given or both left out (then the standard
 * of the format is used) on every camera. Those rules come back as {@code VALIDATION_FAILED} from the domain.
 *
 * @param format  the kind of pitch, the name of a {@link VenueFormat}
 * @param lengthM the measured length in metres
 * @param widthM  the measured width in metres
 */
public record VenueRequest(
        @Schema(implementation = VenueFormat.class,
                description = "The kind of pitch; required on the first camera, not checked on later ones. "
                        + "Standard dimensions: FUTSAL_5V5 40 x 20 m, FOOTBALL_7V7 65 x 45 m, FOOTBALL_11V11 105 x 68 m")
        String format,

        @Schema(description = "Measured length in metres; give it with widthM, or leave both out for the standard",
                example = "105")
        @Positive Double lengthM,

        @Schema(description = "Measured width in metres; give it with lengthM, or leave both out for the standard",
                example = "68")
        @Positive Double widthM) {
}

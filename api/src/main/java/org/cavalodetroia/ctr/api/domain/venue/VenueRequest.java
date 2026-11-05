package org.cavalodetroia.ctr.api.domain.venue;

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
public record VenueRequest(VenueFormat format, @Positive Double lengthM, @Positive Double widthM) {
}

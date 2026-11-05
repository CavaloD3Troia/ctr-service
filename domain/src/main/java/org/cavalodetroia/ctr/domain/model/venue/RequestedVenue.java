package org.cavalodetroia.ctr.domain.model.venue;

import org.cavalodetroia.ctr.domain.exception.VenueSpecException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The venue as a camera sent it. Only the first camera of a session sets the venue, so the format is checked when
 * the session opens ({@link #toSpec()}); a later camera's venue is ignored, whatever its format. The measures go
 * together on every camera.
 *
 * @param format  the name of a {@link VenueFormat}, or {@code null} when the camera sent none
 * @param lengthM the measured length in metres, or {@code null} for the standard of the format
 * @param widthM  the measured width in metres, or {@code null} for the standard of the format
 */
public record RequestedVenue(String format, Double lengthM, Double widthM) {

    private static final String FORMATS = Arrays.stream(VenueFormat.values())
            .map(Enum::name)
            .collect(Collectors.joining(", "));

    /**
     * @throws VenueSpecException when only one of the measures is given
     */
    public RequestedVenue {
        if (lengthM == null && widthM != null) {
            throw new VenueSpecException("venue.lengthM", "must be given together with venue.widthM");
        }
        if (widthM == null && lengthM != null) {
            throw new VenueSpecException("venue.widthM", "must be given together with venue.lengthM");
        }
    }

    /**
     * @return the venue with its measures, or with the standard dimensions of the format when it has none
     * @throws VenueSpecException when the format is missing or not a {@link VenueFormat}, or a measure is not
     *                            positive
     */
    public VenueSpec toSpec() {
        final VenueFormat venueFormat = venueFormat();
        if (lengthM == null) {
            return VenueSpec.standard(venueFormat);
        }
        return new VenueSpec(venueFormat, lengthM, widthM);
    }

    private VenueFormat venueFormat() {
        if (format == null) {
            throw new VenueSpecException("venue.format", "must not be null");
        }
        try {
            return VenueFormat.valueOf(format);
        } catch (final IllegalArgumentException notAFormat) {
            throw new VenueSpecException("venue.format", "must be one of " + FORMATS);
        }
    }
}

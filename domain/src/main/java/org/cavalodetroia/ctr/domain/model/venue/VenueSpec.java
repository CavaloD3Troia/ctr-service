package org.cavalodetroia.ctr.domain.model.venue;

import org.cavalodetroia.ctr.domain.exception.VenueSpecException;

/**
 * The pitch a session was filmed on. Always complete: when the user gives no measures, the standard dimensions of
 * the format are filled in.
 *
 * @param format  the kind of pitch
 * @param lengthM the length in metres, positive
 * @param widthM  the width in metres, positive
 */
public record VenueSpec(VenueFormat format, double lengthM, double widthM) {

    public VenueSpec {
        if (format == null) {
            throw new VenueSpecException("venue.format", "must not be null");
        }
        requirePositive("venue.lengthM", lengthM);
        requirePositive("venue.widthM", widthM);
    }

    /**
     * @return the venue of this format with its standard dimensions
     */
    public static VenueSpec standard(final VenueFormat format) {
        return new VenueSpec(format, format.standardLengthM(), format.standardWidthM());
    }

    private static void requirePositive(final String field, final double metres) {
        if (!(metres > 0)) {
            throw new VenueSpecException(field, "must be greater than 0");
        }
    }
}

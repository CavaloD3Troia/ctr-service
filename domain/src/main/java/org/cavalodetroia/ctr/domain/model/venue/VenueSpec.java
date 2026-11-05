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
     * The venue as the user described it.
     *
     * @param format  the kind of pitch, required
     * @param lengthM the measured length in metres, or {@code null} for the standard of the format
     * @param widthM  the measured width in metres, or {@code null} for the standard of the format
     * @return the measured venue, or the standard one when both measures are {@code null}
     * @throws VenueSpecException when the format is missing, when only one measure is given, or when a measure is
     *                            not positive
     */
    public static VenueSpec of(final VenueFormat format, final Double lengthM, final Double widthM) {
        if (format == null) {
            throw new VenueSpecException("venue.format", "must not be null");
        }
        if (lengthM == null && widthM == null) {
            return new VenueSpec(format, format.standardLengthM(), format.standardWidthM());
        }
        if (lengthM == null) {
            throw new VenueSpecException("venue.lengthM", "must be given together with venue.widthM");
        }
        if (widthM == null) {
            throw new VenueSpecException("venue.widthM", "must be given together with venue.lengthM");
        }
        return new VenueSpec(format, lengthM, widthM);
    }

    private static void requirePositive(final String field, final double metres) {
        if (!(metres > 0)) {
            throw new VenueSpecException(field, "must be greater than 0");
        }
    }
}

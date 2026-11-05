package org.cavalodetroia.ctr.domain.model.venue;

/**
 * The kind of pitch, with the standard dimensions used when the user did not measure it.
 */
public enum VenueFormat {
    FUTSAL_5V5(40, 20),
    FOOTBALL_7V7(65, 45),
    FOOTBALL_11V11(105, 68);

    private final double standardLengthM;
    private final double standardWidthM;

    VenueFormat(final double standardLengthM, final double standardWidthM) {
        this.standardLengthM = standardLengthM;
        this.standardWidthM = standardWidthM;
    }

    public double standardLengthM() {
        return standardLengthM;
    }

    public double standardWidthM() {
        return standardWidthM;
    }
}

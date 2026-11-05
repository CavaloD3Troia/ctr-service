package org.cavalodetroia.ctr.domain.exception;

/**
 * The venue of a registration breaks a rule: a missing format, one measure without the other, a measure that is
 * not positive, or no venue at all on the first camera of a session.
 */
public class VenueSpecException extends RuntimeException {

    private final String field;
    private final String reason;

    /**
     * @param field  the path of the offending value in the registration, for example {@code venue.widthM}
     * @param reason what is wrong with it, for example {@code must not be null}
     */
    public VenueSpecException(final String field, final String reason) {
        super(field + " " + reason);
        this.field = field;
        this.reason = reason;
    }

    public String field() {
        return field;
    }

    public String reason() {
        return reason;
    }
}

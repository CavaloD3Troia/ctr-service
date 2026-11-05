package org.cavalodetroia.ctr.domain.exception;

/**
 * The clip in the bucket has another size than the camera confirmed, for example a truncated upload.
 */
public class SizeMismatchException extends RuntimeException {

    /**
     * @param expected the size the camera confirmed
     * @param actual   the size of the object in the bucket
     */
    public SizeMismatchException(final String objectKey, final long expected, final long actual) {
        super("The object at '" + objectKey + "' has " + actual + " bytes, the confirmation says " + expected);
    }
}

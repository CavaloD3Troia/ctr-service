package org.cavalodetroia.ctr.domain.exception;

/**
 * The clip in the bucket has another etag than the camera confirmed: it is not the object the camera uploaded.
 */
public class EtagMismatchException extends RuntimeException {

    /**
     * @param expected the etag the camera confirmed
     * @param actual   the etag of the object in the bucket
     */
    public EtagMismatchException(final String objectKey, final String expected, final String actual) {
        super("The object at '" + objectKey + "' has the etag " + actual + ", the confirmation says " + expected);
    }
}

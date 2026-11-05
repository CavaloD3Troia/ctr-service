package org.cavalodetroia.ctr.domain.exception;

/**
 * The clip a camera confirmed is not in the bucket.
 */
public class ObjectNotFoundException extends RuntimeException {

    public ObjectNotFoundException(final String objectKey) {
        super("No object at '" + objectKey + "'");
    }
}

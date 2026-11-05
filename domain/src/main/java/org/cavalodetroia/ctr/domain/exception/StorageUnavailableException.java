package org.cavalodetroia.ctr.domain.exception;

/**
 * The object storage could not answer: unreachable, too slow, or refusing ctr-service's credentials. Transient from
 * the client's point of view, so it may try again later.
 */
public class StorageUnavailableException extends RuntimeException {

    public StorageUnavailableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}

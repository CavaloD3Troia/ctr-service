package org.cavalodetroia.ctr.domain.exception;

/**
 * An {@code Idempotency-Key} came back with a different request than the one it was first used for.
 */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("The Idempotency-Key was already used for a different request");
    }
}

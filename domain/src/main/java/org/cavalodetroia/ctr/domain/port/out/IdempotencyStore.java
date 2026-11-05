package org.cavalodetroia.ctr.domain.port.out;

import org.cavalodetroia.ctr.domain.model.idempotency.StoredResponse;

import java.util.Optional;

/**
 * The responses given to requests that carried an {@code Idempotency-Key}, so a retry gets the same answer instead
 * of doing the work twice. A key is remembered for 24 hours.
 */
public interface IdempotencyStore {

    /**
     * @param key the {@code Idempotency-Key} of the request
     * @return the response stored for the key in the last 24 hours, if any
     */
    Optional<StoredResponse> find(String key);

    /**
     * Stores the response for the key, replacing one older than 24 hours.
     *
     * @param key      the {@code Idempotency-Key} of the request
     * @param response the response given
     */
    void save(String key, StoredResponse response);
}

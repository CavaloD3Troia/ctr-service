package org.cavalodetroia.ctr.domain.model.idempotency;

import java.util.Objects;

/**
 * The response given to a request that carried an {@code Idempotency-Key}, kept to replay it.
 *
 * @param requestHash a hash of the request it answered, to tell a repeat from a different request with the same key
 * @param status      the HTTP status
 * @param body        the response body as sent
 */
public record StoredResponse(String requestHash, int status, String body) {

    public StoredResponse {
        Objects.requireNonNull(requestHash, "requestHash");
        Objects.requireNonNull(body, "body");
    }
}

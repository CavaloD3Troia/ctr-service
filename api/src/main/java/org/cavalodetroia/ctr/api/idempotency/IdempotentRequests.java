package org.cavalodetroia.ctr.api.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import org.cavalodetroia.ctr.domain.exception.IdempotencyKeyReusedException;
import org.cavalodetroia.ctr.domain.model.idempotency.StoredResponse;
import org.cavalodetroia.ctr.domain.port.out.IdempotencyStore;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@value #HEADER} for POST endpoints. Without the header the request just runs. With it, the first request runs and
 * its response is stored; a repeat with the same key, method, path and body gets the stored response back without
 * running again; the same key with anything else is {@link IdempotencyKeyReusedException}.
 * <p>
 * The response is stored after the request succeeded, outside its transaction. A crash in between loses only the
 * replay: the retry runs again and the domain answers it (a camera registered twice is a conflict).
 */
@Component
public class IdempotentRequests {

    public static final String HEADER = "Idempotency-Key";
    public static final int MAX_KEY_LENGTH = 255;

    private final IdempotencyStore store;
    private final JsonMapper jsonMapper;

    public IdempotentRequests(final IdempotencyStore store, final JsonMapper jsonMapper) {
        this.store = store;
        this.jsonMapper = jsonMapper;
    }

    /**
     * @param key          the {@value #HEADER} header, or {@code null} when the request has none
     * @param http         the request, for its method and path
     * @param body         the parsed request body
     * @param responseType the type of the response body, to replay a stored one
     * @param request      runs the request
     * @return the response of {@code request}, or the stored one for a repeat
     * @throws IdempotencyKeyReusedException when the key was used for a different request
     */
    public <T> ResponseEntity<T> respond(final String key,
                                         final HttpServletRequest http,
                                         final Object body,
                                         final Class<T> responseType,
                                         final Supplier<ResponseEntity<T>> request) {
        if (key == null) {
            return request.get();
        }
        final String requestHash = hash(http.getMethod() + " " + http.getRequestURI() + "\n"
                + jsonMapper.writeValueAsString(body));
        final Optional<StoredResponse> stored = store.find(key);
        if (stored.isPresent()) {
            return replay(stored.get(), requestHash, responseType);
        }
        final ResponseEntity<T> response = request.get();
        store.save(key, new StoredResponse(requestHash, response.getStatusCode().value(),
                jsonMapper.writeValueAsString(response.getBody())));
        return response;
    }

    private <T> ResponseEntity<T> replay(final StoredResponse stored, final String requestHash, final Class<T> type) {
        if (!stored.requestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        return ResponseEntity.status(stored.status()).body(jsonMapper.readValue(stored.body(), type));
    }

    private static String hash(final String request) {
        try {
            final MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(request.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Every JDK must ship SHA-256 (MessageDigest javadoc), so this cannot happen
            throw new IllegalStateException(e);
        }
    }
}

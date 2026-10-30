package org.cavalodetroia.ctr.api.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * The stable {@code code} property of every problem response. Clients switch on it, never on the message.
 */
public final class ErrorCode {

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String EVENT_NOT_FOUND = "EVENT_NOT_FOUND";
    public static final String JOB_STATUS_NOT_FOUND = "JOB_STATUS_NOT_FOUND";
    public static final String INTERNAL = "INTERNAL";

    private ErrorCode() {
    }

    /**
     * For errors Spring MVC raises on its own: unreadable JSON, a bad path variable, an unsupported method...
     */
    static String forStatus(final HttpStatusCode status) {
        if (status.value() == HttpStatus.BAD_REQUEST.value()) {
            return VALIDATION_FAILED;
        }
        if (status.is5xxServerError()) {
            return INTERNAL;
        }
        final HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.name() : "HTTP_" + status.value();
    }
}

package org.cavalodetroia.ctr.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.cavalodetroia.ctr.domain.common.Uuid7;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Gives every request a correlation id: the caller's {@value #HEADER} when it is a safe token, otherwise a new
 * {@code CTR-<uuidv7>}. The id is in the MDC as {@value #MDC_KEY} for every log line of the request and is echoed
 * back in the {@value #HEADER} response header. Runs first, so even the filters after it log with the id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlation_id";

    // Anything else (too long, spaces, control characters) could forge log lines or bloat them, so it is replaced
    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final String GENERATED_PREFIX = "CTR-";

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        final String correlationId = correlationIdOf(request);
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Also drops what the request added after us (session_id, camera): the thread serves other requests next
            MDC.clear();
        }
    }

    private static String correlationIdOf(final HttpServletRequest request) {
        final String received = request.getHeader(HEADER);
        if (received != null && ACCEPTED.matcher(received).matches()) {
            return received;
        }
        return GENERATED_PREFIX + Uuid7.generate();
    }
}

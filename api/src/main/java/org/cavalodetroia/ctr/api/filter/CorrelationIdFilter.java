package org.cavalodetroia.ctr.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Puts a correlation id in the MDC for the whole request. The Kafka publishers copy it into a record header, so the
 * same id follows the request into the services downstream.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    /** Same key as infra's CorrelationId, which reads it to set the Kafka header. */
    public static final String MDC_KEY = "correlationId";

    // The id ends up in every log line, so only accept something that cannot forge one
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        final String incoming = request.getHeader(HEADER);
        final String correlationId = incoming != null && VALID_ID.matcher(incoming).matches()
                ? incoming
                : "ctr-service-" + UUID.randomUUID();

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}

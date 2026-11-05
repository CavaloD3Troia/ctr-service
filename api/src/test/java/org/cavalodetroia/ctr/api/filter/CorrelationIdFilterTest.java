package org.cavalodetroia.ctr.api.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-8: the correlation id of a request, in the response header and in the MDC while the request runs.
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/anything");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicReference<String> inMdcDuringRequest = new AtomicReference<>();
    private final FilterChain chain = (req, res) -> inMdcDuringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void should_echoTheReceivedId_when_theHeaderIsValid() throws Exception {
        request.addHeader(CorrelationIdFilter.HEADER, "gui-42:retry.1_a-b");

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("gui-42:retry.1_a-b");
        assertThat(inMdcDuringRequest.get()).isEqualTo("gui-42:retry.1_a-b");
    }

    @Test
    void should_generateACtrPrefixedUuid7_when_theHeaderIsAbsent() throws Exception {
        filter.doFilter(request, response, chain);

        final String generated = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(generated).startsWith("CTR-");
        final UUID id = UUID.fromString(generated.substring("CTR-".length()));
        assertThat(id.version()).isEqualTo(7);
        assertThat(inMdcDuringRequest.get()).isEqualTo(generated);
    }

    @Test
    void should_replaceTheReceivedId_when_itIsLongerThan128Characters() throws Exception {
        request.addHeader(CorrelationIdFilter.HEADER, "a".repeat(200));

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).startsWith("CTR-").hasSizeLessThan(200);
    }

    @Test
    void should_replaceTheReceivedId_when_itHasCharactersOutsideTheAllowList() throws Exception {
        request.addHeader(CorrelationIdFilter.HEADER, "abc\" level=ERROR");

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).startsWith("CTR-");
    }

    @Test
    void should_clearTheMdc_when_theRequestEnds() throws Exception {
        final FilterChain addsMore = (req, res) -> MDC.put("session_id", "s-1");

        filter.doFilter(request, response, addsMore);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}

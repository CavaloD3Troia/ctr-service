package org.cavalodetroia.ctr.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-9: with the logging settings of {@code application.yml}, a log line is one JSON object with the service name,
 * the level, the message and the MDC fields. No beans are needed, only the logging system Spring Boot sets up.
 */
@SpringBootTest(classes = StructuredLoggingTest.NoBeans.class)
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(StructuredLoggingTest.class);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void should_writeOneJsonLineWithServiceLevelMessageAndMdc_when_logging(final CapturedOutput output) {
        MDC.put("correlation_id", "CTR-test-1");
        MDC.put("session_id", "sess-7f3c");
        MDC.put("camera", "LEFT");

        LOGGER.info("operation=test, message=Structured logging check");

        final String line = Arrays.stream(output.getOut().split("\\R"))
                .filter(candidate -> candidate.contains("Structured logging check"))
                .findFirst()
                .orElseThrow();
        final JsonNode json = JsonMapper.shared().readTree(line);
        assertThat(json.path("service").asString()).isEqualTo("ctr-service");
        assertThat(json.path("level").asString()).isEqualTo("INFO");
        assertThat(json.path("message").asString()).isEqualTo("operation=test, message=Structured logging check");
        assertThat(json.path("correlation_id").asString()).isEqualTo("CTR-test-1");
        assertThat(json.path("session_id").asString()).isEqualTo("sess-7f3c");
        assertThat(json.path("camera").asString()).isEqualTo("LEFT");
    }

    @Configuration(proxyBeanMethods = false)
    static class NoBeans {
    }
}

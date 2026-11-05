package org.cavalodetroia.ctr.domain.model.upload;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC-6: the domain refuses a confirmation the API should have rejected (sizeBytes and durationMs positive, startMs
 * not negative, etag not blank).
 */
class ClipConfirmationTest {

    @Test
    void should_coverItsStartAndDuration_when_askedForItsWindow() {
        assertThat(new ClipConfirmation(1, "e", 900_000, 300_000).window()).isEqualTo(new Window(900_000, 300_000));
    }

    @Test
    void should_accept_when_theValuesAreAtTheEdgesOfTheRules() {
        assertThat(new ClipConfirmation(1, "e", 0, 1)).isNotNull();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "sizeBytes zero,     0, e,   0, 1",
            "etag blank,         1, ' ', 0, 1",
            "startMs negative,   1, e,  -1, 1",
            "durationMs zero,    1, e,   0, 0"})
    void should_reject_when_aValueBreaksItsRule(final String rule, final long sizeBytes, final String etag,
                                                final long startMs, final long durationMs) {
        assertThatThrownBy(() -> new ClipConfirmation(sizeBytes, etag, startMs, durationMs))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

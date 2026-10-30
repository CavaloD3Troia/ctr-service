package org.cavalodetroia.ctr.domain.event;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class NewEventTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    void should_reject_when_nameIsBlank(final String name) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new NewEvent(name, "FOOTBALL"))
                .withMessage("name is mandatory");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    void should_reject_when_sportIsBlank(final String sport) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new NewEvent("Benfica vs Porto", sport))
                .withMessage("sport is mandatory");
    }
}

package org.cavalodetroia.ctr.api.event;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural validation at the edge: a request the domain must never see is rejected by the record's constraints.
 */
class CreateEventRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void stopValidator() {
        factory.close();
    }

    @Test
    void should_accept_when_nameIsPresent() {
        assertThat(validator.validate(new CreateEventRequest("Benfica vs Porto"))).isEmpty();
    }

    @Test
    void should_accept_when_nameHasExactly200Characters() {
        assertThat(validator.validate(new CreateEventRequest("x".repeat(200)))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void should_rejectName_when_blank(final String name) {
        assertThat(validator.validate(new CreateEventRequest(name)))
                .extracting(CreateEventRequestTest::property)
                .containsExactly("name");
    }

    @Test
    void should_rejectName_when_longerThan200Characters() {
        assertThat(validator.validate(new CreateEventRequest("x".repeat(201))))
                .extracting(CreateEventRequestTest::property)
                .containsExactly("name");
    }

    private static String property(final ConstraintViolation<CreateEventRequest> violation) {
        return violation.getPropertyPath().toString();
    }
}

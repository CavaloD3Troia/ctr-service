package org.cavalodetroia.ctr.domain.model.venue;

import org.cavalodetroia.ctr.domain.exception.VenueSpecException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC-4, AC-11: measured venues are kept, unmeasured ones get the standard of their format, half-measured ones are
 * rejected.
 */
class VenueSpecTest {

    @ParameterizedTest(name = "{0} -> {1} x {2}")
    @CsvSource({
            "FUTSAL_5V5,     40,  20",
            "FOOTBALL_7V7,   65,  45",
            "FOOTBALL_11V11, 105, 68"})
    void should_useTheStandardDimensionsOfTheFormat_when_noMeasuresAreGiven(final VenueFormat format,
                                                                           final double length,
                                                                           final double width) {
        assertThat(VenueSpec.of(format, null, null)).isEqualTo(new VenueSpec(format, length, width));
    }

    @Test
    void should_keepTheMeasures_when_bothAreGiven() {
        assertThat(VenueSpec.of(VenueFormat.FOOTBALL_7V7, 60.5, 40.0))
                .isEqualTo(new VenueSpec(VenueFormat.FOOTBALL_7V7, 60.5, 40.0));
    }

    @Test
    void should_reject_when_onlyTheLengthIsGiven() {
        assertThatThrownBy(() -> VenueSpec.of(VenueFormat.FOOTBALL_7V7, 60.0, null))
                .isInstanceOfSatisfying(VenueSpecException.class,
                        e -> assertThat(e.field()).isEqualTo("venue.widthM"));
    }

    @Test
    void should_reject_when_onlyTheWidthIsGiven() {
        assertThatThrownBy(() -> VenueSpec.of(VenueFormat.FOOTBALL_7V7, null, 40.0))
                .isInstanceOfSatisfying(VenueSpecException.class,
                        e -> assertThat(e.field()).isEqualTo("venue.lengthM"));
    }

    @Test
    void should_reject_when_theFormatIsMissing() {
        assertThatThrownBy(() -> VenueSpec.of(null, null, null))
                .isInstanceOfSatisfying(VenueSpecException.class,
                        e -> assertThat(e.field()).isEqualTo("venue.format"));
    }

    @Test
    void should_reject_when_aMeasureIsNotPositive() {
        assertThatThrownBy(() -> VenueSpec.of(VenueFormat.FUTSAL_5V5, 0.0, 20.0))
                .isInstanceOfSatisfying(VenueSpecException.class,
                        e -> assertThat(e.field()).isEqualTo("venue.lengthM"));
        assertThatThrownBy(() -> VenueSpec.of(VenueFormat.FUTSAL_5V5, 40.0, -1.0))
                .isInstanceOfSatisfying(VenueSpecException.class,
                        e -> assertThat(e.field()).isEqualTo("venue.widthM"));
    }
}

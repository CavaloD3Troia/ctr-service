package org.cavalodetroia.ctr.domain.model.venue;

import org.cavalodetroia.ctr.domain.exception.VenueSpecException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC-4, AC-11: the standard dimensions of each format, and a stored venue always has a format and positive measures.
 */
class VenueSpecTest {

    @ParameterizedTest(name = "{0} -> {1} x {2}")
    @CsvSource({
            "FUTSAL_5V5,     40,  20",
            "FOOTBALL_7V7,   65,  45",
            "FOOTBALL_11V11, 105, 68"})
    void should_haveTheStandardDimensionsOfTheFormat_when_standard(final VenueFormat format,
                                                                  final double length,
                                                                  final double width) {
        assertThat(VenueSpec.standard(format)).isEqualTo(new VenueSpec(format, length, width));
    }

    @Test
    void should_reject_when_theFormatIsMissing() {
        assertThatThrownBy(() -> new VenueSpec(null, 40, 20))
                .isInstanceOfSatisfying(VenueSpecException.class,
                        e -> assertThat(e.field()).isEqualTo("venue.format"));
    }

    @Test
    void should_reject_when_aMeasureIsNotPositive() {
        assertThatThrownBy(() -> new VenueSpec(VenueFormat.FUTSAL_5V5, 0, 20))
                .isInstanceOfSatisfying(VenueSpecException.class,
                        e -> assertThat(e.field()).isEqualTo("venue.lengthM"));
        assertThatThrownBy(() -> new VenueSpec(VenueFormat.FUTSAL_5V5, 40, -1))
                .isInstanceOfSatisfying(VenueSpecException.class,
                        e -> assertThat(e.field()).isEqualTo("venue.widthM"));
    }
}

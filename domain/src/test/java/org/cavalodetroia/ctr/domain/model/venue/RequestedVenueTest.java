package org.cavalodetroia.ctr.domain.model.venue;

import org.cavalodetroia.ctr.domain.exception.VenueSpecException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC-4, AC-6, AC-11: a measured venue is kept, an unmeasured one gets the standard of its format, half-measured ones
 * are rejected on any camera, and the format is only checked when the venue is used.
 */
class RequestedVenueTest {

    @Test
    void should_keepTheMeasures_when_bothAreGiven() {
        assertThat(new RequestedVenue("FOOTBALL_7V7", 60.5, 40.0).toSpec())
                .isEqualTo(new VenueSpec(VenueFormat.FOOTBALL_7V7, 60.5, 40.0));
    }

    @ParameterizedTest(name = "{0} -> {1} x {2}")
    @CsvSource({
            "FUTSAL_5V5,     40,  20",
            "FOOTBALL_7V7,   65,  45",
            "FOOTBALL_11V11, 105, 68"})
    void should_useTheStandardDimensionsOfTheFormat_when_noMeasuresAreGiven(final VenueFormat format,
                                                                           final double length,
                                                                           final double width) {
        assertThat(new RequestedVenue(format.name(), null, null).toSpec())
                .isEqualTo(new VenueSpec(format, length, width));
    }

    @Test
    void should_reject_when_onlyTheLengthIsGiven() {
        assertThatThrownBy(() -> new RequestedVenue("FOOTBALL_7V7", 60.0, null))
                .isInstanceOfSatisfying(VenueSpecException.class,
                        e -> assertThat(e.field()).isEqualTo("venue.widthM"));
    }

    @Test
    void should_reject_when_onlyTheWidthIsGiven() {
        assertThatThrownBy(() -> new RequestedVenue(null, null, 40.0))
                .isInstanceOfSatisfying(VenueSpecException.class,
                        e -> assertThat(e.field()).isEqualTo("venue.lengthM"));
    }

    @Test
    void should_rejectTheFormat_when_itIsMissing() {
        final RequestedVenue venue = new RequestedVenue(null, null, null);

        assertThatThrownBy(venue::toSpec)
                .isInstanceOfSatisfying(VenueSpecException.class, e -> {
                    assertThat(e.field()).isEqualTo("venue.format");
                    assertThat(e.reason()).isEqualTo("must not be null");
                });
    }

    @Test
    void should_rejectTheFormatNamingTheKnownOnes_when_itIsNotAFormat() {
        final RequestedVenue venue = new RequestedVenue("HOCKEY", null, null);

        assertThatThrownBy(venue::toSpec)
                .isInstanceOfSatisfying(VenueSpecException.class, e -> {
                    assertThat(e.field()).isEqualTo("venue.format");
                    assertThat(e.reason()).isEqualTo("must be one of FUTSAL_5V5, FOOTBALL_7V7, FOOTBALL_11V11");
                });
    }

    @Test
    void should_rejectAMeasure_when_itIsNotPositive() {
        assertThatThrownBy(() -> new RequestedVenue("FUTSAL_5V5", 0.0, 20.0).toSpec())
                .isInstanceOfSatisfying(VenueSpecException.class,
                        e -> assertThat(e.field()).isEqualTo("venue.lengthM"));
    }
}

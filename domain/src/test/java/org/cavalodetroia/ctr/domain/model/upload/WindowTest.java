package org.cavalodetroia.ctr.domain.model.upload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-2, AC-3, AC-10: the overlap rule. Start is the later of the two starts, end the earlier of the two ends.
 */
class WindowTest {

    @Test
    void should_startAtTheLaterStartAndEndAtTheEarlierEnd_when_theClipsOverlap() {
        final Window left = new Window(900_000, 300_000);
        final Window right = new Window(898_000, 300_000);

        assertThat(Window.overlap(left, right)).isEqualTo(new Window(900_000, 298_000));
    }

    @Test
    void should_beTheInnerClip_when_oneClipContainsTheOther() {
        assertThat(Window.overlap(new Window(0, 600_000), new Window(100_000, 200_000)))
                .isEqualTo(new Window(100_000, 200_000));
    }

    @Test
    void should_haveDurationZero_when_oneClipEndsWhereTheOtherStarts() {
        final Window overlap = Window.overlap(new Window(0, 300_000), new Window(300_000, 300_000));

        assertThat(overlap).isEqualTo(new Window(300_000, 0));
        assertThat(overlap.isEmpty()).isTrue();
    }

    @Test
    void should_haveANegativeDuration_when_theClipsAreDisjoint() {
        final Window overlap = Window.overlap(new Window(0, 100_000), new Window(400_000, 100_000));

        assertThat(overlap).isEqualTo(new Window(400_000, -300_000));
        assertThat(overlap.isEmpty()).isTrue();
    }

    @ParameterizedTest(name = "[{0}, +{1}] and [{2}, +{3}]")
    @CsvSource({
            "900000, 300000, 898000, 300000",
            "0,      300000, 300000, 300000",
            "0,      100000, 400000, 100000",
            "5,      10,     0,      600000"})
    void should_notDependOnTheOrderOfTheClips_when_computingTheOverlap(final long startA, final long durationA,
                                                                     final long startB, final long durationB) {
        final Window a = new Window(startA, durationA);
        final Window b = new Window(startB, durationB);

        assertThat(Window.overlap(a, b)).isEqualTo(Window.overlap(b, a));
    }

    @Test
    void should_notBeEmpty_when_theDurationIsOneMillisecond() {
        assertThat(new Window(0, 1).isEmpty()).isFalse();
        assertThat(new Window(0, 1).endMs()).isEqualTo(1);
    }
}

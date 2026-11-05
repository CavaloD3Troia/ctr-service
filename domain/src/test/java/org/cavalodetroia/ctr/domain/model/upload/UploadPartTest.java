package org.cavalodetroia.ctr.domain.model.upload;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC-2, AC-3, AC-10: a part closes with its window, and has a window exactly when it is closed.
 */
class UploadPartTest {

    private static final Window WINDOW = new Window(900_000, 298_000);

    @Test
    void should_completeWithTheWindow_when_theTransitionIsComplete() {
        assertThat(UploadPart.waiting(3).after(new PartTransition.Complete(WINDOW)))
                .isEqualTo(new UploadPart(3, PartStatus.COMPLETE, WINDOW, null));
    }

    @Test
    void should_markNoOverlapWithTheEmptyWindow_when_theTransitionIsNoOverlap() {
        final Window empty = new Window(300_000, 0);

        assertThat(UploadPart.waiting(3).after(new PartTransition.NoOverlap(empty)))
                .isEqualTo(new UploadPart(3, PartStatus.NO_OVERLAP, empty, null));
    }

    @Test
    void should_stayTheSame_when_thereIsNoTransition() {
        assertThat(UploadPart.waiting(3).after(PartTransition.NONE)).isEqualTo(UploadPart.waiting(3));
    }

    @Test
    void should_beUnpublished_until_ItHasAPublicationTime() {
        assertThat(UploadPart.waiting(3).published()).isFalse();
        assertThat(new UploadPart(3, PartStatus.COMPLETE, WINDOW, Instant.parse("2026-11-05T12:00:00Z"))
                .published()).isTrue();
    }

    @Test
    void should_reject_when_theStatusDisagreesWithTheWindow() {
        assertThatThrownBy(() -> new UploadPart(3, PartStatus.WAITING, WINDOW, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UploadPart(3, PartStatus.COMPLETE, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UploadPart(3, PartStatus.NO_OVERLAP, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

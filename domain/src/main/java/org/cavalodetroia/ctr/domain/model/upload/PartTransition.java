package org.cavalodetroia.ctr.domain.model.upload;

import java.util.Objects;

/**
 * What a confirmed clip did to its part: nothing yet (another camera still has to upload its clip), or the part
 * closed, {@link Complete} with the window to publish or {@link NoOverlap} when the clips share no stretch of the
 * recording.
 */
public sealed interface PartTransition {

    PartTransition NONE = new None();

    record None() implements PartTransition {
    }

    /**
     * @param window the part's window: the clip's own for {@code SINGLE}, the overlap of the two clips otherwise
     */
    record Complete(Window window) implements PartTransition {

        public Complete {
            Objects.requireNonNull(window, "window");
        }
    }

    /**
     * @param window the empty overlap of the two clips, a duration of zero or below
     */
    record NoOverlap(Window window) implements PartTransition {

        public NoOverlap {
            Objects.requireNonNull(window, "window");
        }
    }
}

package org.cavalodetroia.ctr.domain.model.upload;

import java.util.Objects;

/**
 * What confirming a clip left behind.
 *
 * @param after            the session after the confirmation; the same session when {@code noop}
 * @param camera           the camera whose clip was confirmed
 * @param partIndex        the part of the clip, 0-based
 * @param part             what the confirmation did to the part; {@link PartTransition#NONE} when {@code noop}
 * @param sessionCompleted whether this confirmation completed the session
 * @param noop             whether the clip was already uploaded with the same confirmation, so nothing changed
 */
public record ClipCompletion(UploadSession after,
                             Camera camera,
                             int partIndex,
                             PartTransition part,
                             boolean sessionCompleted,
                             boolean noop) {

    public ClipCompletion {
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(part, "part");
        if (noop && (part != PartTransition.NONE || sessionCompleted)) {
            throw new IllegalArgumentException("A repeated confirmation changes neither the part nor the session");
        }
    }

    /**
     * @return the completion of a confirmation repeated for a clip already uploaded with it
     */
    static ClipCompletion noop(final UploadSession session, final Camera camera, final int partIndex) {
        return new ClipCompletion(session, camera, partIndex, PartTransition.NONE, false, true);
    }

    /**
     * @return the clip as the session holds it after the confirmation
     */
    public UploadClip clip() {
        return after.clip(camera, partIndex);
    }

    /**
     * @return where the clip's part stands after the confirmation
     */
    public PartState partState() {
        return after.partState(partIndex);
    }
}

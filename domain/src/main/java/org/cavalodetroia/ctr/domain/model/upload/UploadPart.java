package org.cavalodetroia.ctr.domain.model.upload;

import java.time.Instant;
import java.util.Objects;

/**
 * One part of the match, across every camera of the session. A {@link PartStatus#WAITING} part has no window; a
 * closed one ({@link PartStatus#COMPLETE} or {@link PartStatus#NO_OVERLAP}) has one.
 *
 * @param partIndex   the part, 0-based
 * @param status      whether every camera has uploaded its clip of it
 * @param window      the stretch of the recording the part covers once closed, {@code null} while waiting
 * @param publishedAt when the part was published, {@code null} until then
 */
public record UploadPart(int partIndex, PartStatus status, Window window, Instant publishedAt) {

    public UploadPart {
        Objects.requireNonNull(status, "status");
        if ((status == PartStatus.WAITING) == (window != null)) {
            throw new IllegalArgumentException("Part " + partIndex + " is " + status
                    + ": it has a window exactly when it is closed");
        }
    }

    /**
     * @return a part still waiting for the clips of its cameras
     */
    public static UploadPart waiting(final int partIndex) {
        return new UploadPart(partIndex, PartStatus.WAITING, null, null);
    }

    /**
     * @return this part after {@code transition}: closed with its window, or unchanged for
     *         {@link PartTransition#NONE}
     */
    public UploadPart after(final PartTransition transition) {
        return switch (transition) {
            case PartTransition.None none -> this;
            case PartTransition.Complete complete ->
                    new UploadPart(partIndex, PartStatus.COMPLETE, complete.window(), publishedAt);
            case PartTransition.NoOverlap noOverlap ->
                    new UploadPart(partIndex, PartStatus.NO_OVERLAP, noOverlap.window(), publishedAt);
        };
    }

    public boolean isPublished() {
        return publishedAt != null;
    }
}

package org.cavalodetroia.ctr.domain.model.upload;

import java.time.Instant;
import java.util.Objects;

/**
 * One part of the match, across every camera of the session.
 *
 * @param partIndex   the part, 0-based
 * @param status      whether every camera has uploaded its clip of it
 * @param startMs     where the part's window starts on the match clock, in milliseconds, once its clips are in
 * @param durationMs  how long the window lasts, in milliseconds, once its clips are in; not positive when they don't
 *                    overlap
 * @param publishedAt when the part was published for extraction, or {@code null} while it is not
 */
public record UploadPart(int partIndex, PartStatus status, Long startMs, Long durationMs, Instant publishedAt) {

    public UploadPart {
        Objects.requireNonNull(status, "status");
    }

    /**
     * @return a part no camera has uploaded yet
     */
    public static UploadPart waiting(final int partIndex) {
        return new UploadPart(partIndex, PartStatus.WAITING, null, null, null);
    }

    public boolean published() {
        return publishedAt != null;
    }

    /**
     * @return whether {@link #startMs} and {@link #durationMs} are a window to cut: only for a
     *         {@link PartStatus#COMPLETE} part, since a {@link PartStatus#NO_OVERLAP} one's is empty
     */
    public boolean hasWindow() {
        return status == PartStatus.COMPLETE;
    }
}

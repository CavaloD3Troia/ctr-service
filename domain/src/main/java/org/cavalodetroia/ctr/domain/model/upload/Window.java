package org.cavalodetroia.ctr.domain.model.upload;

/**
 * A stretch of the recording: where it starts and how long it lasts, in milliseconds from the start of the recording.
 *
 * @param startMs    where it starts
 * @param durationMs how long it lasts; zero or negative for the {@link #overlap} of two clips that don't overlap
 */
public record Window(long startMs, long durationMs) {

    /**
     * The stretch both windows cover: it starts at the later of the two starts and ends at the earlier of the two
     * ends. Two windows that don't overlap give a duration of zero (one ends where the other starts) or below; the
     * caller decides what that means. Symmetric in its arguments.
     *
     * @return the overlap of {@code a} and {@code b}
     */
    public static Window overlap(final Window a, final Window b) {
        final long start = Math.max(a.startMs, b.startMs);
        final long end = Math.min(a.endMs(), b.endMs());
        return new Window(start, end - start);
    }

    /**
     * @return where it ends: {@code startMs + durationMs}
     */
    public long endMs() {
        return startMs + durationMs;
    }

    /**
     * @return whether it covers nothing: a duration of zero or below
     */
    public boolean isEmpty() {
        return durationMs <= 0;
    }
}

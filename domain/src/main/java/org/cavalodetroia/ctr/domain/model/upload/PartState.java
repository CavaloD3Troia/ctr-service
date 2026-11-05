package org.cavalodetroia.ctr.domain.model.upload;

/**
 * Where one part stands as the app shows it: which camera it waits for, or how it ended.
 */
public enum PartState {
    /** Two cameras: the LEFT clip is missing (when both are, LEFT is named first). */
    WAITING_LEFT,
    /** Two cameras: the RIGHT clip is missing, or RIGHT has not registered yet. */
    WAITING_RIGHT,
    /** A single camera: its clip is missing. */
    WAITING,
    /** Every expected clip is in and they overlap. */
    COMPLETE,
    /** Every expected clip is in, but they don't overlap, so the part has no window. */
    NO_OVERLAP
}

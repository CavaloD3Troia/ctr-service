package org.cavalodetroia.ctr.domain.model.upload;

/**
 * Where one part stands as the cameras see it: which camera it still waits for, or how it closed.
 */
public enum PartState {
    /**
     * No camera has uploaded its clip of the part yet, or the single camera has not.
     */
    WAITING,
    /**
     * RIGHT has uploaded its clip of the part; LEFT has not.
     */
    WAITING_LEFT,
    /**
     * LEFT has uploaded its clip of the part; RIGHT has not, or has not registered yet.
     */
    WAITING_RIGHT,
    COMPLETE,
    NO_OVERLAP
}

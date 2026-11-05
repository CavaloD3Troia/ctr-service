package org.cavalodetroia.ctr.domain.model.upload;

import java.util.Objects;

/**
 * One part of the match, across every camera of the session.
 *
 * @param partIndex the part, 0-based
 * @param status    whether every camera has uploaded its clip of it
 */
public record UploadPart(int partIndex, PartStatus status) {

    public UploadPart {
        Objects.requireNonNull(status, "status");
    }
}

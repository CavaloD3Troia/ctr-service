package org.cavalodetroia.ctr.domain.model.upload;

import java.util.Objects;

/**
 * One camera's video of one part of the match.
 *
 * @param camera    the camera that films it
 * @param partIndex the part, 0-based
 * @param objectKey its key in the bucket (see {@link ObjectKeys#clipKey})
 * @param status    whether it is in the bucket yet
 */
public record UploadClip(Camera camera, int partIndex, String objectKey, ClipStatus status) {

    public UploadClip {
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(objectKey, "objectKey");
        Objects.requireNonNull(status, "status");
    }
}

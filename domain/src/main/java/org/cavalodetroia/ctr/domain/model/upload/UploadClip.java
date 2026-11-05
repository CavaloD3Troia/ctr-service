package org.cavalodetroia.ctr.domain.model.upload;

import java.time.Instant;
import java.util.Objects;

/**
 * One camera's video of one part of the match. What the camera reported about the video is only known once it is
 * {@link ClipStatus#UPLOADED}; until then those fields are {@code null}.
 *
 * @param camera     the camera that films it
 * @param partIndex  the part, 0-based
 * @param objectKey  its key in the bucket (see {@link ObjectKeys#clipKey})
 * @param status     whether it is in the bucket yet
 * @param sizeBytes  the size of the object, once uploaded
 * @param startMs    where the clip starts on the match clock, in milliseconds, once uploaded
 * @param durationMs how long the clip lasts, in milliseconds, once uploaded
 * @param uploadedAt when the upload was confirmed
 */
public record UploadClip(Camera camera,
                         int partIndex,
                         String objectKey,
                         ClipStatus status,
                         Long sizeBytes,
                         Long startMs,
                         Long durationMs,
                         Instant uploadedAt) {

    /**
     * @throws IllegalArgumentException when an uploaded clip lacks its size, window or upload time
     */
    public UploadClip {
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(objectKey, "objectKey");
        Objects.requireNonNull(status, "status");
        if (status == ClipStatus.UPLOADED
                && (sizeBytes == null || startMs == null || durationMs == null || uploadedAt == null)) {
            throw new IllegalArgumentException("Clip " + camera + " " + partIndex
                    + " is UPLOADED, so it needs sizeBytes, startMs, durationMs and uploadedAt");
        }
    }

    /**
     * @return a clip the camera has not uploaded yet
     */
    public static UploadClip pending(final Camera camera, final int partIndex, final String objectKey) {
        return new UploadClip(camera, partIndex, objectKey, ClipStatus.PENDING, null, null, null, null);
    }
}

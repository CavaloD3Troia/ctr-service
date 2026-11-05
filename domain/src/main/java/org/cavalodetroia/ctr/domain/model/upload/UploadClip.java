package org.cavalodetroia.ctr.domain.model.upload;

import java.time.Instant;
import java.util.Objects;

/**
 * One camera's video of one part of the match. A {@link ClipStatus#PENDING} clip has no confirmation and no
 * {@code uploadedAt}; an {@link ClipStatus#UPLOADED} one has both.
 *
 * @param camera       the camera that films it
 * @param partIndex    the part, 0-based
 * @param objectKey    its key in the bucket (see {@link ObjectKeys#clipKey})
 * @param status       whether it is in the bucket yet
 * @param confirmation what the camera said about it once uploaded, {@code null} while pending
 * @param uploadedAt   when ctr-service confirmed it was in the bucket, {@code null} while pending
 */
public record UploadClip(Camera camera,
                         int partIndex,
                         String objectKey,
                         ClipStatus status,
                         ClipConfirmation confirmation,
                         Instant uploadedAt) {

    public UploadClip {
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(objectKey, "objectKey");
        Objects.requireNonNull(status, "status");
        final boolean uploaded = status == ClipStatus.UPLOADED;
        if (uploaded != (confirmation != null) || uploaded != (uploadedAt != null)) {
            throw new IllegalArgumentException("Clip " + camera + " " + partIndex + " is " + status
                    + ": it has a confirmation and uploadedAt exactly when it is UPLOADED");
        }
    }

    /**
     * @return a clip the camera has not uploaded yet
     */
    public static UploadClip pending(final Camera camera, final int partIndex, final String objectKey) {
        return new UploadClip(camera, partIndex, objectKey, ClipStatus.PENDING, null, null);
    }

    /**
     * @param confirmation what the camera said about the clip
     * @param now          when ctr-service confirmed it was in the bucket
     * @return this clip, uploaded
     */
    public UploadClip uploaded(final ClipConfirmation confirmation, final Instant now) {
        return new UploadClip(camera, partIndex, objectKey, ClipStatus.UPLOADED, confirmation, now);
    }

    public boolean isUploaded() {
        return status == ClipStatus.UPLOADED;
    }
}

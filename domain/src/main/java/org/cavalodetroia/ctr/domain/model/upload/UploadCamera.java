package org.cavalodetroia.ctr.domain.model.upload;

import java.time.Instant;
import java.util.Objects;

/**
 * A camera registered on a session.
 *
 * @param camera      which camera
 * @param partCount   how many clips it uploads
 * @param contentType the media type of its clips, as it registered them
 * @param prefix      the URI of the folder its clips go to (see {@link ObjectKeys#prefix})
 * @param createdAt   when it registered
 */
public record UploadCamera(Camera camera, int partCount, String contentType, String prefix, Instant createdAt) {

    public UploadCamera {
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}

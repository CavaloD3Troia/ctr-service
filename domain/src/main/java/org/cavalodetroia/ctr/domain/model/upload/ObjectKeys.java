package org.cavalodetroia.ctr.domain.model.upload;

import java.util.UUID;

/**
 * Where a camera writes its clips: {@code events/<eventId>/sessions/<sessionId>/<CAMERA>/<partIndex>.mp4}, with
 * {@code partIndex} 0-based.
 */
public final class ObjectKeys {

    private static final String CLIP_EXTENSION = ".mp4";

    /**
     * The clip key relative to the camera prefix, as the camera is told to fill it in.
     */
    public static final String CLIP_KEY_TEMPLATE = "{partIndex}" + CLIP_EXTENSION;

    private ObjectKeys() {
    }

    /**
     * @return the object key of one clip inside the bucket, for example
     *         {@code events/<eventId>/sessions/<sessionId>/LEFT/39.mp4}
     */
    public static String clipKey(final UUID eventId, final UUID sessionId, final Camera camera, final int partIndex) {
        return folder(eventId, sessionId, camera) + partIndex + CLIP_EXTENSION;
    }

    /**
     * @return the URI of the folder that holds a camera's clips, for example
     *         {@code s3://<bucket>/events/<eventId>/sessions/<sessionId>/LEFT/}
     */
    public static String prefix(final String bucket, final UUID eventId, final UUID sessionId, final Camera camera) {
        return "s3://" + bucket + "/" + folder(eventId, sessionId, camera);
    }

    /**
     * @return the URI of one clip: the camera's {@link #prefix prefix} with {@link #CLIP_KEY_TEMPLATE} filled in, the
     *         way the camera is told to upload it, for example
     *         {@code s3://<bucket>/events/<eventId>/sessions/<sessionId>/LEFT/39.mp4}
     */
    public static String clipUri(final String prefix, final int partIndex) {
        return prefix + partIndex + CLIP_EXTENSION;
    }

    private static String folder(final UUID eventId, final UUID sessionId, final Camera camera) {
        return "events/" + eventId + "/sessions/" + sessionId + "/" + camera.name() + "/";
    }
}

package org.cavalodetroia.ctr.domain.model.upload;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-3, AC-11: the object key convention, {@code events/<eventId>/sessions/<sessionId>/<CAMERA>/<partIndex>.mp4}.
 */
class ObjectKeysTest {

    private static final UUID EVENT_ID = UUID.fromString("0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11");
    private static final UUID SESSION_ID = UUID.fromString("019a5b2e-7f3c-7000-8000-000000000001");

    @Test
    void should_keyTheFirstClipWithIndexZero_when_buildingAClipKey() {
        assertThat(ObjectKeys.clipKey(EVENT_ID, SESSION_ID, Camera.LEFT, 0)).isEqualTo(
                "events/0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11/sessions/019a5b2e-7f3c-7000-8000-000000000001/LEFT/0.mp4");
    }

    @Test
    void should_keyTheLastOfFortyClipsWithIndex39_when_buildingAClipKey() {
        assertThat(ObjectKeys.clipKey(EVENT_ID, SESSION_ID, Camera.RIGHT, 39)).isEqualTo(
                "events/0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11/sessions/019a5b2e-7f3c-7000-8000-000000000001/RIGHT/39.mp4");
    }

    @Test
    void should_pointAtTheCameraFolderInTheBucket_when_buildingAPrefix() {
        assertThat(ObjectKeys.prefix("cdt-videos", EVENT_ID, SESSION_ID, Camera.SINGLE)).isEqualTo(
                "s3://cdt-videos/events/0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11/sessions/019a5b2e-7f3c-7000-8000-000000000001/SINGLE/");
    }

    @Test
    void should_completeTheClipKey_when_thePrefixIsFollowedByTheFilledInTemplate() {
        final String prefix = ObjectKeys.prefix("cdt-videos", EVENT_ID, SESSION_ID, Camera.LEFT);
        final String clip = ObjectKeys.CLIP_KEY_TEMPLATE.replace("{partIndex}", "7");

        assertThat(prefix + clip).isEqualTo("s3://cdt-videos/" + ObjectKeys.clipKey(EVENT_ID, SESSION_ID, Camera.LEFT, 7));
    }

    // TH-152 AC-1, AC-2: the published uri is s3://<bucket>/<clip key>
    @Test
    void should_beTheBucketAndTheClipKey_when_buildingAClipUriFromThePrefix() {
        final String prefix = ObjectKeys.prefix("cdt-videos", EVENT_ID, SESSION_ID, Camera.RIGHT);

        assertThat(ObjectKeys.clipUri(prefix, 3))
                .isEqualTo("s3://cdt-videos/" + ObjectKeys.clipKey(EVENT_ID, SESSION_ID, Camera.RIGHT, 3));
    }
}

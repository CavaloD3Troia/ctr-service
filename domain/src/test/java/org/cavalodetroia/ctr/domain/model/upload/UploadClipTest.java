package org.cavalodetroia.ctr.domain.model.upload;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.cavalodetroia.ctr.domain.model.upload.ClipConfirmations.UPLOADED_AT;
import static org.cavalodetroia.ctr.domain.model.upload.ClipConfirmations.confirmation;

/**
 * AC-1, AC-10: a clip has a confirmation and an upload time exactly when it is uploaded.
 */
class UploadClipTest {

    private static final String KEY = "events/e/sessions/s/LEFT/3.mp4";

    @Test
    void should_keepTheConfirmationAndTheTime_when_theClipIsUploaded() {
        final UploadClip uploaded = UploadClip.pending(Camera.LEFT, 3, KEY).uploaded(confirmation(), UPLOADED_AT);

        assertThat(uploaded.status()).isEqualTo(ClipStatus.UPLOADED);
        assertThat(uploaded.isUploaded()).isTrue();
        assertThat(uploaded.confirmation()).isEqualTo(confirmation());
        assertThat(uploaded.uploadedAt()).isEqualTo(UPLOADED_AT);
        assertThat(uploaded.objectKey()).isEqualTo(KEY);
    }

    @Test
    void should_haveNeitherConfirmationNorTime_when_theClipIsPending() {
        final UploadClip pending = UploadClip.pending(Camera.LEFT, 3, KEY);

        assertThat(pending.isUploaded()).isFalse();
        assertThat(pending.confirmation()).isNull();
        assertThat(pending.uploadedAt()).isNull();
    }

    @Test
    void should_reject_when_theStatusDisagreesWithTheConfirmation() {
        assertThatThrownBy(() -> new UploadClip(Camera.LEFT, 3, KEY, ClipStatus.UPLOADED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UploadClip(Camera.LEFT, 3, KEY, ClipStatus.PENDING, confirmation(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UploadClip(Camera.LEFT, 3, KEY, ClipStatus.UPLOADED, confirmation(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

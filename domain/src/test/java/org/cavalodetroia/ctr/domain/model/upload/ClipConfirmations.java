package org.cavalodetroia.ctr.domain.model.upload;

import java.time.Instant;

/**
 * Clip confirmations with sensible defaults: a 1 GiB clip of 5 minutes starting at 15 minutes, as in the ticket.
 */
public final class ClipConfirmations {

    public static final long SIZE_BYTES = 1_073_741_824L;
    public static final String ETAG = "9b2cf535f27731c974343645a3985328";
    public static final long START_MS = 900_000;
    public static final long DURATION_MS = 300_000;
    public static final Instant UPLOADED_AT = Instant.parse("2026-11-05T11:00:00Z");

    private ClipConfirmations() {
    }

    public static ClipConfirmation confirmation() {
        return new ClipConfirmation(SIZE_BYTES, ETAG, START_MS, DURATION_MS);
    }

    public static ClipConfirmation confirmation(final long startMs, final long durationMs) {
        return new ClipConfirmation(SIZE_BYTES, ETAG, startMs, durationMs);
    }

    /**
     * The session after {@code camera} confirmed every one of its clips with {@link #confirmation()}.
     */
    public static UploadSession allUploadedBy(final UploadSession session, final Camera camera) {
        UploadSession after = session;
        for (int partIndex = 0; partIndex < session.partCount(); partIndex++) {
            after = after.completeClip(camera, partIndex, confirmation(), UPLOADED_AT).after();
        }
        return after;
    }
}

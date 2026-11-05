package org.cavalodetroia.ctr.api;

import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.CameraRegistration;
import org.cavalodetroia.ctr.domain.model.upload.ClipStatus;
import org.cavalodetroia.ctr.domain.model.upload.PartStatus;
import org.cavalodetroia.ctr.domain.model.upload.RegistrationResult;
import org.cavalodetroia.ctr.domain.model.upload.UploadClip;
import org.cavalodetroia.ctr.domain.model.upload.UploadPart;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.model.venue.RequestedVenue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Registration results and sessions as the domain returns them: 40 parts unless said otherwise, an 11-a-side pitch.
 */
public final class Registrations {

    public static final UUID EVENT_ID = UUID.fromString("0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11");
    public static final UUID SESSION_ID = UUID.fromString("019a5b2e-7f3c-7000-8000-000000000001");
    public static final String BUCKET = "cdt-videos";
    public static final int PART_COUNT = 40;
    public static final Instant LEFT_UPLOADED_AT = Instant.parse("2026-11-04T18:07:02Z");
    public static final Instant RIGHT_UPLOADED_AT = Instant.parse("2026-11-04T18:08:30Z");
    public static final Instant PUBLISHED_AT = Instant.parse("2026-11-04T18:10:00Z");
    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");
    private static final RequestedVenue VENUE = new RequestedVenue("FOOTBALL_11V11", null, null);
    private static final long LEFT_SIZE = 1_073_741_824L;
    private static final long RIGHT_SIZE = 1_052_000_000L;
    private static final long FIVE_MINUTES_MS = 300_000L;

    private Registrations() {
    }

    public static RegistrationResult leftOpened() {
        return UploadSession.open(SESSION_ID, EVENT_ID, new CameraRegistration(Camera.LEFT, PART_COUNT, VENUE),
                BUCKET, NOW);
    }

    public static RegistrationResult rightJoinedLeft() {
        return leftOpened().session().join(new CameraRegistration(Camera.RIGHT, PART_COUNT, null), BUCKET, NOW);
    }

    /**
     * A single camera's session, nothing uploaded.
     */
    public static UploadSession singleOpened(final int partCount) {
        return UploadSession.open(SESSION_ID, EVENT_ID, new CameraRegistration(Camera.SINGLE, partCount, VENUE),
                BUCKET, NOW).session();
    }

    /**
     * RIGHT opened and LEFT joined, 4 parts, every clip uploaded but RIGHT 2:
     * <ul>
     *   <li>part 0 {@code COMPLETE}, window 2000 + 298000, published;</li>
     *   <li>part 1 {@code COMPLETE}, window 300000 + 300000, not published;</li>
     *   <li>part 2 {@code WAITING}: LEFT uploaded, RIGHT pending;</li>
     *   <li>part 3 {@code NO_OVERLAP}: LEFT 900000 + 300000 and RIGHT 1202000 + 300000, stored window
     *       1202000 - 2000.</li>
     * </ul>
     */
    public static UploadSession mixedRightThenLeft() {
        final UploadSession opened = UploadSession.open(SESSION_ID, EVENT_ID,
                new CameraRegistration(Camera.RIGHT, 4, VENUE), BUCKET, NOW).session();
        final UploadSession both = opened.join(new CameraRegistration(Camera.LEFT, 4, null), BUCKET, NOW).session();
        final List<UploadClip> clips = both.clips().stream()
                .map(clip -> switch (clip.camera()) {
                    case LEFT -> uploaded(clip, LEFT_SIZE, clip.partIndex() * FIVE_MINUTES_MS, LEFT_UPLOADED_AT);
                    case RIGHT -> switch (clip.partIndex()) {
                        case 0 -> uploaded(clip, RIGHT_SIZE, 2_000L, RIGHT_UPLOADED_AT);
                        case 1 -> uploaded(clip, RIGHT_SIZE, FIVE_MINUTES_MS, RIGHT_UPLOADED_AT);
                        case 3 -> uploaded(clip, RIGHT_SIZE, 1_202_000L, RIGHT_UPLOADED_AT);
                        default -> clip;
                    };
                    case SINGLE -> clip;
                })
                .toList();
        final List<UploadPart> parts = List.of(
                new UploadPart(0, PartStatus.COMPLETE, 2_000L, 298_000L, PUBLISHED_AT),
                new UploadPart(1, PartStatus.COMPLETE, FIVE_MINUTES_MS, FIVE_MINUTES_MS, null),
                UploadPart.waiting(2),
                new UploadPart(3, PartStatus.NO_OVERLAP, 1_202_000L, -2_000L, null));
        return new UploadSession(both.id(), both.eventId(), both.status(), both.venue(), both.createdAt(),
                both.updatedAt(), both.version(), both.cameras(), clips, parts);
    }

    private static UploadClip uploaded(final UploadClip clip, final long sizeBytes, final long startMs,
                                       final Instant uploadedAt) {
        return new UploadClip(clip.camera(), clip.partIndex(), clip.objectKey(), ClipStatus.UPLOADED, sizeBytes,
                startMs, FIVE_MINUTES_MS, uploadedAt);
    }
}

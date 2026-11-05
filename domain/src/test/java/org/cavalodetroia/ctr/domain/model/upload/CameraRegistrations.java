package org.cavalodetroia.ctr.domain.model.upload;

import org.cavalodetroia.ctr.domain.model.venue.RequestedVenue;
import org.cavalodetroia.ctr.domain.model.venue.VenueFormat;
import org.cavalodetroia.ctr.domain.model.venue.VenueSpec;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Registrations and sessions with sensible defaults: 3 parts, an 11-a-side pitch of 105 x 68 m.
 */
public final class CameraRegistrations {

    public static final UUID EVENT_ID = UUID.fromString("0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11");
    public static final UUID SESSION_ID = UUID.fromString("019a5b2e-7f3c-7000-8000-000000000001");
    public static final String BUCKET = "cdt-videos";
    public static final Instant OPENED_AT = Instant.parse("2026-11-05T10:00:00Z");
    public static final Instant JOINED_AT = Instant.parse("2026-11-05T10:05:00Z");
    public static final int PART_COUNT = 3;
    public static final RequestedVenue REQUESTED_VENUE = new RequestedVenue("FOOTBALL_11V11", 105.0, 68.0);
    // The venue the session stores for REQUESTED_VENUE
    public static final VenueSpec VENUE = new VenueSpec(VenueFormat.FOOTBALL_11V11, 105, 68);

    private CameraRegistrations() {
    }

    /**
     * The first camera of a session, with the venue.
     */
    public static CameraRegistration first(final Camera camera) {
        return new CameraRegistration(camera, PART_COUNT, REQUESTED_VENUE);
    }

    /**
     * A camera joining a session, without a venue.
     */
    public static CameraRegistration joining(final Camera camera) {
        return joining(camera, PART_COUNT);
    }

    public static CameraRegistration joining(final Camera camera, final int partCount) {
        return new CameraRegistration(camera, partCount, null);
    }

    /**
     * The session as {@code camera} left it after opening it.
     */
    public static UploadSession openedBy(final Camera camera) {
        return UploadSession.open(SESSION_ID, EVENT_ID, first(camera), BUCKET, OPENED_AT).session();
    }

    /**
     * The session LEFT opened and RIGHT joined, nothing uploaded.
     */
    public static UploadSession leftAndRight() {
        return openedBy(Camera.LEFT).join(joining(Camera.RIGHT), BUCKET, JOINED_AT).session();
    }

    /**
     * The session with these clips of the camera uploaded: 1 GiB, 5 minutes from the start of their part.
     */
    public static UploadSession withUploaded(final UploadSession session, final Camera camera,
                                             final int... partIndexes) {
        final Set<Integer> uploaded = IntStream.of(partIndexes).boxed().collect(Collectors.toSet());
        return withClips(session, clip -> clip.camera() == camera && uploaded.contains(clip.partIndex())
                ? new UploadClip(clip.camera(), clip.partIndex(), clip.objectKey(), ClipStatus.UPLOADED,
                        1_073_741_824L, clip.partIndex() * 300_000L, 300_000L, JOINED_AT)
                : clip);
    }

    /**
     * The session with these parts in place of the ones with the same index.
     */
    public static UploadSession withParts(final UploadSession session, final UploadPart... parts) {
        final List<UploadPart> replaced = session.parts().stream()
                .map(part -> Arrays.stream(parts)
                        .filter(replacement -> replacement.partIndex() == part.partIndex())
                        .findFirst()
                        .orElse(part))
                .toList();
        return new UploadSession(session.id(), session.eventId(), session.status(), session.venue(),
                session.createdAt(), session.updatedAt(), session.version(), session.cameras(), session.clips(),
                replaced);
    }

    private static UploadSession withClips(final UploadSession session, final Function<UploadClip, UploadClip> change) {
        return new UploadSession(session.id(), session.eventId(), session.status(), session.venue(),
                session.createdAt(), session.updatedAt(), session.version(), session.cameras(),
                session.clips().stream().map(change).toList(), session.parts());
    }
}

package org.cavalodetroia.ctr.domain.model.upload;

import org.cavalodetroia.ctr.domain.exception.CameraSetConflictException;
import org.cavalodetroia.ctr.domain.exception.PartCountMismatchException;
import org.cavalodetroia.ctr.domain.exception.VenueSpecException;
import org.cavalodetroia.ctr.domain.model.venue.VenueSpec;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The upload of one event's match: the cameras filming it, one clip per camera and part, and the parts themselves.
 * The first camera opens the session and sets the venue; a second one may join. A session ends with the cameras
 * {@code {SINGLE}} or {@code {LEFT, RIGHT}}, all with the same number of parts.
 *
 * @param id        UUIDv7
 * @param eventId   the event filmed
 * @param status    {@link SessionStatus#OPEN} while cameras register and upload
 * @param venue     the pitch, set by the first camera
 * @param createdAt when the first camera opened it
 * @param updatedAt when a camera last registered
 * @param version   how many times the session changed: 0 when opened, plus one for each camera that joins. A change is
 *                  only stored over the version it was read at, so two cameras joining at once can't both win
 * @param cameras   the registered cameras, in registration order
 * @param clips     every camera's clips
 * @param parts     one per part index, created with the first camera
 */
public record UploadSession(UUID id,
                            UUID eventId,
                            SessionStatus status,
                            VenueSpec venue,
                            Instant createdAt,
                            Instant updatedAt,
                            long version,
                            List<UploadCamera> cameras,
                            List<UploadClip> clips,
                            List<UploadPart> parts) {

    // Every camera set a session may end with; any registration must keep the cameras inside one of them
    private static final List<Set<Camera>> COMPLETE_CAMERA_SETS =
            List.of(EnumSet.of(Camera.SINGLE), EnumSet.of(Camera.LEFT, Camera.RIGHT));

    public UploadSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(venue, "venue");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        cameras = List.copyOf(cameras);
        clips = List.copyOf(clips);
        parts = List.copyOf(parts);
    }

    /**
     * The first camera of an event opens its session: the camera, a {@link ClipStatus#PENDING} clip for each of its
     * parts, and a {@link PartStatus#WAITING} part for each part index.
     *
     * @param id      the id of the new session
     * @param eventId the event filmed
     * @param first   the registration of the first camera
     * @param bucket  the bucket the clips are uploaded to
     * @param now     the time of the registration
     * @return the open session, with the first camera
     * @throws VenueSpecException when the registration has no venue, or its format is missing or unknown
     */
    public static RegistrationResult open(final UUID id,
                                          final UUID eventId,
                                          final CameraRegistration first,
                                          final String bucket,
                                          final Instant now) {
        if (!first.hasVenue()) {
            throw new VenueSpecException("venue", "is required for the first camera of a session");
        }
        final VenueSpec venue = first.venue().toSpec();
        final UploadCamera camera = registeredCamera(bucket, eventId, id, first, now);
        final UploadSession session = new UploadSession(id, eventId, SessionStatus.OPEN, venue, now, now, 0,
                List.of(camera), pendingClips(eventId, id, first), waitingParts(first.partCount()));
        return new RegistrationResult(session, camera, false);
    }

    /**
     * A later camera joins the session: the camera and a {@link ClipStatus#PENDING} clip for each of its parts. The
     * parts are already there. A venue on this registration is ignored, whatever its format: the first camera set
     * it. The session comes back one version up.
     *
     * @param next   the registration of the joining camera
     * @param bucket the bucket the clips are uploaded to
     * @param now    the time of the registration
     * @return the session with the camera added
     * @throws CameraSetConflictException when the camera is already registered, or the cameras would end up as
     *                                    neither {@code {SINGLE}} nor {@code {LEFT, RIGHT}}
     * @throws PartCountMismatchException when the camera brings a different number of parts
     */
    public RegistrationResult join(final CameraRegistration next, final String bucket, final Instant now) {
        if (status != SessionStatus.OPEN) {
            throw new IllegalStateException("Session '" + id + "' is " + status + ", cameras only join open ones");
        }
        requireFitsTheCameraSet(next.camera());
        requireSamePartCount(next.partCount());

        final UploadCamera camera = registeredCamera(bucket, eventId, id, next, now);
        final UploadSession session = new UploadSession(id, eventId, status, venue, createdAt, now, version + 1,
                append(cameras, List.of(camera)), append(clips, pendingClips(eventId, id, next)), parts);
        return new RegistrationResult(session, camera, next.hasVenue());
    }

    /**
     * @return how many clips of this camera are in the bucket
     */
    public int partsUploaded(final Camera camera) {
        return (int) clips.stream()
                .filter(clip -> clip.camera() == camera && clip.status() == ClipStatus.UPLOADED)
                .count();
    }

    private void requireFitsTheCameraSet(final Camera next) {
        final Set<Camera> registered = cameras.stream()
                .map(UploadCamera::camera)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Camera.class)));
        if (registered.contains(next)) {
            throw new CameraSetConflictException(id, "camera " + next + " is already registered");
        }
        final boolean fits = COMPLETE_CAMERA_SETS.stream()
                .anyMatch(complete -> complete.contains(next) && complete.containsAll(registered));
        if (!fits) {
            throw new CameraSetConflictException(id, "camera " + next + " cannot join the cameras " + registered
                    + "; a session has either SINGLE or LEFT and RIGHT");
        }
    }

    private void requireSamePartCount(final int partCount) {
        final int expected = cameras.getFirst().partCount();
        if (partCount != expected) {
            throw new PartCountMismatchException(id, expected, partCount);
        }
    }

    private static UploadCamera registeredCamera(final String bucket,
                                                 final UUID eventId,
                                                 final UUID sessionId,
                                                 final CameraRegistration registration,
                                                 final Instant now) {
        return new UploadCamera(registration.camera(), registration.partCount(),
                ObjectKeys.prefix(bucket, eventId, sessionId, registration.camera()), now);
    }

    private static List<UploadClip> pendingClips(final UUID eventId,
                                                 final UUID sessionId,
                                                 final CameraRegistration registration) {
        final Camera camera = registration.camera();
        return IntStream.range(0, registration.partCount())
                .mapToObj(index -> new UploadClip(camera, index, ObjectKeys.clipKey(eventId, sessionId, camera, index),
                        ClipStatus.PENDING))
                .toList();
    }

    private static List<UploadPart> waitingParts(final int partCount) {
        return IntStream.range(0, partCount)
                .mapToObj(index -> new UploadPart(index, PartStatus.WAITING))
                .toList();
    }

    private static <T> List<T> append(final List<T> existing, final List<T> added) {
        return Stream.concat(existing.stream(), added.stream()).toList();
    }
}

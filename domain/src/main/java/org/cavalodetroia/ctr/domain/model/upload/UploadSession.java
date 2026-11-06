package org.cavalodetroia.ctr.domain.model.upload;

import org.cavalodetroia.ctr.domain.exception.CameraSetConflictException;
import org.cavalodetroia.ctr.domain.exception.ClipAlreadyCompletedException;
import org.cavalodetroia.ctr.domain.exception.PartCountMismatchException;
import org.cavalodetroia.ctr.domain.exception.PartIndexOutOfRangeException;
import org.cavalodetroia.ctr.domain.exception.UnknownCameraException;
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
 * {@code {SINGLE}} or {@code {LEFT, RIGHT}}, all with the same number of parts. Each camera confirms its clips as it
 * uploads them; a part closes when every expected camera has uploaded its clip of it, and the session completes when
 * every clip is uploaded.
 *
 * @param id        UUIDv7
 * @param eventId   the event filmed
 * @param status    {@link SessionStatus#OPEN} while cameras register and upload, {@link SessionStatus#COMPLETED} once
 *                  every clip of every expected camera is uploaded
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
     * A camera confirms a clip it uploaded. The clip becomes {@link ClipStatus#UPLOADED}. When every
     * {@link #expectedCameras() expected camera} has then uploaded its clip of the part, the part closes with its
     * window: the clip's own for {@code SINGLE}, the {@link Window#overlap overlap} of the two clips for {@code LEFT}
     * and {@code RIGHT}, and {@link PartStatus#NO_OVERLAP} instead of {@link PartStatus#COMPLETE} when that overlap
     * is empty. When every clip of every expected camera is uploaded, the session becomes
     * {@link SessionStatus#COMPLETED}. Confirming an uploaded clip again with the same confirmation changes nothing.
     *
     * @param camera       the camera whose clip it is
     * @param partIndex    the part of the clip, 0-based
     * @param confirmation what the camera says about the clip
     * @param now          the time of the confirmation
     * @return the session after the confirmation and what it did to the part
     * @throws UnknownCameraException         when the camera is not registered on the session
     * @throws PartIndexOutOfRangeException   when the part index is outside {@code 0 .. partCount - 1}
     * @throws ClipAlreadyCompletedException  when the clip is already uploaded with another confirmation
     */
    public ClipCompletion completeClip(final Camera camera,
                                       final int partIndex,
                                       final ClipConfirmation confirmation,
                                       final Instant now) {
        final UploadClip clip = clip(camera, partIndex);
        if (clip.isUploaded()) {
            if (!clip.confirmation().equals(confirmation)) {
                throw new ClipAlreadyCompletedException(id, camera, partIndex);
            }
            return ClipCompletion.noop(this, camera, partIndex);
        }

        final List<UploadClip> clipsAfter = replace(clips, clip, clip.uploaded(confirmation, now));
        final PartTransition transition = transitionOf(partIndex, clipsAfter);
        final UploadPart part = part(partIndex);
        final List<UploadPart> partsAfter = replace(parts, part, part.after(transition));
        final boolean completed = registeredCameras().containsAll(expectedCameras())
                && clipsAfter.stream().allMatch(UploadClip::isUploaded);
        final UploadSession after = new UploadSession(id, eventId, completed ? SessionStatus.COMPLETED : status, venue,
                createdAt, updatedAt, version, cameras, clipsAfter, partsAfter);
        return new ClipCompletion(after, camera, partIndex, transition, completed, false);
    }

    /**
     * @return the clip of {@code camera} for the part
     * @throws UnknownCameraException       when the camera is not registered on the session
     * @throws PartIndexOutOfRangeException when the part index is outside {@code 0 .. partCount - 1}
     */
    public UploadClip clip(final Camera camera, final int partIndex) {
        if (!registeredCameras().contains(camera)) {
            throw new UnknownCameraException(id, camera);
        }
        requirePartIndexInRange(partIndex);
        return clips.stream()
                .filter(clip -> clip.camera() == camera && clip.partIndex() == partIndex)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Session '" + id + "' has no clip " + camera + " "
                        + partIndex));
    }

    /**
     * @return the camera as it registered on the session
     * @throws UnknownCameraException when the camera is not registered on the session
     */
    public UploadCamera camera(final Camera camera) {
        return cameras.stream()
                .filter(registered -> registered.camera() == camera)
                .findFirst()
                .orElseThrow(() -> new UnknownCameraException(id, camera));
    }

    /**
     * The part's message is out: the part keeps its window and gets its publication time.
     *
     * @param partIndex   the part, 0-based
     * @param publishedAt when the message was published
     * @return the session with the part published
     * @throws PartIndexOutOfRangeException when the part index is outside {@code 0 .. partCount - 1}
     * @throws IllegalStateException        when the part is not {@link PartStatus#COMPLETE}, or is published already
     */
    public UploadSession markPublished(final int partIndex, final Instant publishedAt) {
        final UploadPart part = part(partIndex);
        return new UploadSession(id, eventId, status, venue, createdAt, updatedAt, version, cameras, clips,
                replace(parts, part, part.markPublished(publishedAt)));
    }

    /**
     * @return the part with this index
     * @throws PartIndexOutOfRangeException when the part index is outside {@code 0 .. partCount - 1}
     */
    public UploadPart part(final int partIndex) {
        requirePartIndexInRange(partIndex);
        return parts.stream()
                .filter(part -> part.partIndex() == partIndex)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Session '" + id + "' has no part " + partIndex));
    }

    /**
     * @return the cameras each part needs a clip from, in {@link Camera} order: {@code SINGLE} when it is
     *         registered, otherwise {@code LEFT} and {@code RIGHT}, even while only one of them has registered
     */
    public Set<Camera> expectedCameras() {
        return registeredCameras().contains(Camera.SINGLE)
                ? EnumSet.of(Camera.SINGLE)
                : EnumSet.of(Camera.LEFT, Camera.RIGHT);
    }

    /**
     * @param partIndex the part, 0-based
     * @return {@link PartState#COMPLETE} or {@link PartState#NO_OVERLAP} once the part is done; while it waits, the
     *         first expected camera whose clip is not uploaded ({@code WAITING_LEFT} before {@code WAITING_RIGHT}),
     *         or {@link PartState#WAITING} for a single camera
     * @throws IllegalArgumentException when the session has no such part
     * @throws IllegalStateException    when the part waits although every expected clip is uploaded
     */
    public PartState partState(final int partIndex) {
        final UploadPart part = parts.stream()
                .filter(candidate -> candidate.partIndex() == partIndex)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Session '" + id + "' has no part " + partIndex));
        return switch (part.status()) {
            case COMPLETE -> PartState.COMPLETE;
            case NO_OVERLAP -> PartState.NO_OVERLAP;
            case WAITING -> waitingFor(partIndex);
        };
    }

    /**
     * @return how many parts the match is cut into
     */
    public int partCount() {
        return parts.size();
    }

    /**
     * @return how many parts have been published for extraction
     */
    public int partsPublished() {
        return (int) parts.stream().filter(UploadPart::published).count();
    }

    /**
     * @return how many clips of this camera are in the bucket
     */
    public int partsUploaded(final Camera camera) {
        return (int) clips.stream()
                .filter(clip -> clip.camera() == camera && clip.status() == ClipStatus.UPLOADED)
                .count();
    }

    private PartTransition transitionOf(final int partIndex, final List<UploadClip> clipsAfter) {
        final Set<Camera> expectedCameras = expectedCameras();
        final List<UploadClip> expected = clipsAfter.stream()
                .filter(clip -> clip.partIndex() == partIndex && expectedCameras.contains(clip.camera()))
                .toList();
        // A camera that has not registered yet has no clip at all
        if (expected.size() < expectedCameras.size() || !expected.stream().allMatch(UploadClip::isUploaded)) {
            return PartTransition.NONE;
        }
        if (expected.size() == 1) {
            return new PartTransition.Complete(expected.getFirst().confirmation().window());
        }
        final Window overlap = Window.overlap(expected.get(0).confirmation().window(),
                expected.get(1).confirmation().window());
        return overlap.isEmpty() ? new PartTransition.NoOverlap(overlap) : new PartTransition.Complete(overlap);
    }

    private void requirePartIndexInRange(final int partIndex) {
        if (partIndex < 0 || partIndex >= partCount()) {
            throw new PartIndexOutOfRangeException(id, partIndex, partCount());
        }
    }

    private PartState waitingFor(final int partIndex) {
        final Camera missing = expectedCameras().stream()
                .filter(camera -> !isUploaded(camera, partIndex))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Part " + partIndex + " of session '" + id
                        + "' is WAITING although every expected clip is uploaded"));
        return switch (missing) {
            case SINGLE -> PartState.WAITING;
            case LEFT -> PartState.WAITING_LEFT;
            case RIGHT -> PartState.WAITING_RIGHT;
        };
    }

    private boolean isUploaded(final Camera camera, final int partIndex) {
        return clips.stream().anyMatch(clip -> clip.camera() == camera
                && clip.partIndex() == partIndex
                && clip.isUploaded());
    }

    private Set<Camera> registeredCameras() {
        return cameras.stream()
                .map(UploadCamera::camera)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Camera.class)));
    }

    private void requireFitsTheCameraSet(final Camera next) {
        final Set<Camera> registered = registeredCameras();
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
        return new UploadCamera(registration.camera(), registration.partCount(), registration.contentType(),
                ObjectKeys.prefix(bucket, eventId, sessionId, registration.camera()), now);
    }

    private static List<UploadClip> pendingClips(final UUID eventId,
                                                 final UUID sessionId,
                                                 final CameraRegistration registration) {
        final Camera camera = registration.camera();
        return IntStream.range(0, registration.partCount())
                .mapToObj(index -> UploadClip.pending(camera, index,
                        ObjectKeys.clipKey(eventId, sessionId, camera, index)))
                .toList();
    }

    private static List<UploadPart> waitingParts(final int partCount) {
        return IntStream.range(0, partCount)
                .mapToObj(UploadPart::waiting)
                .toList();
    }

    private static <T> List<T> replace(final List<T> existing, final T old, final T replacement) {
        return existing.stream().map(item -> item.equals(old) ? replacement : item).toList();
    }

    private static <T> List<T> append(final List<T> existing, final List<T> added) {
        return Stream.concat(existing.stream(), added.stream()).toList();
    }
}

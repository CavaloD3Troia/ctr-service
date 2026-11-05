package org.cavalodetroia.ctr.domain.service;

import org.cavalodetroia.ctr.domain.common.Uuid7;
import org.cavalodetroia.ctr.domain.exception.EtagMismatchException;
import org.cavalodetroia.ctr.domain.exception.EventNotFoundException;
import org.cavalodetroia.ctr.domain.exception.ObjectNotFoundException;
import org.cavalodetroia.ctr.domain.exception.OpenSessionAlreadyExistsException;
import org.cavalodetroia.ctr.domain.exception.SessionNotFoundException;
import org.cavalodetroia.ctr.domain.exception.SizeMismatchException;
import org.cavalodetroia.ctr.domain.exception.UploadSessionChangedException;
import org.cavalodetroia.ctr.domain.model.storage.ObjectMetadata;
import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.CameraRegistration;
import org.cavalodetroia.ctr.domain.model.upload.ClipCompletion;
import org.cavalodetroia.ctr.domain.model.upload.ClipConfirmation;
import org.cavalodetroia.ctr.domain.model.upload.PartTransition;
import org.cavalodetroia.ctr.domain.model.upload.RegistrationResult;
import org.cavalodetroia.ctr.domain.model.upload.UploadCamera;
import org.cavalodetroia.ctr.domain.model.upload.UploadClip;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.model.upload.Window;
import org.cavalodetroia.ctr.domain.port.in.CompleteClip;
import org.cavalodetroia.ctr.domain.port.in.GetUploadSession;
import org.cavalodetroia.ctr.domain.port.in.RegisterCamera;
import org.cavalodetroia.ctr.domain.port.out.ClipStorage;
import org.cavalodetroia.ctr.domain.port.out.EventRepository;
import org.cavalodetroia.ctr.domain.port.out.UploadSessionRepository;

import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Registers cameras on upload sessions, reads them back and confirms the clips the cameras upload. Logs through
 * {@link System.Logger}, the JDK's logging facade, so the domain keeps depending on nothing but the JDK; Spring Boot
 * bridges it to the application's logs.
 */
public class UploadSessionService implements RegisterCamera, GetUploadSession, CompleteClip {

    private static final System.Logger LOGGER = System.getLogger(UploadSessionService.class.getName());
    // A session takes at most two cameras, so a registration loses at most twice: to the camera that opened the
    // session, then to one that joined it
    private static final int MAX_ATTEMPTS = 3;

    private final EventRepository events;
    private final UploadSessionRepository sessions;
    private final ClipStorage storage;
    private final Clock clock;
    private final String bucket;

    /**
     * @param storage the bucket {@code bucket}, where the clips are checked
     * @param bucket  the bucket cameras upload their clips to
     */
    public UploadSessionService(final EventRepository events,
                                final UploadSessionRepository sessions,
                                final ClipStorage storage,
                                final Clock clock,
                                final String bucket) {
        this.events = events;
        this.sessions = sessions;
        this.storage = storage;
        this.clock = clock;
        this.bucket = Objects.requireNonNull(bucket, "bucket");
    }

    @Override
    public RegistrationResult register(final UUID eventId, final CameraRegistration registration) {
        if (!events.exists(eventId)) {
            throw new EventNotFoundException(eventId);
        }
        final RegistrationResult result = openOrJoinUntilNoRace(eventId, registration);
        log(result);
        return result;
    }

    @Override
    public UploadSession get(final UUID sessionId) {
        return sessions.findById(sessionId).orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    /**
     * Everything that can be decided from the stored session (camera, part index, a clip already uploaded) is decided
     * before the bucket is asked, and the bucket is asked before anything is written, outside any transaction. The
     * domain then decides again over the session read under the repository's lock, since another confirmation may
     * have changed it in the meantime.
     */
    @Override
    public ClipCompletion complete(final UUID sessionId,
                                   final Camera camera,
                                   final int partIndex,
                                   final ClipConfirmation confirmation) {
        final UploadSession session = get(sessionId);
        final UploadClip clip = session.clip(camera, partIndex);
        // An uploaded clip needs no check of the bucket: the same confirmation is answered from what is stored, and
        // another one is a conflict
        final ClipCompletion completion = clip.isUploaded()
                ? session.completeClip(camera, partIndex, confirmation, clock.instant())
                : checkThenStore(sessionId, clip, confirmation);
        log(completion);
        return completion;
    }

    private ClipCompletion checkThenStore(final UUID sessionId,
                                          final UploadClip clip,
                                          final ClipConfirmation confirmation) {
        requireInTheBucket(clip.objectKey(), confirmation);
        final Instant now = clock.instant();
        return sessions.update(sessionId,
                stored -> stored.completeClip(clip.camera(), clip.partIndex(), confirmation, now));
    }

    private void requireInTheBucket(final String objectKey, final ClipConfirmation confirmation) {
        final ObjectMetadata stored = storage.head(objectKey)
                .orElseThrow(() -> new ObjectNotFoundException(objectKey));
        if (stored.sizeBytes() != confirmation.sizeBytes()) {
            throw new SizeMismatchException(objectKey, confirmation.sizeBytes(), stored.sizeBytes());
        }
        if (!stored.hasEtag(confirmation.etag())) {
            throw new EtagMismatchException(objectKey, confirmation.etag(), stored.etag());
        }
    }

    private static void log(final ClipCompletion completion) {
        final UUID sessionId = completion.after().id();
        final int partIndex = completion.partIndex();
        if (completion.noop()) {
            LOGGER.log(Level.INFO, () -> "operation=completeClip, message=Clip already uploaded with this "
                    + "confirmation sessionId='%s' camera='%s' partIndex=%d".formatted(sessionId,
                    completion.camera(), partIndex));
            return;
        }
        LOGGER.log(Level.INFO, () -> ("operation=completeClip, message=Clip uploaded sessionId='%s' camera='%s' "
                + "partIndex=%d part='%s'").formatted(sessionId, completion.camera(), partIndex,
                completion.partState()));
        if (completion.part() instanceof PartTransition.NoOverlap noOverlap) {
            final String windows = completion.after().expectedCameras().stream()
                    .map(camera -> camera + "=" + describe(completion.after().clip(camera, partIndex)
                            .confirmation().window()))
                    .collect(Collectors.joining(" "));
            LOGGER.log(Level.WARNING, () -> "operation=completeClip, message=Part has no overlap, nothing will be "
                    + "published for it sessionId='%s' partIndex=%d overlap=%s %s".formatted(sessionId, partIndex,
                    describe(noOverlap.window()), windows));
        }
        if (completion.sessionCompleted()) {
            LOGGER.log(Level.INFO, () -> "operation=completeClip, message=Upload session completed "
                    + "sessionId='%s'".formatted(sessionId));
        }
    }

    private static String describe(final Window window) {
        return "[startMs=%d durationMs=%d]".formatted(window.startMs(), window.durationMs());
    }

    /**
     * Another camera may write the event's session between our read and our write: it opened the session first
     * ({@link OpenSessionAlreadyExistsException}) or joined it first ({@link UploadSessionChangedException}). Then
     * nothing of ours was stored, so read the session again and register against what is there now.
     */
    private RegistrationResult openOrJoinUntilNoRace(final UUID eventId, final CameraRegistration registration) {
        for (int attempt = 1; ; attempt++) {
            try {
                return openOrJoin(eventId, registration);
            } catch (OpenSessionAlreadyExistsException | UploadSessionChangedException raced) {
                if (attempt == MAX_ATTEMPTS) {
                    throw raced;
                }
                final int failed = attempt;
                LOGGER.log(Level.INFO, () -> "operation=registerCamera, message=Concurrent registration, reading the "
                        + "session again camera='%s' attempt=%d race='%s'".formatted(registration.camera(), failed,
                        raced.getClass().getSimpleName()));
            }
        }
    }

    private RegistrationResult openOrJoin(final UUID eventId, final CameraRegistration registration) {
        final Instant now = clock.instant();
        final Optional<UploadSession> open = sessions.findOpenByEvent(eventId);
        if (open.isPresent()) {
            final RegistrationResult joined = open.get().join(registration, bucket, now);
            sessions.addCamera(joined.session(), joined.camera());
            return joined;
        }
        final RegistrationResult opened = UploadSession.open(Uuid7.generate(clock), eventId, registration, bucket, now);
        sessions.create(opened.session());
        return opened;
    }

    private static void log(final RegistrationResult result) {
        final UUID sessionId = result.session().id();
        final UploadCamera camera = result.camera();
        if (result.venueIgnored()) {
            LOGGER.log(Level.WARNING, () -> "operation=registerCamera, message=Venue ignored, the first camera set it "
                    + "sessionId='%s' camera='%s'".formatted(sessionId, camera.camera()));
        }
        LOGGER.log(Level.INFO, () -> "operation=registerCamera, message=Camera registered "
                + "sessionId='%s' camera='%s' partCount=%d".formatted(sessionId, camera.camera(), camera.partCount()));
    }
}

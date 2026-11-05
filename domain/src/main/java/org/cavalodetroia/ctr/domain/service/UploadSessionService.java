package org.cavalodetroia.ctr.domain.service;

import org.cavalodetroia.ctr.domain.common.Uuid7;
import org.cavalodetroia.ctr.domain.exception.EventNotFoundException;
import org.cavalodetroia.ctr.domain.exception.OpenSessionAlreadyExistsException;
import org.cavalodetroia.ctr.domain.exception.UploadSessionChangedException;
import org.cavalodetroia.ctr.domain.model.upload.CameraRegistration;
import org.cavalodetroia.ctr.domain.model.upload.RegistrationResult;
import org.cavalodetroia.ctr.domain.model.upload.UploadCamera;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.port.in.RegisterCamera;
import org.cavalodetroia.ctr.domain.port.out.EventRepository;
import org.cavalodetroia.ctr.domain.port.out.UploadSessionRepository;

import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Registers cameras on upload sessions. Logs through {@link System.Logger}, the JDK's logging facade, so the domain
 * keeps depending on nothing but the JDK; Spring Boot bridges it to the application's logs.
 */
public class UploadSessionService implements RegisterCamera {

    private static final System.Logger LOGGER = System.getLogger(UploadSessionService.class.getName());
    // A session takes at most two cameras, so a registration loses at most twice: to the camera that opened the
    // session, then to one that joined it
    private static final int MAX_ATTEMPTS = 3;

    private final EventRepository events;
    private final UploadSessionRepository sessions;
    private final Clock clock;
    private final String bucket;

    /**
     * @param bucket the bucket cameras upload their clips to
     */
    public UploadSessionService(final EventRepository events,
                                final UploadSessionRepository sessions,
                                final Clock clock,
                                final String bucket) {
        this.events = events;
        this.sessions = sessions;
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

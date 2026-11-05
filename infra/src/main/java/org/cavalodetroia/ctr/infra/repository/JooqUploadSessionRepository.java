package org.cavalodetroia.ctr.infra.repository;

import org.cavalodetroia.ctr.domain.exception.OpenSessionAlreadyExistsException;
import org.cavalodetroia.ctr.domain.exception.SessionNotFoundException;
import org.cavalodetroia.ctr.domain.exception.UploadSessionChangedException;
import org.cavalodetroia.ctr.domain.model.upload.ClipCompletion;
import org.cavalodetroia.ctr.domain.model.upload.PartTransition;
import org.cavalodetroia.ctr.domain.model.upload.SessionStatus;
import org.cavalodetroia.ctr.domain.model.upload.UploadCamera;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.port.out.UploadSessionRepository;
import org.cavalodetroia.ctr.infra.converter.UploadSessionRecordConverter;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.UploadCameraRecord;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.UploadClipRecord;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.UploadPartRecord;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.UploadSessionRecord;
import org.jooq.DSLContext;
import org.jooq.TableRecord;
import org.slf4j.MDC;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.cavalodetroia.ctr.infra.persistence.jooq.Indexes.UPLOAD_SESSION_ONE_OPEN_PER_EVENT;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_CAMERA;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_CLIP;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_PART;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_SESSION;

/**
 * Each write runs in one transaction, so a registration lands whole or not at all. When a concurrent registration
 * won, the write fails with a domain exception: a second open session for the event violates
 * {@code upload_session_one_open_per_event}, and a camera joining a session that changed since it was read finds the
 * {@code version} moved on. A clip confirmation locks the session row ({@code SELECT ... FOR UPDATE}) for its whole
 * transaction, so confirmations of one session run one after the other, each over what the previous one stored.
 * Puts {@code session_id} in the MDC as soon as it is known.
 */
@Repository
public class JooqUploadSessionRepository implements UploadSessionRepository {

    private static final String MDC_SESSION_ID = "session_id";
    private static final String UNIQUE_VIOLATION = "23505";

    private final DSLContext dsl;
    private final UploadSessionRecordConverter converter;

    public JooqUploadSessionRepository(final DSLContext dsl, final UploadSessionRecordConverter converter) {
        this.dsl = dsl;
        this.converter = converter;
    }

    @Override
    public Optional<UploadSession> findOpenByEvent(final UUID eventId) {
        return dsl.selectFrom(UPLOAD_SESSION)
                .where(UPLOAD_SESSION.EVENT_ID.eq(eventId))
                .and(UPLOAD_SESSION.STATUS.eq(SessionStatus.OPEN.name()))
                .fetchOptional()
                .map(session -> withCamerasClipsAndParts(dsl, session));
    }

    @Override
    public Optional<UploadSession> findById(final UUID sessionId) {
        MDC.put(MDC_SESSION_ID, sessionId.toString());
        return dsl.selectFrom(UPLOAD_SESSION)
                .where(UPLOAD_SESSION.ID.eq(sessionId))
                .fetchOptional()
                .map(session -> withCamerasClipsAndParts(dsl, session));
    }

    @Override
    public ClipCompletion update(final UUID sessionId, final Function<UploadSession, ClipCompletion> completion) {
        MDC.put(MDC_SESSION_ID, sessionId.toString());
        return dsl.transactionResult(transaction -> {
            final DSLContext db = transaction.dsl();
            // A second confirmation of the session waits here until this transaction ends, then reads what it stored
            final UploadSessionRecord locked = db.selectFrom(UPLOAD_SESSION)
                    .where(UPLOAD_SESSION.ID.eq(sessionId))
                    .forUpdate()
                    .fetchOptional()
                    .orElseThrow(() -> new SessionNotFoundException(sessionId));
            final ClipCompletion result = completion.apply(withCamerasClipsAndParts(db, locked));
            if (!result.noop()) {
                store(db, result);
            }
            return result;
        });
    }

    @Override
    public void create(final UploadSession session) {
        MDC.put(MDC_SESSION_ID, session.id().toString());
        try {
            dsl.transaction(transaction -> {
                final DSLContext db = transaction.dsl();
                db.executeInsert(converter.toRecord(session));
                for (final UploadCamera camera : session.cameras()) {
                    db.executeInsert(converter.toRecord(session.id(), camera));
                }
                db.batchInsert(clipRecords(session, session.cameras())).execute();
                db.batchInsert(session.parts().stream().map(part -> converter.toRecord(session.id(), part)).toList())
                        .execute();
            });
        } catch (RuntimeException e) {
            if (isUniqueViolationOf(e, UPLOAD_SESSION_ONE_OPEN_PER_EVENT.getName())) {
                throw new OpenSessionAlreadyExistsException(session.eventId(), e);
            }
            throw e;
        }
    }

    @Override
    public void addCamera(final UploadSession session, final UploadCamera camera) {
        MDC.put(MDC_SESSION_ID, session.id().toString());
        dsl.transaction(transaction -> {
            final DSLContext db = transaction.dsl();
            // Postgres locks the row, so a concurrent join waits for this transaction, then finds the version
            // moved on and updates nothing
            final int updated = db.update(UPLOAD_SESSION)
                    .set(UPLOAD_SESSION.UPDATED_AT, converter.toRecord(session).getUpdatedAt())
                    .set(UPLOAD_SESSION.VERSION, session.version())
                    .where(UPLOAD_SESSION.ID.eq(session.id()))
                    .and(UPLOAD_SESSION.STATUS.eq(SessionStatus.OPEN.name()))
                    .and(UPLOAD_SESSION.VERSION.eq(session.version() - 1))
                    .execute();
            if (updated == 0) {
                throw new UploadSessionChangedException(session.id());
            }
            db.executeInsert(converter.toRecord(session.id(), camera));
            db.batchInsert(clipRecords(session, List.of(camera))).execute();
        });
    }

    /**
     * Writes only what the confirmation changed: the clip, the part when it closed, the session when it completed.
     */
    private void store(final DSLContext db, final ClipCompletion completion) {
        final UUID sessionId = completion.after().id();
        final UploadClipRecord clip = converter.toRecord(sessionId, completion.clip());
        db.update(UPLOAD_CLIP)
                .set(UPLOAD_CLIP.STATUS, clip.getStatus())
                .set(UPLOAD_CLIP.SIZE_BYTES, clip.getSizeBytes())
                .set(UPLOAD_CLIP.ETAG, clip.getEtag())
                .set(UPLOAD_CLIP.START_MS, clip.getStartMs())
                .set(UPLOAD_CLIP.DURATION_MS, clip.getDurationMs())
                .set(UPLOAD_CLIP.UPLOADED_AT, clip.getUploadedAt())
                .where(UPLOAD_CLIP.SESSION_ID.eq(sessionId))
                .and(UPLOAD_CLIP.CAMERA.eq(clip.getCamera()))
                .and(UPLOAD_CLIP.PART_INDEX.eq(clip.getPartIndex()))
                .execute();
        if (completion.part() != PartTransition.NONE) {
            final UploadPartRecord part = converter.toRecord(sessionId,
                    completion.after().part(completion.partIndex()));
            db.update(UPLOAD_PART)
                    .set(UPLOAD_PART.STATUS, part.getStatus())
                    .set(UPLOAD_PART.START_MS, part.getStartMs())
                    .set(UPLOAD_PART.DURATION_MS, part.getDurationMs())
                    .where(UPLOAD_PART.SESSION_ID.eq(sessionId))
                    .and(UPLOAD_PART.PART_INDEX.eq(part.getPartIndex()))
                    .execute();
        }
        if (completion.sessionCompleted()) {
            db.update(UPLOAD_SESSION)
                    .set(UPLOAD_SESSION.STATUS, completion.after().status().name())
                    .where(UPLOAD_SESSION.ID.eq(sessionId))
                    .execute();
        }
    }

    /**
     * One query per table, whatever the part count. Clips are read before parts: a clip and the part it completes
     * are written in one transaction, so parts read after the clips are never older than them.
     */
    private UploadSession withCamerasClipsAndParts(final DSLContext db, final UploadSessionRecord session) {
        final UUID sessionId = session.getId();
        MDC.put(MDC_SESSION_ID, sessionId.toString());
        final List<UploadCameraRecord> cameras = cameras(db, sessionId);
        final List<UploadClipRecord> clips = clips(db, sessionId);
        final List<UploadPartRecord> parts = parts(db, sessionId);
        return converter.toDomain(session, cameras, clips, parts);
    }

    private static List<UploadCameraRecord> cameras(final DSLContext db, final UUID sessionId) {
        return db.selectFrom(UPLOAD_CAMERA)
                .where(UPLOAD_CAMERA.SESSION_ID.eq(sessionId))
                .orderBy(UPLOAD_CAMERA.CREATED_AT, UPLOAD_CAMERA.CAMERA)
                .fetch();
    }

    private static List<UploadClipRecord> clips(final DSLContext db, final UUID sessionId) {
        return db.selectFrom(UPLOAD_CLIP)
                .where(UPLOAD_CLIP.SESSION_ID.eq(sessionId))
                .orderBy(UPLOAD_CLIP.CAMERA, UPLOAD_CLIP.PART_INDEX)
                .fetch();
    }

    private static List<UploadPartRecord> parts(final DSLContext db, final UUID sessionId) {
        return db.selectFrom(UPLOAD_PART)
                .where(UPLOAD_PART.SESSION_ID.eq(sessionId))
                .orderBy(UPLOAD_PART.PART_INDEX)
                .fetch();
    }

    private List<TableRecord<?>> clipRecords(final UploadSession session, final List<UploadCamera> cameras) {
        return session.clips().stream()
                .filter(clip -> cameras.stream().anyMatch(camera -> camera.camera() == clip.camera()))
                .<TableRecord<?>>map(clip -> converter.toRecord(session.id(), clip))
                .toList();
    }

    /**
     * Whether a unique index or key rejected the write. The driver's SQLException sits somewhere in the chain,
     * wrapped by jOOQ or by Spring's translation.
     */
    private static boolean isUniqueViolationOf(final Throwable failure, final String constraint) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                    && UNIQUE_VIOLATION.equals(sql.getSQLState())
                    && sql.getMessage() != null
                    && sql.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }
}

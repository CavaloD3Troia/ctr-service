package org.cavalodetroia.ctr.infra.repository;

import org.cavalodetroia.ctr.domain.exception.OpenSessionAlreadyExistsException;
import org.cavalodetroia.ctr.domain.exception.UploadSessionChangedException;
import org.cavalodetroia.ctr.domain.model.upload.SessionStatus;
import org.cavalodetroia.ctr.domain.model.upload.UploadCamera;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.port.out.UploadSessionRepository;
import org.cavalodetroia.ctr.infra.converter.UploadSessionRecordConverter;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.UploadCameraRecord;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.UploadClipRecord;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.UploadPartRecord;
import org.jooq.DSLContext;
import org.jooq.TableRecord;
import org.slf4j.MDC;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.cavalodetroia.ctr.infra.persistence.jooq.Indexes.UPLOAD_SESSION_ONE_OPEN_PER_EVENT;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_CAMERA;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_CLIP;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_PART;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_SESSION;

/**
 * Each write runs in one transaction, so a registration lands whole or not at all. When a concurrent registration
 * won, the write fails with a domain exception: a second open session for the event violates
 * {@code upload_session_one_open_per_event}, and a camera joining a session that changed since it was read finds the
 * {@code version} moved on. Puts {@code session_id} in the MDC as soon as it is known.
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
                .map(session -> {
                    MDC.put(MDC_SESSION_ID, session.getId().toString());
                    return converter.toDomain(session, cameras(session.getId()), clips(session.getId()),
                            parts(session.getId()));
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

    private List<UploadCameraRecord> cameras(final UUID sessionId) {
        return dsl.selectFrom(UPLOAD_CAMERA)
                .where(UPLOAD_CAMERA.SESSION_ID.eq(sessionId))
                .orderBy(UPLOAD_CAMERA.CREATED_AT, UPLOAD_CAMERA.CAMERA)
                .fetch();
    }

    private List<UploadClipRecord> clips(final UUID sessionId) {
        return dsl.selectFrom(UPLOAD_CLIP)
                .where(UPLOAD_CLIP.SESSION_ID.eq(sessionId))
                .orderBy(UPLOAD_CLIP.CAMERA, UPLOAD_CLIP.PART_INDEX)
                .fetch();
    }

    private List<UploadPartRecord> parts(final UUID sessionId) {
        return dsl.selectFrom(UPLOAD_PART)
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

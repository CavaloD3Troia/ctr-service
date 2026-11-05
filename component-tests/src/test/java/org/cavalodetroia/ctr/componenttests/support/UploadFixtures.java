package org.cavalodetroia.ctr.componenttests.support;

import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.UploadClipRecord;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.UploadPartRecord;
import org.jooq.DSLContext;
import org.jooq.TableRecord;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.EVENT;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_CAMERA;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_CLIP;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_PART;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_SESSION;

/**
 * Upload rows written straight into Postgres, as the upload flow leaves them, so a read can be checked against
 * states no endpoint produces yet. Every session gets its own event, so scenarios don't see each other's rows.
 */
public final class UploadFixtures {

    public static final String BUCKET = "cdt-videos";
    private static final OffsetDateTime OPENED_AT = OffsetDateTime.of(2026, 11, 4, 17, 45, 0, 0, ZoneOffset.UTC);
    private static final long FIVE_MINUTES_MS = 300_000L;

    private final DSLContext dsl;

    public UploadFixtures(final DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * An open session on a FOOTBALL_11V11 pitch of 105 x 68 m: the cameras, a PENDING clip per camera and part, and
     * a WAITING part per index.
     */
    public void openSession(final UUID eventId, final UUID sessionId, final List<String> cameras,
                            final int partCount) {
        dsl.insertInto(EVENT).set(EVENT.ID, eventId).set(EVENT.NAME, "Benfica vs Porto").execute();
        dsl.insertInto(UPLOAD_SESSION)
                .set(UPLOAD_SESSION.ID, sessionId)
                .set(UPLOAD_SESSION.EVENT_ID, eventId)
                .set(UPLOAD_SESSION.STATUS, "OPEN")
                .set(UPLOAD_SESSION.VENUE_FORMAT, "FOOTBALL_11V11")
                .set(UPLOAD_SESSION.VENUE_LENGTH_M, 105f)
                .set(UPLOAD_SESSION.VENUE_WIDTH_M, 68f)
                .set(UPLOAD_SESSION.CREATED_AT, OPENED_AT)
                .set(UPLOAD_SESSION.UPDATED_AT, OPENED_AT)
                .set(UPLOAD_SESSION.VERSION, cameras.size() - 1L)
                .execute();
        final List<TableRecord<?>> rows = new ArrayList<>();
        for (final String camera : cameras) {
            dsl.insertInto(UPLOAD_CAMERA)
                    .set(UPLOAD_CAMERA.SESSION_ID, sessionId)
                    .set(UPLOAD_CAMERA.CAMERA, camera)
                    .set(UPLOAD_CAMERA.PART_COUNT, partCount)
                    .set(UPLOAD_CAMERA.PREFIX, prefix(eventId, sessionId, camera))
                    .set(UPLOAD_CAMERA.CREATED_AT, OPENED_AT)
                    .execute();
            IntStream.range(0, partCount).forEach(index -> {
                final UploadClipRecord clip = new UploadClipRecord();
                clip.setSessionId(sessionId);
                clip.setCamera(camera);
                clip.setPartIndex(index);
                clip.setObjectKey(folder(eventId, sessionId, camera) + index + ".mp4");
                clip.setStatus("PENDING");
                rows.add(clip);
            });
        }
        IntStream.range(0, partCount).forEach(index -> {
            final UploadPartRecord part = new UploadPartRecord();
            part.setSessionId(sessionId);
            part.setPartIndex(index);
            part.setStatus("WAITING");
            rows.add(part);
        });
        dsl.batchInsert(rows).execute();
    }

    public void uploadClip(final UUID sessionId, final String camera, final int partIndex, final long sizeBytes,
                           final long startMs, final long durationMs, final Instant uploadedAt) {
        dsl.update(UPLOAD_CLIP)
                .set(UPLOAD_CLIP.STATUS, "UPLOADED")
                .set(UPLOAD_CLIP.SIZE_BYTES, sizeBytes)
                .set(UPLOAD_CLIP.ETAG, "\"etag-" + camera + "-" + partIndex + "\"")
                .set(UPLOAD_CLIP.START_MS, startMs)
                .set(UPLOAD_CLIP.DURATION_MS, durationMs)
                .set(UPLOAD_CLIP.UPLOADED_AT, uploadedAt.atOffset(ZoneOffset.UTC))
                .where(UPLOAD_CLIP.SESSION_ID.eq(sessionId))
                .and(UPLOAD_CLIP.CAMERA.eq(camera))
                .and(UPLOAD_CLIP.PART_INDEX.eq(partIndex))
                .execute();
    }

    /**
     * Every clip of the camera uploaded: 1 GiB, 5 minutes from the start of its part.
     */
    public void uploadEveryClip(final UUID sessionId, final String camera) {
        dsl.update(UPLOAD_CLIP)
                .set(UPLOAD_CLIP.STATUS, "UPLOADED")
                .set(UPLOAD_CLIP.SIZE_BYTES, 1_073_741_824L)
                .set(UPLOAD_CLIP.ETAG, "\"etag\"")
                .set(UPLOAD_CLIP.START_MS, UPLOAD_CLIP.PART_INDEX.cast(Long.class).mul(FIVE_MINUTES_MS))
                .set(UPLOAD_CLIP.DURATION_MS, FIVE_MINUTES_MS)
                .set(UPLOAD_CLIP.UPLOADED_AT, OPENED_AT.plusMinutes(5))
                .where(UPLOAD_CLIP.SESSION_ID.eq(sessionId))
                .and(UPLOAD_CLIP.CAMERA.eq(camera))
                .execute();
    }

    /**
     * @param publishedAt when the part was published, or {@code null} while it is not
     */
    public void finishPart(final UUID sessionId, final int partIndex, final String status, final long startMs,
                           final long durationMs, final Instant publishedAt) {
        dsl.update(UPLOAD_PART)
                .set(UPLOAD_PART.STATUS, status)
                .set(UPLOAD_PART.START_MS, startMs)
                .set(UPLOAD_PART.DURATION_MS, durationMs)
                .set(UPLOAD_PART.PUBLISHED_AT, publishedAt == null ? null : publishedAt.atOffset(ZoneOffset.UTC))
                .where(UPLOAD_PART.SESSION_ID.eq(sessionId))
                .and(UPLOAD_PART.PART_INDEX.eq(partIndex))
                .execute();
    }

    public static String prefix(final UUID eventId, final UUID sessionId, final String camera) {
        return "s3://" + BUCKET + "/" + folder(eventId, sessionId, camera);
    }

    private static String folder(final UUID eventId, final UUID sessionId, final String camera) {
        return "events/" + eventId + "/sessions/" + sessionId + "/" + camera + "/";
    }
}

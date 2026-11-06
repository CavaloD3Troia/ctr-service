package org.cavalodetroia.ctr.infra.converter;

import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.ClipConfirmation;
import org.cavalodetroia.ctr.domain.model.upload.ClipStatus;
import org.cavalodetroia.ctr.domain.model.upload.PartStatus;
import org.cavalodetroia.ctr.domain.model.upload.SessionStatus;
import org.cavalodetroia.ctr.domain.model.upload.UploadCamera;
import org.cavalodetroia.ctr.domain.model.upload.UploadClip;
import org.cavalodetroia.ctr.domain.model.upload.UploadPart;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.model.upload.Window;
import org.cavalodetroia.ctr.domain.model.venue.VenueFormat;
import org.cavalodetroia.ctr.domain.model.venue.VenueSpec;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.UploadCameraRecord;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.UploadClipRecord;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.UploadPartRecord;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.UploadSessionRecord;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * An upload session and the rows of {@code upload_session}, {@code upload_camera}, {@code upload_clip} and
 * {@code upload_part}. The venue measures are {@code REAL} columns, so they keep float precision. A pending clip and
 * a waiting part leave their nullable columns empty.
 */
@Component
public class UploadSessionRecordConverter {

    public UploadSessionRecord toRecord(final UploadSession session) {
        final UploadSessionRecord record = new UploadSessionRecord();
        record.setId(session.id());
        record.setEventId(session.eventId());
        record.setStatus(session.status().name());
        record.setVenueFormat(session.venue().format().name());
        record.setVenueLengthM((float) session.venue().lengthM());
        record.setVenueWidthM((float) session.venue().widthM());
        record.setCreatedAt(toOffsetDateTime(session.createdAt()));
        record.setUpdatedAt(toOffsetDateTime(session.updatedAt()));
        record.setVersion(session.version());
        return record;
    }

    public UploadCameraRecord toRecord(final UUID sessionId, final UploadCamera camera) {
        final UploadCameraRecord record = new UploadCameraRecord();
        record.setSessionId(sessionId);
        record.setCamera(camera.camera().name());
        record.setPartCount(camera.partCount());
        record.setContentType(camera.contentType());
        record.setPrefix(camera.prefix());
        record.setCreatedAt(toOffsetDateTime(camera.createdAt()));
        return record;
    }

    public UploadClipRecord toRecord(final UUID sessionId, final UploadClip clip) {
        final UploadClipRecord record = new UploadClipRecord();
        record.setSessionId(sessionId);
        record.setCamera(clip.camera().name());
        record.setPartIndex(clip.partIndex());
        record.setObjectKey(clip.objectKey());
        record.setStatus(clip.status().name());
        final ClipConfirmation confirmation = clip.confirmation();
        if (confirmation != null) {
            record.setSizeBytes(confirmation.sizeBytes());
            record.setEtag(confirmation.etag());
            record.setStartMs(confirmation.startMs());
            record.setDurationMs(confirmation.durationMs());
            record.setUploadedAt(toOffsetDateTime(clip.uploadedAt()));
        }
        return record;
    }

    public UploadPartRecord toRecord(final UUID sessionId, final UploadPart part) {
        final UploadPartRecord record = new UploadPartRecord();
        record.setSessionId(sessionId);
        record.setPartIndex(part.partIndex());
        record.setStatus(part.status().name());
        if (part.window() != null) {
            record.setStartMs(part.window().startMs());
            record.setDurationMs(part.window().durationMs());
        }
        if (part.publishedAt() != null) {
            record.setPublishedAt(toOffsetDateTime(part.publishedAt()));
        }
        return record;
    }

    public UploadSession toDomain(final UploadSessionRecord session,
                                  final List<UploadCameraRecord> cameras,
                                  final List<UploadClipRecord> clips,
                                  final List<UploadPartRecord> parts) {
        final VenueSpec venue = new VenueSpec(VenueFormat.valueOf(session.getVenueFormat()),
                session.getVenueLengthM(), session.getVenueWidthM());
        return new UploadSession(
                session.getId(),
                session.getEventId(),
                SessionStatus.valueOf(session.getStatus()),
                venue,
                session.getCreatedAt().toInstant(),
                session.getUpdatedAt().toInstant(),
                session.getVersion(),
                cameras.stream().map(UploadSessionRecordConverter::toDomain).toList(),
                clips.stream().map(UploadSessionRecordConverter::toDomain).toList(),
                parts.stream().map(UploadSessionRecordConverter::toDomain).toList());
    }

    private static UploadCamera toDomain(final UploadCameraRecord camera) {
        return new UploadCamera(Camera.valueOf(camera.getCamera()), camera.getPartCount(), camera.getContentType(),
                camera.getPrefix(), camera.getCreatedAt().toInstant());
    }

    private static UploadClip toDomain(final UploadClipRecord clip) {
        final ClipStatus status = ClipStatus.valueOf(clip.getStatus());
        final ClipConfirmation confirmation = status == ClipStatus.UPLOADED
                ? new ClipConfirmation(clip.getSizeBytes(), clip.getEtag(), clip.getStartMs(), clip.getDurationMs())
                : null;
        return new UploadClip(Camera.valueOf(clip.getCamera()), clip.getPartIndex(), clip.getObjectKey(), status,
                confirmation, toInstant(clip.getUploadedAt()));
    }

    private static UploadPart toDomain(final UploadPartRecord part) {
        final Window window = part.getStartMs() == null
                ? null
                : new Window(part.getStartMs(), part.getDurationMs());
        return new UploadPart(part.getPartIndex(), PartStatus.valueOf(part.getStatus()), window,
                toInstant(part.getPublishedAt()));
    }

    private static Instant toInstant(final OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }

    private static OffsetDateTime toOffsetDateTime(final Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}

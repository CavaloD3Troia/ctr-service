package org.cavalodetroia.ctr.infra.converter;

import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.CameraRegistration;
import org.cavalodetroia.ctr.domain.model.upload.ClipStatus;
import org.cavalodetroia.ctr.domain.model.upload.PartStatus;
import org.cavalodetroia.ctr.domain.model.upload.UploadClip;
import org.cavalodetroia.ctr.domain.model.upload.UploadPart;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.model.venue.RequestedVenue;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.UploadSessionRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-3, AC-4: the rows a session is stored as, and back.
 */
class UploadSessionRecordConverterTest {

    private static final UUID EVENT_ID = UUID.fromString("0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11");
    private static final UUID SESSION_ID = UUID.fromString("019a5b2e-7f3c-7000-8000-000000000001");
    private static final Instant OPENED_AT = Instant.parse("2026-11-05T10:00:00Z");
    private static final Instant JOINED_AT = Instant.parse("2026-11-05T10:05:00.123456Z");

    private final UploadSessionRecordConverter converter = new UploadSessionRecordConverter();

    @Test
    void should_storeTheVenueFormatAndMeasures_when_convertingASession() {
        final UploadSession session = leftThenRight(new RequestedVenue("FOOTBALL_7V7", 60.5, 40.25));

        final UploadSessionRecord record = converter.toRecord(session);

        assertThat(record.getId()).isEqualTo(SESSION_ID);
        assertThat(record.getEventId()).isEqualTo(EVENT_ID);
        assertThat(record.getStatus()).isEqualTo("OPEN");
        assertThat(record.getVenueFormat()).isEqualTo("FOOTBALL_7V7");
        assertThat(record.getVenueLengthM()).isEqualTo(60.5f);
        assertThat(record.getVenueWidthM()).isEqualTo(40.25f);
        assertThat(record.getCreatedAt()).isEqualTo(OffsetDateTime.of(2026, 11, 5, 10, 0, 0, 0, ZoneOffset.UTC));
        assertThat(record.getVersion()).isEqualTo(1L);
    }

    @Test
    void should_storeTheClipKeyAndStatus_when_convertingAClip() {
        final UploadClip clip = UploadClip.pending(Camera.LEFT, 39, "events/e/sessions/s/LEFT/39.mp4");

        final var record = converter.toRecord(SESSION_ID, clip);

        assertThat(record.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(record.getCamera()).isEqualTo("LEFT");
        assertThat(record.getPartIndex()).isEqualTo(39);
        assertThat(record.getObjectKey()).isEqualTo("events/e/sessions/s/LEFT/39.mp4");
        assertThat(record.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void should_giveBackTheSameSession_when_roundTrippingThroughTheRecords() {
        final UploadSession session = leftThenRight(new RequestedVenue("FOOTBALL_11V11", null, null));

        final UploadSession back = converter.toDomain(
                converter.toRecord(session),
                session.cameras().stream().map(camera -> converter.toRecord(SESSION_ID, camera)).toList(),
                session.clips().stream().map(clip -> converter.toRecord(SESSION_ID, clip)).toList(),
                session.parts().stream().map(part -> converter.toRecord(SESSION_ID, part)).toList());

        assertThat(back).isEqualTo(session);
    }

    // AC-2 (TH-143): what a camera reported about its clip, and a part's window and publication, come back as stored
    @Test
    void should_giveBackTheSameClipsAndParts_when_roundTrippingUploadedClipsAndFinishedParts() {
        final UploadClip uploaded = new UploadClip(Camera.RIGHT, 1, "events/e/sessions/s/RIGHT/1.mp4",
                ClipStatus.UPLOADED, 1_052_000_000L, 598_000L, 300_000L, Instant.parse("2026-11-04T18:03:40Z"));
        final UploadPart published = new UploadPart(1, PartStatus.COMPLETE, 600_000L, 298_000L,
                Instant.parse("2026-11-04T18:10:00Z"));
        final UploadPart noOverlap = new UploadPart(2, PartStatus.NO_OVERLAP, 1_202_000L, -2_000L, null);
        final UploadSession left = leftThenRight(new RequestedVenue("FOOTBALL_11V11", null, null));
        final UploadSession session = new UploadSession(SESSION_ID, EVENT_ID, left.status(), left.venue(),
                left.createdAt(), left.updatedAt(), left.version(), left.cameras(),
                List.of(uploaded, UploadClip.pending(Camera.LEFT, 1, "events/e/sessions/s/LEFT/1.mp4")),
                List.of(UploadPart.waiting(0), published, noOverlap));

        final UploadSession back = converter.toDomain(
                converter.toRecord(session),
                session.cameras().stream().map(camera -> converter.toRecord(SESSION_ID, camera)).toList(),
                session.clips().stream().map(clip -> converter.toRecord(SESSION_ID, clip)).toList(),
                session.parts().stream().map(part -> converter.toRecord(SESSION_ID, part)).toList());

        assertThat(back).isEqualTo(session);
        assertThat(converter.toRecord(SESSION_ID, uploaded).getUploadedAt())
                .isEqualTo(OffsetDateTime.of(2026, 11, 4, 18, 3, 40, 0, ZoneOffset.UTC));
        assertThat(converter.toRecord(SESSION_ID, published).getPublishedAt())
                .isEqualTo(OffsetDateTime.of(2026, 11, 4, 18, 10, 0, 0, ZoneOffset.UTC));
    }

    private static UploadSession leftThenRight(final RequestedVenue venue) {
        final UploadSession left = UploadSession.open(SESSION_ID, EVENT_ID,
                new CameraRegistration(Camera.LEFT, 2, venue), "cdt-videos", OPENED_AT).session();
        return left.join(new CameraRegistration(Camera.RIGHT, 2, null), "cdt-videos", JOINED_AT).session();
    }
}

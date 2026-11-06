package org.cavalodetroia.ctr.infra.repository;

import org.cavalodetroia.ctr.domain.exception.OpenSessionAlreadyExistsException;
import org.cavalodetroia.ctr.domain.exception.SessionNotFoundException;
import org.cavalodetroia.ctr.domain.exception.UploadSessionChangedException;
import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.CameraRegistration;
import org.cavalodetroia.ctr.domain.model.upload.ClipCompletion;
import org.cavalodetroia.ctr.domain.model.upload.ClipConfirmation;
import org.cavalodetroia.ctr.domain.model.upload.RegistrationResult;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.model.venue.RequestedVenue;
import org.cavalodetroia.ctr.infra.converter.UploadSessionRecordConverter;
import org.jooq.exception.DataAccessException;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_CAMERA;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_CLIP;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_PART;
import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.UPLOAD_SESSION;

/**
 * What the repository sends to Postgres and makes of the answers, through jOOQ's mock JDBC connection: a session is
 * read in four queries, a confirmation locks its row and writes only what changed, a registration lands whole or
 * becomes the domain exception Postgres' rejection stands for, and a camera only joins over the version it read.
 * The component tests prove the same statements against a real Postgres.
 */
class JooqUploadSessionRepositoryTest {

    private static final UUID EVENT_ID = UUID.fromString("0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11");
    private static final UUID SESSION_ID = UUID.fromString("019a5b2e-7f3c-7000-8000-000000000001");
    private static final String BUCKET = "cdt-videos";
    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");
    private static final RequestedVenue VENUE = new RequestedVenue("FOOTBALL_11V11", null, null);
    private static final ClipConfirmation LEFT_CLIP =
            new ClipConfirmation(1_073_741_824L, "9b2cf535f27731c974343645a3985328", 900_000, 300_000);
    private static final ClipConfirmation RIGHT_CLIP =
            new ClipConfirmation(1_073_741_000L, "77aa0c1d6b1e4e8f9a0b1c2d3e4f5a6b", 898_000, 300_000);

    private final MockPostgres postgres = new MockPostgres(UPLOAD_SESSION, UPLOAD_CAMERA, UPLOAD_CLIP, UPLOAD_PART);
    private final UploadSessionRecordConverter converter = new UploadSessionRecordConverter();
    private final JooqUploadSessionRepository repository = new JooqUploadSessionRepository(postgres.dsl, converter);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    // Reads

    @Test
    void should_readTheSessionWithCamerasClipsAndPartsInFourQueries_when_findingById() {
        final UploadSession session = leftThenRight(2);
        stored(session);

        assertThat(repository.findById(SESSION_ID)).contains(session);

        assertThat(postgres.sql()).hasSize(4).allSatisfy(sql -> assertThat(sql).startsWithIgnoringCase("select"));
        assertThat(postgres.statements.getFirst().bindings()).containsExactly(SESSION_ID);
        assertThat(MDC.get("session_id")).isEqualTo(SESSION_ID.toString());
    }

    @Test
    void should_beEmpty_when_noSessionHasTheId() {
        assertThat(repository.findById(SESSION_ID)).isEmpty();
        assertThat(postgres.sql()).hasSize(1);
    }

    @Test
    void should_readOnlyTheOpenSession_when_findingByEvent() {
        final UploadSession session = leftThenRight(2);
        stored(session);

        assertThat(repository.findOpenByEvent(EVENT_ID)).contains(session);

        assertThat(postgres.statements.getFirst().sql()).containsIgnoringCase("from \"upload_session\"");
        assertThat(postgres.statements.getFirst().bindings()).containsExactly(EVENT_ID, "OPEN");
    }

    @Test
    void should_beEmpty_when_theEventHasNoOpenSession() {
        assertThat(repository.findOpenByEvent(EVENT_ID)).isEmpty();
    }

    // Confirmations

    @Test
    void should_lockTheSessionRowAndWriteNothing_when_theConfirmationIsRepeated() {
        final UploadSession session = leftThenRight(2).completeClip(Camera.LEFT, 0, LEFT_CLIP, NOW).after();
        stored(session);

        final ClipCompletion completion = repository.update(SESSION_ID,
                stored -> stored.completeClip(Camera.LEFT, 0, LEFT_CLIP, NOW));

        assertThat(completion.noop()).isTrue();
        assertThat(postgres.sql()).hasSize(4).allSatisfy(sql -> assertThat(sql).startsWithIgnoringCase("select"));
        assertThat(postgres.statements.getFirst().sql()).containsIgnoringCase("for update");
    }

    @Test
    void should_writeOnlyTheClip_when_thePartStillWaitsForTheOtherCamera() {
        stored(leftThenRight(2));

        repository.update(SESSION_ID, stored -> stored.completeClip(Camera.LEFT, 1, LEFT_CLIP, NOW));

        assertThat(writes()).singleElement().satisfies(statement -> {
            assertThat(statement.sql()).startsWithIgnoringCase("update \"upload_clip\"");
            assertThat(statement.bindings()).contains("UPLOADED", 1_073_741_824L, "9b2cf535f27731c974343645a3985328",
                    900_000L, 300_000L, SESSION_ID, "LEFT", 1);
        });
    }

    @Test
    void should_writeTheClipThePartAndTheSession_when_theLastClipCompletesThem() {
        stored(leftThenRight(1).completeClip(Camera.LEFT, 0, LEFT_CLIP, NOW).after());

        final ClipCompletion completion = repository.update(SESSION_ID,
                stored -> stored.completeClip(Camera.RIGHT, 0, RIGHT_CLIP, NOW));

        assertThat(completion.sessionCompleted()).isTrue();
        assertThat(writes()).hasSize(3).satisfiesExactly(
                clip -> {
                    assertThat(clip.sql()).startsWithIgnoringCase("update \"upload_clip\"");
                    assertThat(clip.bindings()).contains("UPLOADED", SESSION_ID, "RIGHT", 0);
                },
                part -> {
                    assertThat(part.sql()).startsWithIgnoringCase("update \"upload_part\"");
                    // The overlap: the later start, up to the earlier end
                    assertThat(part.bindings()).containsExactly("COMPLETE", 900_000L, 298_000L, SESSION_ID, 0);
                },
                session -> {
                    assertThat(session.sql()).startsWithIgnoringCase("update \"upload_session\"");
                    assertThat(session.bindings()).containsExactly("COMPLETED", SESSION_ID);
                });
    }

    @Test
    void should_throwSessionNotFound_when_confirmingOnAnUnknownSession() {
        assertThatThrownBy(() -> repository.update(SESSION_ID, stored -> stored.completeClip(Camera.LEFT, 0, LEFT_CLIP, NOW)))
                .isInstanceOf(SessionNotFoundException.class);
        assertThat(writes()).isEmpty();
    }

    // Publication (TH-152)

    // AC-1, AC-4: one statement outside any confirmation, and a part published already keeps its first time
    @Test
    void should_setPublishedAtOnlyWhereItIsNull_when_markingAPartPublished() {
        repository.markPublished(SESSION_ID, 3, NOW);

        assertThat(postgres.statements).singleElement().satisfies(statement -> {
            assertThat(statement.sql()).startsWithIgnoringCase("update \"upload_part\"")
                    .containsIgnoringCase("\"published_at\" is null")
                    .doesNotContainIgnoringCase("for update");
            // The mock connection gets the timestamp as Postgres text, in UTC
            assertThat(statement.bindings()).containsExactly("2026-11-05 10:00:00+00:00", SESSION_ID, 3);
        });
        assertThat(MDC.get("session_id")).isEqualTo(SESSION_ID.toString());
    }

    // Registrations

    @Test
    void should_insertTheSessionTheCameraAndTheirClipsAndParts_when_creating() {
        final UploadSession session = UploadSession.open(SESSION_ID, EVENT_ID,
                new CameraRegistration(Camera.LEFT, 2, "video/mp4", VENUE), BUCKET, NOW).session();

        repository.create(session);

        assertThat(postgres.statements).hasSize(4).satisfiesExactly(
                sessionRow -> {
                    assertThat(sessionRow.sql()).startsWithIgnoringCase("insert into \"upload_session\"");
                    assertThat(sessionRow.bindings()).contains(SESSION_ID, EVENT_ID, "OPEN", "FOOTBALL_11V11", 0L);
                },
                cameraRow -> {
                    assertThat(cameraRow.sql()).startsWithIgnoringCase("insert into \"upload_camera\"");
                    assertThat(cameraRow.bindings()).contains(SESSION_ID, "LEFT", 2, "video/mp4");
                },
                clipRows -> {
                    assertThat(clipRows.sql()).startsWithIgnoringCase("insert into \"upload_clip\"");
                    assertThat(clipRows.batchBindings()).hasNumberOfRows(2);
                },
                partRows -> {
                    assertThat(partRows.sql()).startsWithIgnoringCase("insert into \"upload_part\"");
                    assertThat(partRows.batchBindings()).hasNumberOfRows(2);
                });
    }

    @Test
    void should_throwOpenSessionAlreadyExists_when_postgresRejectsASecondOpenSession() {
        postgres.failsWith(new SQLException(
                "ERROR: duplicate key value violates unique constraint \"upload_session_one_open_per_event\"", "23505"));
        final UploadSession session = UploadSession.open(SESSION_ID, EVENT_ID,
                new CameraRegistration(Camera.LEFT, 2, "video/mp4", VENUE), BUCKET, NOW).session();

        assertThatThrownBy(() -> repository.create(session))
                .isInstanceOf(OpenSessionAlreadyExistsException.class)
                .hasCauseInstanceOf(DataAccessException.class);
    }

    @Test
    void should_letAnotherFailureThrough_when_creating() {
        postgres.failsWith(new SQLException("connection lost", "08006"));
        final UploadSession session = UploadSession.open(SESSION_ID, EVENT_ID,
                new CameraRegistration(Camera.LEFT, 2, "video/mp4", VENUE), BUCKET, NOW).session();

        assertThatThrownBy(() -> repository.create(session))
                .isInstanceOf(DataAccessException.class)
                .isNotInstanceOf(OpenSessionAlreadyExistsException.class);
    }

    @Test
    void should_storeTheCameraOverTheVersionItRead_when_addingACamera() {
        final RegistrationResult joined = UploadSession.open(SESSION_ID, EVENT_ID,
                        new CameraRegistration(Camera.LEFT, 2, "video/mp4", VENUE), BUCKET, NOW).session()
                .join(new CameraRegistration(Camera.RIGHT, 2, "video/mp4", null), BUCKET, NOW);

        repository.addCamera(joined.session(), joined.camera());

        assertThat(postgres.statements).hasSize(3).satisfiesExactly(
                guard -> {
                    assertThat(guard.sql()).startsWithIgnoringCase("update \"upload_session\"")
                            .containsIgnoringCase("\"status\" = ?")
                            .containsIgnoringCase("\"version\" = ?");
                    // The new version, then the id, the status and the version read, one below
                    assertThat(guard.bindings()).contains(1L, SESSION_ID, "OPEN", 0L);
                },
                cameraRow -> {
                    assertThat(cameraRow.sql()).startsWithIgnoringCase("insert into \"upload_camera\"");
                    assertThat(cameraRow.bindings()).contains(SESSION_ID, "RIGHT", 2);
                },
                clipRows -> {
                    assertThat(clipRows.sql()).startsWithIgnoringCase("insert into \"upload_clip\"");
                    assertThat(List.of(clipRows.batchBindings())).hasSize(2)
                            .allSatisfy(bindings -> assertThat(bindings).contains("RIGHT"));
                });
    }

    @Test
    void should_throwUploadSessionChangedAndInsertNothing_when_theSessionMovedOn() {
        postgres.affects(UPLOAD_SESSION, 0);
        final RegistrationResult joined = UploadSession.open(SESSION_ID, EVENT_ID,
                        new CameraRegistration(Camera.LEFT, 2, "video/mp4", VENUE), BUCKET, NOW).session()
                .join(new CameraRegistration(Camera.RIGHT, 2, "video/mp4", null), BUCKET, NOW);

        assertThatThrownBy(() -> repository.addCamera(joined.session(), joined.camera()))
                .isInstanceOf(UploadSessionChangedException.class);
        assertThat(postgres.sql()).singleElement().satisfies(sql -> assertThat(sql).startsWithIgnoringCase("update"));
    }

    private static UploadSession leftThenRight(final int partCount) {
        final UploadSession left = UploadSession.open(SESSION_ID, EVENT_ID,
                new CameraRegistration(Camera.LEFT, partCount, "video/mp4", VENUE), BUCKET, NOW).session();
        return left.join(new CameraRegistration(Camera.RIGHT, partCount, "video/mp4", null), BUCKET, NOW).session();
    }

    /** The session as its four tables hold it. */
    private void stored(final UploadSession session) {
        postgres.rows(UPLOAD_SESSION, List.of(converter.toRecord(session)));
        postgres.rows(UPLOAD_CAMERA, session.cameras().stream()
                .map(camera -> converter.toRecord(session.id(), camera)).toList());
        postgres.rows(UPLOAD_CLIP, session.clips().stream()
                .map(clip -> converter.toRecord(session.id(), clip)).toList());
        postgres.rows(UPLOAD_PART, session.parts().stream()
                .map(part -> converter.toRecord(session.id(), part)).toList());
    }

    private List<MockExecuteContext> writes() {
        return postgres.statements.stream()
                .filter(statement -> !statement.sql().toLowerCase(Locale.ROOT).startsWith("select"))
                .toList();
    }
}

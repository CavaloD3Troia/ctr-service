package org.cavalodetroia.ctr.api.error;

import org.cavalodetroia.ctr.api.controller.UploadSessionController;
import org.cavalodetroia.ctr.api.converter.UploadSessionConverter;
import org.cavalodetroia.ctr.api.filter.CorrelationIdFilter;
import org.cavalodetroia.ctr.api.idempotency.IdempotentRequests;
import org.cavalodetroia.ctr.domain.exception.CameraSetConflictException;
import org.cavalodetroia.ctr.domain.exception.ClipAlreadyCompletedException;
import org.cavalodetroia.ctr.domain.exception.EtagMismatchException;
import org.cavalodetroia.ctr.domain.exception.EventNotFoundException;
import org.cavalodetroia.ctr.domain.exception.ObjectNotFoundException;
import org.cavalodetroia.ctr.domain.exception.PartCountMismatchException;
import org.cavalodetroia.ctr.domain.exception.PartIndexOutOfRangeException;
import org.cavalodetroia.ctr.domain.exception.SessionNotFoundException;
import org.cavalodetroia.ctr.domain.exception.SizeMismatchException;
import org.cavalodetroia.ctr.domain.exception.StorageUnavailableException;
import org.cavalodetroia.ctr.domain.exception.UnknownCameraException;
import org.cavalodetroia.ctr.domain.exception.VenueSpecException;
import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.ClipCompletion;
import org.cavalodetroia.ctr.domain.port.in.CompleteClip;
import org.cavalodetroia.ctr.domain.port.in.RegisterCamera;
import org.cavalodetroia.ctr.domain.port.out.IdempotencyStore;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cavalodetroia.ctr.api.Registrations.EVENT_ID;
import static org.cavalodetroia.ctr.api.Registrations.SESSION_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Each domain exception becomes problem+json with its status, {@code code} and {@code type}; a 500 shows only the
 * correlation id (TH-137 AC-5, AC-6). TH-142: AC-4 (422 OBJECT_NOT_FOUND, SIZE_MISMATCH, ETAG_MISMATCH), AC-5 (503
 * STORAGE_UNAVAILABLE with Retry-After), AC-6 (404 SESSION_NOT_FOUND, 400 naming camera or partIndex, 409
 * CLIP_ALREADY_COMPLETED).
 */
@WebMvcTest(UploadSessionController.class)
@Import({UploadSessionConverter.class, IdempotentRequests.class})
class ApiExceptionHandlerTest {

    private static final String PATH = "/api/v1/events/{eventId}/upload-session/cameras";
    private static final String BODY = """
            {"camera": "RIGHT", "partCount": 40, "contentType": "video/mp4"}""";
    private static final String COMPLETE_PATH =
            "/api/v1/upload-sessions/{sessionId}/cameras/{camera}/parts/{partIndex}/complete";
    private static final String COMPLETE_BODY = """
            {"sizeBytes": 1073741824, "etag": "9b2cf5", "startMs": 900000, "durationMs": 300000}""";
    private static final String KEY = "events/e/sessions/s/LEFT/3.mp4";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private RegisterCamera registerCamera;

    @MockitoBean
    private CompleteClip completeClip;

    @MockitoBean
    private IdempotencyStore idempotencyStore;

    @Test
    void should_return404EventNotFound_when_theEventIsUnknown() {
        when(registerCamera.register(eq(EVENT_ID), any())).thenThrow(new EventNotFoundException(EVENT_ID));

        assertProblem(post(), HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "event-not-found");
    }

    @Test
    void should_return409CameraSetConflict_when_theCameraDoesNotFitTheSession() {
        when(registerCamera.register(eq(EVENT_ID), any()))
                .thenThrow(new CameraSetConflictException(SESSION_ID, "camera SINGLE cannot join the cameras [LEFT]"));

        assertProblem(post(), HttpStatus.CONFLICT, "CAMERA_SET_CONFLICT", "camera-set-conflict");
    }

    @Test
    void should_return409PartCountMismatch_when_theCameraBringsAnotherPartCount() {
        when(registerCamera.register(eq(EVENT_ID), any()))
                .thenThrow(new PartCountMismatchException(SESSION_ID, 40, 41));

        assertProblem(post(), HttpStatus.CONFLICT, "PART_COUNT_MISMATCH", "part-count-mismatch");
    }

    @Test
    void should_return400NamingTheVenue_when_theFirstCameraHasNone() {
        when(registerCamera.register(eq(EVENT_ID), any()))
                .thenThrow(new VenueSpecException("venue", "is required for the first camera of a session"));

        final MvcTestResult result = post();

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "validation-failed");
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"errors": [{"field": "venue", "message": "is required for the first camera of a session"}]}""");
    }

    @Test
    void should_return500WithOnlyTheCorrelationId_when_somethingUnexpectedFails() {
        when(registerCamera.register(eq(EVENT_ID), any()))
                .thenThrow(new IllegalStateException("select * from upload_session failed on db-host-7"));

        final MvcTestResult result = mvc.post().uri(PATH, EVENT_ID)
                .header(CorrelationIdFilter.HEADER, "gui-500")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .exchange();

        assertProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "internal-error");
        assertThat(result).bodyJson().extractingPath("$.correlationId").isEqualTo("gui-500");
        assertThat(result).body().asString().doesNotContain("upload_session").doesNotContain("db-host-7");
    }

    @Test
    void should_return404SessionNotFound_when_theSessionIsUnknown() {
        whenCompleting().thenThrow(new SessionNotFoundException(SESSION_ID));

        assertProblem(complete(), HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "session-not-found");
    }

    @Test
    void should_return400NamingTheCamera_when_itIsNotRegisteredOnTheSession() {
        whenCompleting().thenThrow(new UnknownCameraException(SESSION_ID, Camera.LEFT));

        final MvcTestResult result = complete();

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "validation-failed");
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"errors": [{"field": "camera", "message": "is not registered on the session"}]}""");
    }

    @Test
    void should_return400NamingThePartIndex_when_itIsOutOfRange() {
        whenCompleting().thenThrow(new PartIndexOutOfRangeException(SESSION_ID, 40, 40));

        final MvcTestResult result = complete();

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "validation-failed");
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"errors": [{"field": "partIndex", "message": "must be between 0 and 39"}]}""");
    }

    @Test
    void should_return409ClipAlreadyCompleted_when_anUploadedClipComesWithAnotherBody() {
        whenCompleting().thenThrow(new ClipAlreadyCompletedException(SESSION_ID, Camera.LEFT, 3));

        assertProblem(complete(), HttpStatus.CONFLICT, "CLIP_ALREADY_COMPLETED", "clip-already-completed");
    }

    @Test
    void should_return422ObjectNotFound_when_theClipIsNotInTheBucket() {
        whenCompleting().thenThrow(new ObjectNotFoundException(KEY));

        assertProblem(complete(), HttpStatus.UNPROCESSABLE_CONTENT, "OBJECT_NOT_FOUND", "object-not-found");
    }

    @Test
    void should_return422SizeMismatchWithBothSizes_when_theObjectHasAnotherSize() {
        whenCompleting().thenThrow(new SizeMismatchException(KEY, 1_073_741_824L, 1_000L));

        final MvcTestResult result = complete();

        assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "SIZE_MISMATCH", "size-mismatch");
        assertThat(result).bodyJson().extractingPath("$.detail").asString()
                .contains("1073741824").contains("1000");
    }

    @Test
    void should_return422EtagMismatch_when_theObjectHasAnotherEtag() {
        whenCompleting().thenThrow(new EtagMismatchException(KEY, "9b2cf5", "77aa0c"));

        assertProblem(complete(), HttpStatus.UNPROCESSABLE_CONTENT, "ETAG_MISMATCH", "etag-mismatch");
    }

    @Test
    void should_return503WithRetryAfterAndNoInternals_when_theStorageIsUnavailable() {
        whenCompleting().thenThrow(new StorageUnavailableException("The storage did not answer for 'minio:9000'",
                new IllegalStateException("connect timed out")));

        final MvcTestResult result = complete();

        assertProblem(result, HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "storage-unavailable");
        assertThat(result).hasHeader("Retry-After", "5");
        assertThat(result).body().asString().doesNotContain("minio").doesNotContain("timed out");
    }

    private OngoingStubbing<ClipCompletion> whenCompleting() {
        return when(completeClip.complete(eq(SESSION_ID), any(), anyInt(), any()));
    }

    private MvcTestResult complete() {
        return mvc.post().uri(COMPLETE_PATH, SESSION_ID, "LEFT", 3)
                .contentType(MediaType.APPLICATION_JSON)
                .content(COMPLETE_BODY)
                .exchange();
    }

    private MvcTestResult post() {
        return mvc.post().uri(PATH, EVENT_ID).contentType(MediaType.APPLICATION_JSON).content(BODY).exchange();
    }

    private static void assertProblem(final MvcTestResult result,
                                      final HttpStatus status,
                                      final String code,
                                      final String type) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("https://cavalodetroia.dev/problems/" + type);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status.value());
    }
}

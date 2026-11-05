package org.cavalodetroia.ctr.api.error;

import org.cavalodetroia.ctr.api.controller.UploadSessionController;
import org.cavalodetroia.ctr.api.converter.UploadSessionConverter;
import org.cavalodetroia.ctr.api.filter.CorrelationIdFilter;
import org.cavalodetroia.ctr.api.idempotency.IdempotentRequests;
import org.cavalodetroia.ctr.domain.exception.CameraSetConflictException;
import org.cavalodetroia.ctr.domain.exception.EventNotFoundException;
import org.cavalodetroia.ctr.domain.exception.PartCountMismatchException;
import org.cavalodetroia.ctr.domain.exception.VenueSpecException;
import org.cavalodetroia.ctr.domain.port.in.RegisterCamera;
import org.cavalodetroia.ctr.domain.port.out.IdempotencyStore;
import org.junit.jupiter.api.Test;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * AC-5, AC-6: each domain exception becomes problem+json with its status, {@code code} and {@code type}; a 500
 * shows only the correlation id.
 */
@WebMvcTest(UploadSessionController.class)
@Import({UploadSessionConverter.class, IdempotentRequests.class})
class ApiExceptionHandlerTest {

    private static final String PATH = "/api/v1/events/{eventId}/upload-session/cameras";
    private static final String BODY = """
            {"camera": "RIGHT", "partCount": 40, "contentType": "video/mp4"}""";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private RegisterCamera registerCamera;

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

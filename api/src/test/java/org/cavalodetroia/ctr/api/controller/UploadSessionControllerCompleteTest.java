package org.cavalodetroia.ctr.api.controller;

import org.cavalodetroia.ctr.api.converter.UploadSessionConverter;
import org.cavalodetroia.ctr.api.idempotency.IdempotentRequests;
import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.ClipConfirmation;
import org.cavalodetroia.ctr.domain.port.in.CompleteClip;
import org.cavalodetroia.ctr.domain.port.in.RegisterCamera;
import org.cavalodetroia.ctr.domain.port.out.IdempotencyStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cavalodetroia.ctr.api.Registrations.LEFT_CLIP;
import static org.cavalodetroia.ctr.api.Registrations.SESSION_ID;
import static org.cavalodetroia.ctr.api.Registrations.leftConfirmedPart3;
import static org.cavalodetroia.ctr.api.Registrations.rightConfirmedPart3AfterLeft;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * POST .../parts/{partIndex}/complete. AC-1: the 200 body of the ticket's example; AC-2: COMPLETE once the second
 * camera confirms; AC-6: an invalid body or path gives 400 VALIDATION_FAILED naming the field, before the use case
 * runs.
 */
@WebMvcTest(UploadSessionController.class)
@Import({UploadSessionConverter.class, IdempotentRequests.class})
class UploadSessionControllerCompleteTest {

    private static final String PATH = "/api/v1/upload-sessions/{sessionId}/cameras/{camera}/parts/{partIndex}/complete";
    private static final String LEFT_BODY = """
            {"sizeBytes": 1073741824, "etag": "9b2cf535f27731c974343645a3985328",
             "startMs": 900000, "durationMs": 300000}""";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CompleteClip completeClip;

    @MockitoBean
    private RegisterCamera registerCamera;

    @MockitoBean
    private IdempotencyStore idempotencyStore;

    @Test
    void should_return200WithTheClipItsPartAndItsSession_when_leftConfirmsAClip() {
        when(completeClip.complete(eq(SESSION_ID), eq(Camera.LEFT), eq(3), any())).thenReturn(leftConfirmedPart3());

        assertThat(post("LEFT", 3, LEFT_BODY))
                .hasStatus(HttpStatus.OK)
                .bodyJson().isStrictlyEqualTo("""
                        {
                          "camera": "LEFT",
                          "partIndex": 3,
                          "status": "UPLOADED",
                          "part": {"status": "WAITING_RIGHT", "published": false},
                          "session": {"status": "OPEN", "partsPublished": 0, "partCount": 40}
                        }""");
    }

    @Test
    void should_passTheBodyToTheUseCaseAsSent_when_confirming() {
        when(completeClip.complete(eq(SESSION_ID), eq(Camera.LEFT), eq(3), any())).thenReturn(leftConfirmedPart3());

        post("LEFT", 3, LEFT_BODY);

        verify(completeClip).complete(SESSION_ID, Camera.LEFT, 3, LEFT_CLIP);
    }

    @Test
    void should_sayComplete_when_rightConfirmsAfterLeft() {
        when(completeClip.complete(eq(SESSION_ID), eq(Camera.RIGHT), eq(3), any()))
                .thenReturn(rightConfirmedPart3AfterLeft());

        assertThat(post("RIGHT", 3, """
                {"sizeBytes": 1073741000, "etag": "77aa0c1d6b1e4e8f9a0b1c2d3e4f5a6b",
                 "startMs": 898000, "durationMs": 300000}"""))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.part.status").isEqualTo("COMPLETE");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            sizeBytes zero          | sizeBytes  | {"sizeBytes": 0, "etag": "e", "startMs": 0, "durationMs": 1}
            sizeBytes missing       | sizeBytes  | {"etag": "e", "startMs": 0, "durationMs": 1}
            sizeBytes not a number  | sizeBytes  | {"sizeBytes": "big", "etag": "e", "startMs": 0, "durationMs": 1}
            etag blank              | etag       | {"sizeBytes": 1, "etag": " ", "startMs": 0, "durationMs": 1}
            etag missing            | etag       | {"sizeBytes": 1, "startMs": 0, "durationMs": 1}
            startMs negative        | startMs    | {"sizeBytes": 1, "etag": "e", "startMs": -1, "durationMs": 1}
            startMs missing         | startMs    | {"sizeBytes": 1, "etag": "e", "durationMs": 1}
            durationMs zero         | durationMs | {"sizeBytes": 1, "etag": "e", "startMs": 0, "durationMs": 0}
            durationMs missing      | durationMs | {"sizeBytes": 1, "etag": "e", "startMs": 0}
            """)
    void should_return400NamingTheFieldWithoutConfirming_when_theBodyIsInvalid(final String rule,
                                                                             final String field,
                                                                             final String body) {
        final MvcTestResult result = post("LEFT", 3, body);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().containsExactly(field);
        verifyNoInteractions(completeClip);
    }

    @Test
    void should_return400NamingTheEtag_when_itIsLongerThan255Characters() {
        final MvcTestResult result = post("LEFT", 3, """
                {"sizeBytes": 1, "etag": "%s", "startMs": 0, "durationMs": 1}""".formatted("e".repeat(256)));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[*].field").asArray().containsExactly("etag");
        verifyNoInteractions(completeClip);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            partIndex not an int | sessionId | LEFT  | three | partIndex
            camera unknown       | sessionId | FRONT | 3     | camera
            sessionId not a UUID | sess-7f3c | LEFT  | 3     | sessionId
            """)
    void should_return400NamingThePathVariable_when_itHasTheWrongType(final String rule,
                                                                    final String sessionId,
                                                                    final String camera,
                                                                    final String partIndex,
                                                                    final String field) {
        final String session = "sessionId".equals(sessionId) ? SESSION_ID.toString() : sessionId;

        final MvcTestResult result = mvc.post().uri(PATH, session, camera, partIndex)
                .contentType(MediaType.APPLICATION_JSON).content(LEFT_BODY).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0].field").isEqualTo(field);
        verifyNoInteractions(completeClip);
    }

    @Test
    void should_accept_when_theValuesAreAtTheEdgesOfTheRules() {
        when(completeClip.complete(eq(SESSION_ID), eq(Camera.LEFT), eq(3), any())).thenReturn(leftConfirmedPart3());

        assertThat(post("LEFT", 3, """
                {"sizeBytes": 1, "etag": "e", "startMs": 0, "durationMs": 1}""")).hasStatus(HttpStatus.OK);
        verify(completeClip).complete(SESSION_ID, Camera.LEFT, 3, new ClipConfirmation(1, "e", 0, 1));
    }

    private MvcTestResult post(final String camera, final int partIndex, final String body) {
        return mvc.post().uri(PATH, SESSION_ID, camera, partIndex)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}

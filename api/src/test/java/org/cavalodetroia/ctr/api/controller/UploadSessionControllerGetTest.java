package org.cavalodetroia.ctr.api.controller;

import org.cavalodetroia.ctr.api.converter.UploadSessionConverter;
import org.cavalodetroia.ctr.api.idempotency.IdempotentRequests;
import org.cavalodetroia.ctr.domain.exception.SessionNotFoundException;
import org.cavalodetroia.ctr.domain.port.in.CompleteClip;
import org.cavalodetroia.ctr.domain.port.in.GetUploadSession;
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
import static org.cavalodetroia.ctr.api.Registrations.mixedRightThenLeft;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TH-143 AC-1: the 200 body, with null part windows and PENDING clips without their fields; AC-4: an unknown session
 * is 404 SESSION_NOT_FOUND as problem+json; a sessionId that is not a UUID is 400 VALIDATION_FAILED.
 */
@WebMvcTest(UploadSessionController.class)
@Import({UploadSessionConverter.class, IdempotentRequests.class})
class UploadSessionControllerGetTest {

    private static final String PATH = "/api/v1/upload-sessions/{sessionId}";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private GetUploadSession getUploadSession;

    @MockitoBean
    private CompleteClip completeClip;

    @MockitoBean
    private RegisterCamera registerCamera;

    @MockitoBean
    private IdempotencyStore idempotencyStore;

    @Test
    void should_return200WithTheSessionItsCamerasAndEveryPart_when_theSessionExists() {
        when(getUploadSession.get(SESSION_ID)).thenReturn(mixedRightThenLeft());

        assertThat(mvc.get().uri(PATH, SESSION_ID))
                .hasStatus(HttpStatus.OK)
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson().isStrictlyEqualTo("""
                        {
                          "sessionId": "%1$s",
                          "eventId": "%2$s",
                          "status": "OPEN",
                          "partCount": 4,
                          "partsPublished": 1,
                          "venue": {"format": "FOOTBALL_11V11", "lengthM": 105.0, "widthM": 68.0},
                          "cameras": [
                            {"camera": "LEFT", "partCount": 4, "partsUploaded": 4,
                             "prefix": "s3://cdt-videos/events/%2$s/sessions/%1$s/LEFT/"},
                            {"camera": "RIGHT", "partCount": 4, "partsUploaded": 3,
                             "prefix": "s3://cdt-videos/events/%2$s/sessions/%1$s/RIGHT/"}
                          ],
                          "parts": [
                            {"partIndex": 0, "status": "COMPLETE", "published": true,
                             "startMs": 2000, "durationMs": 298000,
                             "clips": [
                               {"camera": "LEFT", "status": "UPLOADED", "sizeBytes": 1073741824,
                                "startMs": 0, "durationMs": 300000, "uploadedAt": "2026-11-04T18:07:02Z"},
                               {"camera": "RIGHT", "status": "UPLOADED", "sizeBytes": 1052000000,
                                "startMs": 2000, "durationMs": 300000, "uploadedAt": "2026-11-04T18:08:30Z"}
                             ]},
                            {"partIndex": 1, "status": "COMPLETE", "published": false,
                             "startMs": 300000, "durationMs": 300000,
                             "clips": [
                               {"camera": "LEFT", "status": "UPLOADED", "sizeBytes": 1073741824,
                                "startMs": 300000, "durationMs": 300000, "uploadedAt": "2026-11-04T18:07:02Z"},
                               {"camera": "RIGHT", "status": "UPLOADED", "sizeBytes": 1052000000,
                                "startMs": 300000, "durationMs": 300000, "uploadedAt": "2026-11-04T18:08:30Z"}
                             ]},
                            {"partIndex": 2, "status": "WAITING_RIGHT", "published": false,
                             "startMs": null, "durationMs": null,
                             "clips": [
                               {"camera": "LEFT", "status": "UPLOADED", "sizeBytes": 1073741824,
                                "startMs": 600000, "durationMs": 300000, "uploadedAt": "2026-11-04T18:07:02Z"},
                               {"camera": "RIGHT", "status": "PENDING"}
                             ]},
                            {"partIndex": 3, "status": "NO_OVERLAP", "published": false,
                             "startMs": null, "durationMs": null,
                             "clips": [
                               {"camera": "LEFT", "status": "UPLOADED", "sizeBytes": 1073741824,
                                "startMs": 900000, "durationMs": 300000, "uploadedAt": "2026-11-04T18:07:02Z"},
                               {"camera": "RIGHT", "status": "UPLOADED", "sizeBytes": 1052000000,
                                "startMs": 1202000, "durationMs": 300000, "uploadedAt": "2026-11-04T18:08:30Z"}
                             ]}
                          ]
                        }""".formatted(SESSION_ID, EVENT_ID));
    }

    @Test
    void should_return404SessionNotFoundAsProblemJson_when_theSessionIsUnknown() {
        when(getUploadSession.get(SESSION_ID)).thenThrow(new SessionNotFoundException(SESSION_ID));

        final MvcTestResult result = mvc.get().uri(PATH, SESSION_ID).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("SESSION_NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.type")
                .isEqualTo("https://cavalodetroia.dev/problems/session-not-found");
        assertThat(result).bodyJson().extractingPath("$.detail").asString().contains(SESSION_ID.toString());
    }

    @Test
    void should_return400NamingTheSessionIdWithoutReading_when_itIsNotAUuid() {
        final MvcTestResult result = mvc.get().uri(PATH, "sess-7f3c").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().containsExactly("sessionId");
        verifyNoInteractions(getUploadSession);
    }
}

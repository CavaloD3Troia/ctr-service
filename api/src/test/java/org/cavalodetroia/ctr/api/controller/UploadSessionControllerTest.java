package org.cavalodetroia.ctr.api.controller;

import org.cavalodetroia.ctr.api.converter.UploadSessionConverter;
import org.cavalodetroia.ctr.api.idempotency.IdempotentRequests;
import org.cavalodetroia.ctr.domain.model.idempotency.StoredResponse;
import org.cavalodetroia.ctr.domain.port.in.RegisterCamera;
import org.cavalodetroia.ctr.domain.port.out.IdempotencyStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cavalodetroia.ctr.api.Registrations.EVENT_ID;
import static org.cavalodetroia.ctr.api.Registrations.SESSION_ID;
import static org.cavalodetroia.ctr.api.Registrations.leftOpened;
import static org.cavalodetroia.ctr.api.Registrations.rightJoinedLeft;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AC-1, AC-2: the 201 body; AC-6: each validation rule gives 400 VALIDATION_FAILED naming the field, before the
 * event is looked up; AC-7: a repeat with the same Idempotency-Key and body replays the stored response.
 */
@WebMvcTest(UploadSessionController.class)
@Import({UploadSessionConverter.class, IdempotentRequests.class})
class UploadSessionControllerTest {

    private static final String PATH = "/api/v1/events/{eventId}/upload-session/cameras";
    private static final String LEFT_BODY = """
            {"camera": "LEFT", "partCount": 40, "contentType": "video/mp4",
             "venue": {"format": "FOOTBALL_11V11", "lengthM": 105, "widthM": 68}}""";
    private static final String RIGHT_BODY = """
            {"camera": "RIGHT", "partCount": 40, "contentType": "video/mp4"}""";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private RegisterCamera registerCamera;

    @MockitoBean
    private IdempotencyStore idempotencyStore;

    @Test
    void should_return201WithPrefixClipKeyAndCameras_when_theFirstCameraRegisters() {
        when(registerCamera.register(eq(EVENT_ID), any())).thenReturn(leftOpened());

        assertThat(post(LEFT_BODY, null))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().isStrictlyEqualTo("""
                        {
                          "sessionId": "%1$s",
                          "eventId": "%2$s",
                          "status": "OPEN",
                          "camera": "LEFT",
                          "partCount": 40,
                          "prefix": "s3://cdt-videos/events/%2$s/sessions/%1$s/LEFT/",
                          "clipKey": "{partIndex}.mp4",
                          "cameras": [{"camera": "LEFT", "partsUploaded": 0, "partCount": 40}]
                        }""".formatted(SESSION_ID, EVENT_ID));
    }

    @Test
    void should_listBothCameras_when_theSecondCameraJoins() {
        when(registerCamera.register(eq(EVENT_ID), any())).thenReturn(rightJoinedLeft());

        final MvcTestResult result = post(RIGHT_BODY, null);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.sessionId").isEqualTo(SESSION_ID.toString());
        assertThat(result).bodyJson().extractingPath("$.cameras[*].camera").asArray().containsExactly("LEFT", "RIGHT");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            partCount 0                | partCount     | {"camera": "LEFT", "partCount": 0, "contentType": "video/mp4"}
            partCount 1001             | partCount     | {"camera": "LEFT", "partCount": 1001, "contentType": "video/mp4"}
            partCount missing          | partCount     | {"camera": "LEFT", "contentType": "video/mp4"}
            partCount not a number     | partCount     | {"camera": "LEFT", "partCount": "forty", "contentType": "video/mp4"}
            camera missing             | camera        | {"partCount": 40, "contentType": "video/mp4"}
            camera unknown             | camera        | {"camera": "FRONT", "partCount": 40, "contentType": "video/mp4"}
            contentType not video      | contentType   | {"camera": "LEFT", "partCount": 40, "contentType": "application/octet-stream"}
            contentType missing        | contentType   | {"camera": "LEFT", "partCount": 40}
            venue format unknown       | venue.format  | {"camera": "LEFT", "partCount": 40, "contentType": "video/mp4", "venue": {"format": "HOCKEY"}}
            venue format missing       | venue.format  | {"camera": "LEFT", "partCount": 40, "contentType": "video/mp4", "venue": {}}
            venue length not positive  | venue.lengthM | {"camera": "LEFT", "partCount": 40, "contentType": "video/mp4", "venue": {"format": "FUTSAL_5V5", "lengthM": -40, "widthM": 20}}
            venue width without length | venue.lengthM | {"camera": "LEFT", "partCount": 40, "contentType": "video/mp4", "venue": {"format": "FUTSAL_5V5", "widthM": 20}}
            venue length without width | venue.widthM  | {"camera": "LEFT", "partCount": 40, "contentType": "video/mp4", "venue": {"format": "FUTSAL_5V5", "lengthM": 40}}
            """)
    void should_return400NamingTheFieldWithoutLookingUpTheEvent_when_theBodyIsInvalid(final String rule,
                                                                                    final String field,
                                                                                    final String body) {
        final MvcTestResult result = post(body, null);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().containsExactly(field);
        verifyNoInteractions(registerCamera);
    }

    @Test
    void should_return400_when_theBodyIsNotJson() {
        assertThat(post("{\"camera\": ", null))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void should_return400NamingTheEventId_when_itIsNotAUuid() {
        assertThat(mvc.post().uri(PATH, "evt-1").contentType(MediaType.APPLICATION_JSON).content(RIGHT_BODY))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0].field").isEqualTo("eventId");
    }

    @Test
    void should_return400NamingTheHeader_when_theIdempotencyKeyIsTooLong() {
        assertThat(post(RIGHT_BODY, "k".repeat(256)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[*].field").asArray().containsExactly("Idempotency-Key");
        verifyNoInteractions(registerCamera);
    }

    @ParameterizedTest(name = "{0}, {1} parts")
    @CsvSource(delimiter = '|', textBlock = """
            video/quicktime | 40
            video/mp4       | 1
            VIDEO/MP4       | 1000
            """)
    void should_accept_when_theValuesAreAtTheEdgesOfTheRules(final String contentType, final int partCount) {
        when(registerCamera.register(eq(EVENT_ID), any())).thenReturn(rightJoinedLeft());

        assertThat(post("""
                {"camera": "RIGHT", "partCount": %d, "contentType": "%s"}""".formatted(partCount, contentType), null))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void should_replayTheStoredResponseWithoutRegisteringAgain_when_theSameKeyAndBodyComeBack() throws Exception {
        when(registerCamera.register(eq(EVENT_ID), any())).thenReturn(leftOpened());
        final MvcTestResult first = post(LEFT_BODY, "9b1d");
        replayWhatWasStoredFor("9b1d");

        final MvcTestResult repeat = post(LEFT_BODY, "9b1d");

        assertThat(repeat).hasStatus(HttpStatus.CREATED);
        assertThat(repeat).bodyJson().isStrictlyEqualTo(first.getResponse().getContentAsString());
        verify(registerCamera, times(1)).register(any(), any());
        verify(idempotencyStore, times(1)).save(anyString(), any());
    }

    @Test
    void should_return422WithoutRegistering_when_theSameKeyComesWithAnotherBody() {
        when(registerCamera.register(eq(EVENT_ID), any())).thenReturn(leftOpened());
        post(LEFT_BODY, "9b1d");
        replayWhatWasStoredFor("9b1d");

        assertThat(post(LEFT_BODY.replace("40", "41"), "9b1d"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        verify(registerCamera, times(1)).register(any(), any());
    }

    @Test
    void should_neitherReadNorStoreAReplay_when_thereIsNoIdempotencyKey() {
        when(registerCamera.register(eq(EVENT_ID), any())).thenReturn(leftOpened());

        assertThat(post(LEFT_BODY, null)).hasStatus(HttpStatus.CREATED);
        verify(idempotencyStore, never()).find(anyString());
        verify(idempotencyStore, never()).save(anyString(), any());
    }

    private void replayWhatWasStoredFor(final String key) {
        final ArgumentCaptor<StoredResponse> stored = ArgumentCaptor.forClass(StoredResponse.class);
        verify(idempotencyStore).save(eq(key), stored.capture());
        when(idempotencyStore.find(key)).thenReturn(Optional.of(stored.getValue()));
    }

    private MvcTestResult post(final String body, final String idempotencyKey) {
        final var request = mvc.post().uri(PATH, EVENT_ID).contentType(MediaType.APPLICATION_JSON).content(body);
        if (idempotencyKey != null) {
            request.header(IdempotentRequests.HEADER, idempotencyKey);
        }
        return request.exchange();
    }
}

package org.cavalodetroia.ctr.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.cavalodetroia.ctr.api.converter.UploadSessionConverter;
import org.cavalodetroia.ctr.api.domain.upload.RegisterCameraRequest;
import org.cavalodetroia.ctr.api.domain.upload.RegisterCameraResponse;
import org.cavalodetroia.ctr.api.domain.upload.UploadSessionResponse;
import org.cavalodetroia.ctr.api.filter.CorrelationIdFilter;
import org.cavalodetroia.ctr.api.idempotency.IdempotentRequests;
import org.cavalodetroia.ctr.domain.port.in.GetUploadSession;
import org.cavalodetroia.ctr.domain.port.in.RegisterCamera;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HTTP only: validate, convert, delegate to the domain, convert the result.
 */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Upload session", description = "The upload of an event's clips by its cameras")
public class UploadSessionController {

    private static final String MDC_CAMERA = "camera";
    private static final String MDC_SESSION_ID = "session_id";
    private static final String CORRELATION_ID = "The request's correlation id: the X-Correlation-Id sent, or a new "
            + "CTR-<uuidv7>";
    private static final String CORRELATION_ID_SENT = "Correlation id for the logs, echoed back; 1 to 128 of A-Z a-z "
            + "0-9 . _ : -, otherwise replaced by a generated CTR-<uuidv7>";

    private final RegisterCamera registerCamera;
    private final GetUploadSession getUploadSession;
    private final UploadSessionConverter converter;
    private final IdempotentRequests idempotentRequests;

    public UploadSessionController(final RegisterCamera registerCamera,
                                   final GetUploadSession getUploadSession,
                                   final UploadSessionConverter converter,
                                   final IdempotentRequests idempotentRequests) {
        this.registerCamera = registerCamera;
        this.getUploadSession = getUploadSession;
        this.converter = converter;
        this.idempotentRequests = idempotentRequests;
    }

    @Operation(
            summary = "Register a camera on the event's upload session",
            description = "The first camera of an event opens its upload session and sets the venue; a second camera "
                    + "joins it. A session ends with the cameras {SINGLE} or {LEFT, RIGHT}, all with the same "
                    + "partCount. The camera uploads clip i (0-based) to prefix + clipKey with {partIndex} = i.",
            parameters = @Parameter(in = ParameterIn.HEADER, name = CorrelationIdFilter.HEADER,
                    description = CORRELATION_ID_SENT,
                    schema = @Schema(type = "string", maxLength = 128, example = "gui-7f3c")))
    @ApiResponse(responseCode = "201", description = "Registered: the session (new or joined) and the camera's prefix",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = CORRELATION_ID),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = RegisterCameraResponse.class)))
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED: errors lists each invalid field "
            + "({field, message}). Also when the first camera of a session sends no venue.",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = CORRELATION_ID),
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "EVENT_NOT_FOUND: ctr-service does not know the event",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = CORRELATION_ID),
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "CAMERA_SET_CONFLICT: the camera is already registered (with "
            + "another Idempotency-Key) or does not fit the session's cameras. PART_COUNT_MISMATCH: the camera "
            + "brings another partCount than the cameras already registered.",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = CORRELATION_ID),
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "IDEMPOTENCY_KEY_REUSED: the Idempotency-Key was used in the "
            + "last 24 hours for a different request",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = CORRELATION_ID),
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: the body carries only the correlationId",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = CORRELATION_ID),
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping(value = "/events/{eventId}/upload-session/cameras", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RegisterCameraResponse> registerCamera(
            @Parameter(description = "The event filmed") @PathVariable final UUID eventId,
            @Parameter(description = "Makes a retry safe: the same key with the same body within 24 hours returns "
                    + "the first 201 without registering again", example = "9b1d6f4e-2c1a-4f7e-9a51-0c7d3e8b2f10")
            @RequestHeader(value = IdempotentRequests.HEADER, required = false)
            @Size(min = 1, max = IdempotentRequests.MAX_KEY_LENGTH) final String idempotencyKey,
            @Valid @RequestBody final RegisterCameraRequest request,
            final HttpServletRequest http) {
        MDC.put(MDC_CAMERA, request.camera().name());
        return idempotentRequests.respond(idempotencyKey, http, request, RegisterCameraResponse.class,
                () -> ResponseEntity.status(HttpStatus.CREATED).body(
                        converter.toResponse(registerCamera.register(eventId, converter.toRegistration(request)))));
    }

    @Operation(
            summary = "Read an upload session with its cameras and parts",
            description = "Where the upload stands: the session and its venue, each registered camera with how many "
                    + "clips it has uploaded, and every part from 0 to partCount - 1 with one clip per registered "
                    + "camera, LEFT before RIGHT. A part always has startMs and durationMs, null until it is "
                    + "COMPLETE and for NO_OVERLAP. A clip leaves out sizeBytes, startMs, durationMs and uploadedAt "
                    + "while it is PENDING. Reads the database only.",
            parameters = @Parameter(in = ParameterIn.HEADER, name = CorrelationIdFilter.HEADER,
                    description = CORRELATION_ID_SENT,
                    schema = @Schema(type = "string", maxLength = 128, example = "gui-7f3c")))
    @ApiResponse(responseCode = "200", description = "The session, its cameras and every part with its clips",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = CORRELATION_ID),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = UploadSessionResponse.class)))
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED: the sessionId is not a UUID; errors names "
            + "it ({field, message})",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = CORRELATION_ID),
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "SESSION_NOT_FOUND: no upload session has this id",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = CORRELATION_ID),
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: the body carries only the correlationId",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = CORRELATION_ID),
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/upload-sessions/{sessionId}")
    public ResponseEntity<UploadSessionResponse> getUploadSession(
            @Parameter(description = "The upload session") @PathVariable final UUID sessionId) {
        MDC.put(MDC_SESSION_ID, sessionId.toString());
        return ResponseEntity.ok(converter.toResponse(getUploadSession.get(sessionId)));
    }
}

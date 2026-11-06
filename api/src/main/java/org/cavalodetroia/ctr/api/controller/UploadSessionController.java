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
import org.cavalodetroia.ctr.api.domain.upload.CompleteClipRequest;
import org.cavalodetroia.ctr.api.domain.upload.CompleteClipResponse;
import org.cavalodetroia.ctr.api.domain.upload.RegisterCameraRequest;
import org.cavalodetroia.ctr.api.domain.upload.RegisterCameraResponse;
import org.cavalodetroia.ctr.api.domain.upload.UploadSessionResponse;
import org.cavalodetroia.ctr.api.error.ApiExceptionHandler;
import org.cavalodetroia.ctr.api.filter.CorrelationIdFilter;
import org.cavalodetroia.ctr.api.idempotency.IdempotentRequests;
import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.port.in.CompleteClip;
import org.cavalodetroia.ctr.domain.port.in.GetUploadSession;
import org.cavalodetroia.ctr.domain.port.in.RegisterCamera;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
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
    private static final String MDC_PART_INDEX = "part_index";
    private static final String MDC_SESSION_ID = "session_id";
    private static final String CORRELATION_ID = "The request's correlation id: the X-Correlation-Id sent, or a new "
            + "CTR-<uuidv7>";
    private static final String CORRELATION_ID_PARAMETER = "Correlation id for the logs, echoed back; 1 to 128 of "
            + "A-Z a-z 0-9 . _ : -, otherwise replaced by a generated CTR-<uuidv7>";

    private final RegisterCamera registerCamera;
    private final GetUploadSession getUploadSession;
    private final CompleteClip completeClip;
    private final UploadSessionConverter converter;
    private final IdempotentRequests idempotentRequests;

    public UploadSessionController(final RegisterCamera registerCamera,
                                   final GetUploadSession getUploadSession,
                                   final CompleteClip completeClip,
                                   final UploadSessionConverter converter,
                                   final IdempotentRequests idempotentRequests) {
        this.registerCamera = registerCamera;
        this.getUploadSession = getUploadSession;
        this.completeClip = completeClip;
        this.converter = converter;
        this.idempotentRequests = idempotentRequests;
    }

    @Operation(
            summary = "Register a camera on the event's upload session",
            description = "The first camera of an event opens its upload session and sets the venue; a second camera "
                    + "joins it. A session ends with the cameras {SINGLE} or {LEFT, RIGHT}, all with the same "
                    + "partCount. The camera uploads clip i (0-based) to prefix + clipKey with {partIndex} = i.",
            parameters = @Parameter(in = ParameterIn.HEADER, name = CorrelationIdFilter.HEADER,
                    description = CORRELATION_ID_PARAMETER,
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
                    description = CORRELATION_ID_PARAMETER,
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

    @Operation(
            summary = "Confirm a clip the camera uploaded",
            description = "After the PUT of clip partIndex to prefix + clipKey, the camera sends what S3 answered "
                    + "(sizeBytes, etag) and where the clip sits in the recording (startMs, durationMs). ctr-service "
                    + "asks S3 for the object's size and etag with a HEAD, never downloading it, and only when they "
                    + "agree marks the clip UPLOADED. The part is COMPLETE when every camera has uploaded clip "
                    + "partIndex: its window is the clip's own for SINGLE, and the overlap of the two clips for LEFT "
                    + "and RIGHT (the later start to the earlier end), or NO_OVERLAP when they share none. The "
                    + "session is COMPLETED when every clip of every camera is uploaded. Once the part is COMPLETE and "
                    + "stored, its message goes to Kafka (ClipPairUploaded for LEFT and RIGHT, ClipReady for SINGLE) "
                    + "and part.published says whether the broker took it; a broker that is down never fails the "
                    + "call. Repeating the call with the same body returns the same 200 and writes nothing, except "
                    + "that it publishes a COMPLETE part whose publishing failed.",
            parameters = @Parameter(in = ParameterIn.HEADER, name = CorrelationIdFilter.HEADER,
                    description = CORRELATION_ID_PARAMETER,
                    schema = @Schema(type = "string", maxLength = 128, example = "gui-7f3c")))
    @ApiResponse(responseCode = "200", description = "Confirmed (or confirmed before with the same body): the clip, "
            + "its part and its session as stored",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = CORRELATION_ID),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CompleteClipResponse.class)))
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED: errors lists each invalid field "
            + "({field, message}): the camera is not registered on the session, partIndex is outside 0 to "
            + "partCount - 1, or the body breaks a rule (sizeBytes and durationMs positive, startMs zero or more, "
            + "etag not blank)",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = CORRELATION_ID),
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "SESSION_NOT_FOUND: ctr-service does not know the session",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = CORRELATION_ID),
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "CLIP_ALREADY_COMPLETED: the clip was already confirmed with "
            + "a different body",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = CORRELATION_ID),
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "The bucket disagrees with the body, nothing was written: "
            + "OBJECT_NOT_FOUND (no object at the clip's key), SIZE_MISMATCH (another size, for example a truncated "
            + "upload), ETAG_MISMATCH (another etag, compared without double quotes)",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = CORRELATION_ID),
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: the body carries only the correlationId",
            headers = @Header(name = CorrelationIdFilter.HEADER, description = CORRELATION_ID),
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "STORAGE_UNAVAILABLE: the bucket could not be asked within "
            + "5 s; nothing was written, try again after Retry-After seconds",
            headers = {
                    @Header(name = CorrelationIdFilter.HEADER, description = CORRELATION_ID),
                    @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait before trying again",
                            schema = @Schema(type = "integer", example = ApiExceptionHandler.RETRY_AFTER_SECONDS))},
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping(value = "/upload-sessions/{sessionId}/cameras/{camera}/parts/{partIndex}/complete",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CompleteClipResponse> completeClip(
            @Parameter(description = "The upload session, as given at registration") @PathVariable final UUID sessionId,
            @Parameter(description = "The camera that uploaded the clip") @PathVariable final Camera camera,
            @Parameter(description = "The part of the clip, 0-based", example = "3")
            @PathVariable final int partIndex,
            @Valid @RequestBody final CompleteClipRequest request) {
        MDC.put(MDC_CAMERA, camera.name());
        MDC.put(MDC_PART_INDEX, String.valueOf(partIndex));
        return ResponseEntity.ok(converter.toResponse(
                completeClip.complete(sessionId, camera, partIndex, converter.toConfirmation(request))));
    }
}

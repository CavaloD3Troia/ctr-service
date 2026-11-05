package org.cavalodetroia.ctr.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.cavalodetroia.ctr.api.converter.UploadSessionConverter;
import org.cavalodetroia.ctr.api.domain.upload.RegisterCameraRequest;
import org.cavalodetroia.ctr.api.domain.upload.RegisterCameraResponse;
import org.cavalodetroia.ctr.api.idempotency.IdempotentRequests;
import org.cavalodetroia.ctr.domain.port.in.RegisterCamera;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping(value = "/api/v1/events/{eventId}/upload-session", produces = MediaType.APPLICATION_JSON_VALUE)
public class UploadSessionController {

    private static final String MDC_CAMERA = "camera";

    private final RegisterCamera registerCamera;
    private final UploadSessionConverter converter;
    private final IdempotentRequests idempotentRequests;

    public UploadSessionController(final RegisterCamera registerCamera,
                                   final UploadSessionConverter converter,
                                   final IdempotentRequests idempotentRequests) {
        this.registerCamera = registerCamera;
        this.converter = converter;
        this.idempotentRequests = idempotentRequests;
    }

    @PostMapping(value = "/cameras", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RegisterCameraResponse> registerCamera(
            @PathVariable final UUID eventId,
            @RequestHeader(value = IdempotentRequests.HEADER, required = false)
            @Size(min = 1, max = IdempotentRequests.MAX_KEY_LENGTH) final String idempotencyKey,
            @Valid @RequestBody final RegisterCameraRequest request,
            final HttpServletRequest http) {
        MDC.put(MDC_CAMERA, request.camera().name());
        return idempotentRequests.respond(idempotencyKey, http, request, RegisterCameraResponse.class,
                () -> ResponseEntity.status(HttpStatus.CREATED).body(
                        converter.toResponse(registerCamera.register(eventId, converter.toRegistration(request)))));
    }
}

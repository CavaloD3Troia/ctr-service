package org.cavalodetroia.ctr.api.error;

import org.cavalodetroia.ctr.api.filter.CorrelationIdFilter;
import org.cavalodetroia.ctr.domain.exception.CameraSetConflictException;
import org.cavalodetroia.ctr.domain.exception.EventNotFoundException;
import org.cavalodetroia.ctr.domain.exception.IdempotencyKeyReusedException;
import org.cavalodetroia.ctr.domain.exception.PartCountMismatchException;
import org.cavalodetroia.ctr.domain.exception.SessionNotFoundException;
import org.cavalodetroia.ctr.domain.exception.VenueSpecException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Every error as RFC 9457 {@code application/problem+json} with a stable {@code code} and a {@code type} of
 * {@value #TYPE_BASE}{@code <code-in-kebab-case>}. A 500 carries only the correlation id, never internals.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    static final String TYPE_BASE = "https://cavalodetroia.dev/problems/";

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(EventNotFoundException.class)
    ResponseEntity<Object> eventNotFound(final EventNotFoundException e) {
        return respond(problem(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(SessionNotFoundException.class)
    ResponseEntity<Object> sessionNotFound(final SessionNotFoundException e) {
        return respond(problem(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(CameraSetConflictException.class)
    ResponseEntity<Object> cameraSetConflict(final CameraSetConflictException e) {
        return respond(problem(HttpStatus.CONFLICT, "CAMERA_SET_CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(PartCountMismatchException.class)
    ResponseEntity<Object> partCountMismatch(final PartCountMismatchException e) {
        return respond(problem(HttpStatus.CONFLICT, "PART_COUNT_MISMATCH", e.getMessage()));
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ResponseEntity<Object> idempotencyKeyReused(final IdempotencyKeyReusedException e) {
        return respond(problem(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED", e.getMessage()));
    }

    @ExceptionHandler(VenueSpecException.class)
    ResponseEntity<Object> invalidVenue(final VenueSpecException e) {
        return respond(validationFailed(List.of(new InvalidField(e.field(), e.reason()))));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(final Exception e) {
        LOGGER.error("operation=handleRequest, message=Unexpected error", e);
        final ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong. Quote the correlation id when reporting it.");
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return respond(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(final MethodArgumentNotValidException ex,
                                                                  final HttpHeaders headers,
                                                                  final HttpStatusCode status,
                                                                  final WebRequest request) {
        return respond(validationFailed(fieldErrors(ex.getBindingResult().getFieldErrors())));
    }

    // Spring validates every argument together when a parameter carries a constraint (the Idempotency-Key size)
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(final HandlerMethodValidationException ex,
                                                                            final HttpHeaders headers,
                                                                            final HttpStatusCode status,
                                                                            final WebRequest request) {
        final List<InvalidField> invalid = new ArrayList<>();
        for (final ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors errors) {
                invalid.addAll(fieldErrors(errors.getFieldErrors()));
            } else {
                final String name = parameterName(result.getMethodParameter());
                result.getResolvableErrors().forEach(error -> invalid.add(new InvalidField(name, message(error))));
            }
        }
        return respond(validationFailed(invalid));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(final HttpMessageNotReadableException ex,
                                                                  final HttpHeaders headers,
                                                                  final HttpStatusCode status,
                                                                  final WebRequest request) {
        // A value of the wrong type (an unknown camera, a text partCount) names its field; broken JSON names none
        if (ex.getCause() instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
            final String field = jackson.getPath().stream()
                    .map(reference -> reference.getPropertyName() != null
                            ? reference.getPropertyName() : String.valueOf(reference.getIndex()))
                    .collect(Collectors.joining("."));
            return respond(validationFailed(List.of(new InvalidField(field, "has an invalid value"))));
        }
        return respond(validationFailed(List.of()));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(final TypeMismatchException ex,
                                                        final HttpHeaders headers,
                                                        final HttpStatusCode status,
                                                        final WebRequest request) {
        final String field = Objects.requireNonNullElse(ex.getPropertyName(), "request");
        return respond(validationFailed(List.of(new InvalidField(field, "has an invalid value"))));
    }

    private static ProblemDetail validationFailed(final List<InvalidField> invalid) {
        final ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "The request has invalid fields");
        problem.setProperty("errors", invalid.stream()
                .sorted(Comparator.comparing(InvalidField::field).thenComparing(InvalidField::message))
                .toList());
        return problem;
    }

    private static ProblemDetail problem(final HttpStatus status, final String code, final String detail) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setProperty("code", code);
        return problem;
    }

    private static ResponseEntity<Object> respond(final ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    private static List<InvalidField> fieldErrors(final List<FieldError> errors) {
        return errors.stream().map(error -> new InvalidField(error.getField(), message(error))).toList();
    }

    private static String message(final MessageSourceResolvable error) {
        return Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid");
    }

    private static String parameterName(final MethodParameter parameter) {
        final RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null) {
            return header.value().isEmpty() ? header.name() : header.value();
        }
        return Objects.requireNonNullElse(parameter.getParameterName(), "request");
    }
}

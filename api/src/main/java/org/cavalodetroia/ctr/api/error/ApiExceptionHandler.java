package org.cavalodetroia.ctr.api.error;

import org.cavalodetroia.ctr.api.filter.CorrelationIdFilter;
import org.cavalodetroia.ctr.domain.event.EventNotFoundException;
import org.cavalodetroia.ctr.domain.job.JobStatusNotFoundException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

/**
 * The one place that turns exceptions into RFC 9457 problem responses, each with a stable {@code code}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String CODE = "code";

    @ExceptionHandler(EventNotFoundException.class)
    public ProblemDetail handle(final EventNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ErrorCode.EVENT_NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(JobStatusNotFoundException.class)
    public ProblemDetail handle(final JobStatusNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ErrorCode.JOB_STATUS_NOT_FOUND, ex.getMessage());
    }

    /**
     * Catch-all: the cause goes to the logs, the client only gets the correlation id to report.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handle(final Exception ex) {
        LOGGER.error("operation=handle, message=Unexpected error", ex);
        final ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL,
                "An unexpected error occurred");
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return problem;
    }

    /**
     * {@code @Valid} failed on a request body: list every invalid field.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(final MethodArgumentNotValidException ex,
                                                                  final HttpHeaders headers,
                                                                  final HttpStatusCode status,
                                                                  final WebRequest request) {
        final Map<String, String> errors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        final ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Invalid request");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /**
     * Every other error Spring MVC handles (unreadable JSON, a bad UUID in the path, 405, 415...) keeps Spring's
     * problem body and gets a code too.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable final Object body,
                                                          final HttpHeaders headers,
                                                          final HttpStatusCode statusCode,
                                                          final WebRequest request) {
        if (body instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey(CODE))) {
            problem.setProperty(CODE, ErrorCode.forStatus(statusCode));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static ProblemDetail problem(final HttpStatus status, final String code, final String detail) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE, code);
        return problem;
    }
}

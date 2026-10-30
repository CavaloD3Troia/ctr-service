package org.cavalodetroia.ctr.api.event;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.cavalodetroia.ctr.domain.event.Event;
import org.cavalodetroia.ctr.domain.event.port.in.CreateEvent;
import org.cavalodetroia.ctr.domain.event.port.in.GetEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

/**
 * HTTP only: validate, convert, delegate to the domain, convert back.
 */
@RestController
@RequestMapping(value = "/api/v1/events", produces = MediaType.APPLICATION_JSON_VALUE)
public class EventController {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventController.class);

    private final CreateEvent createEvent;
    private final GetEvent getEvent;
    private final EventRequestConverter requestConverter;
    private final EventResponseConverter responseConverter;

    public EventController(final CreateEvent createEvent,
                           final GetEvent getEvent,
                           final EventRequestConverter requestConverter,
                           final EventResponseConverter responseConverter) {
        this.createEvent = createEvent;
        this.getEvent = getEvent;
        this.requestConverter = requestConverter;
        this.responseConverter = responseConverter;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Event created"),
            @ApiResponse(responseCode = "400", description = "Invalid request (VALIDATION_FAILED)")})
    public ResponseEntity<EventResponse> create(@Valid @RequestBody final CreateEventRequest request) {
        LOGGER.info("operation=create, message=Creating event sport='{}'", request.sport());

        final Event event = createEvent.create(requestConverter.toDomain(request));

        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(event.id()).toUri())
                .body(responseConverter.toResponse(event));
    }

    @GetMapping("/{eventId}")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The event"),
            @ApiResponse(responseCode = "404", description = "No such event (EVENT_NOT_FOUND)")})
    public EventResponse byId(@PathVariable final UUID eventId) {
        return responseConverter.toResponse(getEvent.byId(eventId));
    }
}

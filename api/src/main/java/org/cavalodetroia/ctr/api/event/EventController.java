package org.cavalodetroia.ctr.api.event;

import jakarta.validation.Valid;
import org.cavalodetroia.ctr.domain.event.port.in.CreateEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP only: validate, delegate to the domain, convert the result.
 */
@RestController
@RequestMapping(value = "/api/v1/events", produces = MediaType.APPLICATION_JSON_VALUE)
public class EventController {

    private final CreateEvent createEvent;
    private final EventConverter converter;

    public EventController(final CreateEvent createEvent, final EventConverter converter) {
        this.createEvent = createEvent;
        this.converter = converter;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody final CreateEventRequest request) {
        return converter.toResponse(createEvent.create(request.name()));
    }
}

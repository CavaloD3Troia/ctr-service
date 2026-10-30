package org.cavalodetroia.ctr.api.job;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.cavalodetroia.ctr.domain.job.port.in.GetJobStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/events/{eventId}/analysis", produces = MediaType.APPLICATION_JSON_VALUE)
public class JobStatusController {

    private final GetJobStatus getJobStatus;
    private final JobStatusResponseConverter converter;

    public JobStatusController(final GetJobStatus getJobStatus, final JobStatusResponseConverter converter) {
        this.getJobStatus = getJobStatus;
        this.converter = converter;
    }

    @GetMapping
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Latest status of the event's processing job"),
            @ApiResponse(responseCode = "404", description = "Nothing reported yet (JOB_STATUS_NOT_FOUND)")})
    public JobStatusResponse byEventId(@PathVariable final UUID eventId) {
        return converter.toResponse(getJobStatus.byEventId(eventId));
    }
}

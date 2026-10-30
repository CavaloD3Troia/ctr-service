package org.cavalodetroia.ctr.api.job;

import org.cavalodetroia.ctr.domain.job.JobStatus;
import org.springframework.stereotype.Component;

@Component
public class JobStatusResponseConverter {

    public JobStatusResponse toResponse(final JobStatus status) {
        return new JobStatusResponse(status.eventId(), status.state().name(), status.updatedAt());
    }
}

package org.cavalodetroia.ctr.domain.job.port.in;

import org.cavalodetroia.ctr.domain.job.JobStatus;
import org.cavalodetroia.ctr.domain.job.JobStatusNotFoundException;

import java.util.UUID;

public interface GetJobStatus {

    /**
     * @throws JobStatusNotFoundException when the pipeline has not reported anything for that event yet
     */
    JobStatus byEventId(UUID eventId);
}

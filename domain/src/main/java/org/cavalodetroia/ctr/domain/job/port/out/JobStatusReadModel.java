package org.cavalodetroia.ctr.domain.job.port.out;

import org.cavalodetroia.ctr.domain.job.JobStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Local projection of the pipeline's job status, so reads never depend on the pipeline being up.
 */
public interface JobStatusReadModel {

    Optional<JobStatus> findByEventId(UUID eventId);

    void save(JobStatus status);
}

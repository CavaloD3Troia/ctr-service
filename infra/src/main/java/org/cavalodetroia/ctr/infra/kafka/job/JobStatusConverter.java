package org.cavalodetroia.ctr.infra.kafka.job;

import org.cavalodetroia.contracts.pipeline.v1.JobStatusChanged;
import org.cavalodetroia.ctr.domain.job.JobState;
import org.cavalodetroia.ctr.domain.job.JobStatus;
import org.cavalodetroia.ctr.infra.kafka.ProtoTimestamps;

import java.util.UUID;

public class JobStatusConverter {

    /**
     * @throws IllegalArgumentException when the message cannot be a valid status: bad id, unknown state or no time
     */
    public JobStatus toDomain(final JobStatusChanged message) {
        if (!message.hasOccurredAt()) {
            throw new IllegalArgumentException("occurred_at is missing");
        }
        return new JobStatus(
                UUID.fromString(message.getEventId()),
                toState(message.getState()),
                ProtoTimestamps.toInstant(message.getOccurredAt()));
    }

    private static JobState toState(final org.cavalodetroia.contracts.pipeline.v1.JobState state) {
        return switch (state) {
            case JOB_STATE_QUEUED -> JobState.QUEUED;
            case JOB_STATE_PROCESSING -> JobState.PROCESSING;
            case JOB_STATE_READY -> JobState.READY;
            case JOB_STATE_FAILED -> JobState.FAILED;
            case JOB_STATE_UNSPECIFIED, UNRECOGNIZED -> throw new IllegalArgumentException("Unsupported job state " + state);
        };
    }
}

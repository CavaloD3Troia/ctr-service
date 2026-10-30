package org.cavalodetroia.ctr.infra.kafka.job;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.cavalodetroia.contracts.pipeline.v1.JobStatusChanged;
import org.cavalodetroia.ctr.domain.job.JobStatus;
import org.cavalodetroia.ctr.domain.job.port.in.ProjectJobStatus;
import org.cavalodetroia.ctr.infra.kafka.CorrelationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.listener.MessageListener;

/**
 * Converts and delegates; the projection rules live in the domain.
 */
public class JobStatusListener implements MessageListener<String, JobStatusChanged> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobStatusListener.class);

    private final ProjectJobStatus projectJobStatus;
    private final JobStatusConverter converter;

    public JobStatusListener(final ProjectJobStatus projectJobStatus, final JobStatusConverter converter) {
        this.projectJobStatus = projectJobStatus;
        this.converter = converter;
    }

    @Override
    public void onMessage(final ConsumerRecord<String, JobStatusChanged> record) {
        try {
            CorrelationId.putInMdc(record.headers());
            final JobStatus status = converter.toDomain(record.value());

            LOGGER.info("operation=onMessage, message=Job status received eventId='{}' state='{}'",
                    status.eventId(), status.state());
            projectJobStatus.project(status);

        } catch (IllegalArgumentException e) {
            // Retrying will never fix a malformed message: log it and move on so the partition is not blocked
            LOGGER.warn("operation=onMessage, message=Invalid job status skipped key='{}' offset={} cause='{}'",
                    record.key(), record.offset(), e.getMessage());
        } finally {
            // Listener threads are reused: never leak this record's id into the next one
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}

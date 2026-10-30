package org.cavalodetroia.ctr.infra.kafka.job;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.cavalodetroia.contracts.pipeline.v1.JobStatusChanged;
import org.cavalodetroia.ctr.domain.job.JobState;
import org.cavalodetroia.ctr.domain.job.JobStatus;
import org.cavalodetroia.ctr.domain.job.port.in.ProjectJobStatus;
import org.cavalodetroia.ctr.infra.kafka.ProtoTimestamps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class JobStatusListenerTest {

    private static final String TOPIC = "pipeline.job.status.v1";
    private static final UUID EVENT_ID = UUID.fromString("0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11");
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-30T10:15:30Z");

    @Mock
    private ProjectJobStatus projectJobStatus;

    @Test
    void should_projectStatus_when_messageIsValid() {
        listener().onMessage(record(EVENT_ID.toString()));

        verify(projectJobStatus).project(new JobStatus(EVENT_ID, JobState.READY, OCCURRED_AT));
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void should_skipWithoutThrowing_when_messageIsInvalid() {
        listener().onMessage(record("not-a-uuid"));

        verifyNoInteractions(projectJobStatus);
    }

    private JobStatusListener listener() {
        return new JobStatusListener(projectJobStatus, new JobStatusConverter());
    }

    private static ConsumerRecord<String, JobStatusChanged> record(final String eventId) {
        final JobStatusChanged message = JobStatusChanged.newBuilder()
                .setEventId(eventId)
                .setState(org.cavalodetroia.contracts.pipeline.v1.JobState.JOB_STATE_READY)
                .setOccurredAt(ProtoTimestamps.from(OCCURRED_AT))
                .build();
        return new ConsumerRecord<>(TOPIC, 0, 0L, eventId, message);
    }
}

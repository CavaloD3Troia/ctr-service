package org.cavalodetroia.ctr.api.job;

import org.cavalodetroia.ctr.domain.job.JobState;
import org.cavalodetroia.ctr.domain.job.JobStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JobStatusResponseConverterTest {

    private final JobStatusResponseConverter converter = new JobStatusResponseConverter();

    @Test
    void should_mapAllFields_when_statusExists() {
        final JobStatus status = new JobStatus(UUID.randomUUID(), JobState.PROCESSING,
                Instant.parse("2026-10-30T10:15:30Z"));

        assertThat(converter.toResponse(status))
                .isEqualTo(new JobStatusResponse(status.eventId(), "PROCESSING", status.updatedAt()));
    }
}

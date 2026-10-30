package org.cavalodetroia.ctr.infra.persistence;

import org.cavalodetroia.ctr.domain.job.JobState;
import org.cavalodetroia.ctr.domain.job.JobStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JobStatusRecordConverterTest {

    private final JobStatusRecordConverter converter = new JobStatusRecordConverter();

    @Test
    void should_preserveAllFields_when_roundTripped() {
        final JobStatus status = new JobStatus(UUID.randomUUID(), JobState.PROCESSING,
                Instant.parse("2026-10-30T10:15:30.123Z"));

        assertThat(converter.toDomain(converter.toRecord(status))).isEqualTo(status);
    }
}

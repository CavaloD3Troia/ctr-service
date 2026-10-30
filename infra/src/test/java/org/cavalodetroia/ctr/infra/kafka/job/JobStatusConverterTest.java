package org.cavalodetroia.ctr.infra.kafka.job;

import org.cavalodetroia.contracts.pipeline.v1.JobStatusChanged;
import org.cavalodetroia.ctr.domain.job.JobState;
import org.cavalodetroia.ctr.domain.job.JobStatus;
import org.cavalodetroia.ctr.infra.kafka.ProtoTimestamps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class JobStatusConverterTest {

    private static final UUID EVENT_ID = UUID.fromString("0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11");
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-30T10:15:30Z");

    private final JobStatusConverter converter = new JobStatusConverter();

    @Test
    void should_mapAllFields_when_messageIsValid() {
        final JobStatus status = converter.toDomain(message(EVENT_ID.toString(),
                org.cavalodetroia.contracts.pipeline.v1.JobState.JOB_STATE_PROCESSING));

        assertThat(status).isEqualTo(new JobStatus(EVENT_ID, JobState.PROCESSING, OCCURRED_AT));
    }

    @ParameterizedTest
    @EnumSource(value = org.cavalodetroia.contracts.pipeline.v1.JobState.class, names = {"JOB_STATE_UNSPECIFIED", "UNRECOGNIZED"}, mode = EnumSource.Mode.EXCLUDE)
    void should_mapEveryKnownState(final org.cavalodetroia.contracts.pipeline.v1.JobState state) {
        assertThat(converter.toDomain(message(EVENT_ID.toString(), state)).state()).isNotNull();
    }

    @Test
    void should_reject_when_stateIsUnspecified() {
        assertThatIllegalArgumentException().isThrownBy(() -> converter.toDomain(message(EVENT_ID.toString(),
                org.cavalodetroia.contracts.pipeline.v1.JobState.JOB_STATE_UNSPECIFIED)));
    }

    @Test
    void should_reject_when_eventIdIsNotAUuid() {
        assertThatIllegalArgumentException().isThrownBy(() -> converter.toDomain(message("not-a-uuid",
                org.cavalodetroia.contracts.pipeline.v1.JobState.JOB_STATE_READY)));
    }

    @Test
    void should_reject_when_occurredAtIsMissing() {
        final JobStatusChanged withoutTimestamp = message(EVENT_ID.toString(),
                org.cavalodetroia.contracts.pipeline.v1.JobState.JOB_STATE_READY).toBuilder().clearOccurredAt().build();

        assertThatIllegalArgumentException().isThrownBy(() -> converter.toDomain(withoutTimestamp));
    }

    private static JobStatusChanged message(final String eventId,
                                            final org.cavalodetroia.contracts.pipeline.v1.JobState state) {
        return JobStatusChanged.newBuilder()
                .setEventId(eventId)
                .setState(state)
                .setOccurredAt(ProtoTimestamps.from(OCCURRED_AT))
                .build();
    }
}

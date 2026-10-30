package org.cavalodetroia.ctr.domain.job;

import org.cavalodetroia.ctr.domain.job.port.out.JobStatusReadModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobStatusServiceTest {

    private static final UUID EVENT_ID = UUID.fromString("0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11");
    private static final Instant T1 = Instant.parse("2026-10-30T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-30T10:05:00Z");

    @Mock
    private JobStatusReadModel readModel;

    private JobStatusService service;

    @BeforeEach
    void setUp() {
        service = new JobStatusService(readModel);
    }

    @Test
    void should_saveStatus_when_noneExistsYet() {
        final JobStatus status = new JobStatus(EVENT_ID, JobState.QUEUED, T1);
        when(readModel.findByEventId(EVENT_ID)).thenReturn(Optional.empty());

        service.project(status);

        verify(readModel).save(status);
    }

    @Test
    void should_replaceStatus_when_newerThanCurrent() {
        final JobStatus newer = new JobStatus(EVENT_ID, JobState.PROCESSING, T2);
        when(readModel.findByEventId(EVENT_ID)).thenReturn(Optional.of(new JobStatus(EVENT_ID, JobState.QUEUED, T1)));

        service.project(newer);

        verify(readModel).save(newer);
    }

    @Test
    void should_ignoreStatus_when_olderThanCurrent() {
        final JobStatus older = new JobStatus(EVENT_ID, JobState.QUEUED, T1);
        when(readModel.findByEventId(EVENT_ID)).thenReturn(Optional.of(new JobStatus(EVENT_ID, JobState.READY, T2)));

        service.project(older);

        verify(readModel, never()).save(any());
    }

    @Test
    void should_returnStatus_when_itExists() {
        final JobStatus status = new JobStatus(EVENT_ID, JobState.READY, T2);
        when(readModel.findByEventId(EVENT_ID)).thenReturn(Optional.of(status));

        assertThat(service.byEventId(EVENT_ID)).isEqualTo(status);
    }

    @Test
    void should_throwNotFound_when_noStatusForEvent() {
        when(readModel.findByEventId(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.byEventId(EVENT_ID))
                .isInstanceOf(JobStatusNotFoundException.class)
                .hasMessageContaining(EVENT_ID.toString());
    }
}

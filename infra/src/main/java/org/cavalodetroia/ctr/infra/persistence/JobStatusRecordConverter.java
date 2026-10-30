package org.cavalodetroia.ctr.infra.persistence;

import org.cavalodetroia.ctr.domain.job.JobState;
import org.cavalodetroia.ctr.domain.job.JobStatus;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.AnalysisJobViewRecord;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

@Component
public class JobStatusRecordConverter {

    public AnalysisJobViewRecord toRecord(final JobStatus status) {
        return new AnalysisJobViewRecord(status.eventId(), status.state().name(), status.updatedAt().atOffset(ZoneOffset.UTC));
    }

    public JobStatus toDomain(final AnalysisJobViewRecord record) {
        return new JobStatus(record.getEventId(), JobState.valueOf(record.getState()), record.getUpdatedAt().toInstant());
    }
}

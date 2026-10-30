package org.cavalodetroia.ctr.infra.persistence;

import org.cavalodetroia.ctr.domain.job.JobStatus;
import org.cavalodetroia.ctr.domain.job.port.out.JobStatusReadModel;
import org.cavalodetroia.ctr.infra.persistence.jooq.tables.records.AnalysisJobViewRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import static org.cavalodetroia.ctr.infra.persistence.jooq.Tables.ANALYSIS_JOB_VIEW;

@Repository
public class JooqJobStatusReadModel implements JobStatusReadModel {

    private final DSLContext dsl;
    private final JobStatusRecordConverter converter;

    public JooqJobStatusReadModel(final DSLContext dsl, final JobStatusRecordConverter converter) {
        this.dsl = dsl;
        this.converter = converter;
    }

    @Override
    public Optional<JobStatus> findByEventId(final UUID eventId) {
        return dsl.selectFrom(ANALYSIS_JOB_VIEW)
                .where(ANALYSIS_JOB_VIEW.EVENT_ID.eq(eventId))
                .fetchOptional(converter::toDomain);
    }

    @Override
    public void save(final JobStatus status) {
        final AnalysisJobViewRecord record = converter.toRecord(status);
        dsl.insertInto(ANALYSIS_JOB_VIEW)
                .set(record)
                .onConflict(ANALYSIS_JOB_VIEW.EVENT_ID)
                .doUpdate()
                .set(record)
                .execute();
    }
}

package org.cavalodetroia.ctr.domain.job;

import org.cavalodetroia.ctr.domain.job.port.in.GetJobStatus;
import org.cavalodetroia.ctr.domain.job.port.in.ProjectJobStatus;
import org.cavalodetroia.ctr.domain.job.port.out.JobStatusReadModel;

import java.util.UUID;

public class JobStatusService implements GetJobStatus, ProjectJobStatus {

    private final JobStatusReadModel readModel;

    public JobStatusService(final JobStatusReadModel readModel) {
        this.readModel = readModel;
    }

    @Override
    public JobStatus byEventId(final UUID eventId) {
        return readModel.findByEventId(eventId).orElseThrow(() -> new JobStatusNotFoundException(eventId));
    }

    /**
     * Messages can arrive late or be replayed, so a status only replaces an older one.
     */
    @Override
    public void project(final JobStatus status) {
        final boolean isNewer = readModel.findByEventId(status.eventId())
                .map(status::isNewerThan)
                .orElse(true);

        if (isNewer) {
            readModel.save(status);
        }
    }
}

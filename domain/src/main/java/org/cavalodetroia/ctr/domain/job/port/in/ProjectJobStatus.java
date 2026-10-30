package org.cavalodetroia.ctr.domain.job.port.in;

import org.cavalodetroia.ctr.domain.job.JobStatus;

public interface ProjectJobStatus {

    void project(JobStatus status);
}

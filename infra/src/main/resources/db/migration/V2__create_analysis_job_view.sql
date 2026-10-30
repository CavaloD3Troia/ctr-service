-- Read projection of pipeline.job.status.v1: the latest status of each event's processing job
CREATE TABLE analysis_job_view (
    event_id   UUID                     PRIMARY KEY,
    state      VARCHAR(20)              NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

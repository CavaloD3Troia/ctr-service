--liquibase formatted sql

--changeset ctr:0002-create-upload-tables
CREATE TABLE upload_session (
    id             UUID                     PRIMARY KEY,
    event_id       UUID                     NOT NULL REFERENCES event (id),
    status         TEXT                     NOT NULL,
    venue_format   TEXT                     NOT NULL,
    venue_length_m REAL                     NOT NULL,
    venue_width_m  REAL                     NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT upload_session_status_check CHECK (status IN ('OPEN', 'COMPLETED')),
    CONSTRAINT upload_session_venue_format_check
        CHECK (venue_format IN ('FUTSAL_5V5', 'FOOTBALL_7V7', 'FOOTBALL_11V11'))
);

-- An event has at most one open session; completed ones don't count
CREATE UNIQUE INDEX upload_session_one_open_per_event ON upload_session (event_id) WHERE status = 'OPEN';

CREATE TABLE upload_camera (
    session_id    UUID                     NOT NULL REFERENCES upload_session (id),
    camera        VARCHAR                  NOT NULL,
    part_count    INT                      NOT NULL,
    prefix        TEXT                     NOT NULL,
    registered_by TEXT,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (session_id, camera)
);

CREATE TABLE upload_clip (
    session_id  UUID                     NOT NULL REFERENCES upload_session (id),
    camera      VARCHAR                  NOT NULL,
    part_index  INT                      NOT NULL,
    object_key  TEXT                     NOT NULL,
    status      TEXT                     NOT NULL,
    size_bytes  BIGINT,
    etag        TEXT,
    start_ms    BIGINT,
    duration_ms BIGINT,
    uploaded_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (session_id, camera, part_index),
    CONSTRAINT upload_clip_status_check CHECK (status IN ('PENDING', 'UPLOADED'))
);

-- camera is TEXT like the other string columns. It starts as VARCHAR because jOOQ generates the classes by running
-- this file on H2, which reads TEXT as a CLOB and cannot put a CLOB in a primary key; only Postgres runs the change.
-- [jooq ignore start]
ALTER TABLE upload_camera ALTER COLUMN camera TYPE TEXT;
ALTER TABLE upload_clip ALTER COLUMN camera TYPE TEXT;
-- [jooq ignore stop]

-- After the type change, so the constraint compares TEXT values
ALTER TABLE upload_camera ADD CONSTRAINT upload_camera_camera_check CHECK (camera IN ('SINGLE', 'LEFT', 'RIGHT'));

CREATE TABLE upload_part (
    session_id   UUID                     NOT NULL REFERENCES upload_session (id),
    part_index   INT                      NOT NULL,
    status       TEXT                     NOT NULL,
    start_ms     BIGINT,
    duration_ms  BIGINT,
    published_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (session_id, part_index),
    CONSTRAINT upload_part_status_check CHECK (status IN ('WAITING', 'COMPLETE', 'NO_OVERLAP'))
);

--liquibase formatted sql

--changeset ctr:0003-create-idempotency-key
-- The response given to each POST that carried an Idempotency-Key, so a retry with the same key and body gets it
-- back instead of doing the work twice. Rows older than 24 hours are ignored; purging them is a later change.
CREATE TABLE idempotency_key (
    key             VARCHAR                  PRIMARY KEY,
    request_hash    TEXT                     NOT NULL,
    response_status INT                      NOT NULL,
    response_body   TEXT                     NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

-- TEXT like the other string columns; VARCHAR first because H2, which jOOQ generates the classes with, cannot put
-- its CLOB (TEXT) in a primary key. Only Postgres runs the change (see 0002-create-upload-tables.sql).
-- [jooq ignore start]
ALTER TABLE idempotency_key ALTER COLUMN key TYPE TEXT;
-- [jooq ignore stop]

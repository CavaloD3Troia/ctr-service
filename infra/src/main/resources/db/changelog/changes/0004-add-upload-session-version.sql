--liquibase formatted sql

--changeset ctr:0004-add-upload-session-version
-- Optimistic lock: a camera that joins is only stored over the version of the session it read, so two cameras joining
-- at once can't both pass the camera set and part count checks. 0 when opened, plus one per camera that joins.
ALTER TABLE upload_session ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

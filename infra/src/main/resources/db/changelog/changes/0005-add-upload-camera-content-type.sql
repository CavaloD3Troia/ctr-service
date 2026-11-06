--liquibase formatted sql

--changeset ctr:0005-add-upload-camera-content-type
-- The media type the camera registered its clips with. The registration has always validated it (a video/* type) but
-- never stored it; the messages of TH-152 carry it as Clip.content_type, so it is kept from now on. Rows registered
-- before get video/mp4, the only type cameras send today.
ALTER TABLE upload_camera ADD COLUMN content_type TEXT NOT NULL DEFAULT 'video/mp4';

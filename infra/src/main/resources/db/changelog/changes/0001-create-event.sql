--liquibase formatted sql

--changeset ctr:0001-create-event
CREATE TABLE event (
    id   UUID         PRIMARY KEY,
    name VARCHAR(200) NOT NULL
);

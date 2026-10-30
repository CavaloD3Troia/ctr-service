CREATE TABLE event (
    id         UUID                     PRIMARY KEY,
    name       VARCHAR(200)             NOT NULL,
    sport      VARCHAR(50)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

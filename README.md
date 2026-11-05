# ctr-service

Control Tower: the only backend the UI (gui-service) talks to.

Right now it has two endpoints of the clip upload flow (ADR-0001, second series): a camera registers on an
event's upload session and is told where in the bucket to upload its clips, and the app reads the session back to
show where the upload stands. See [API](#api).

## Architecture

Hexagonal, with one Maven module per layer. Dependencies point inward, and the domain knows nothing about HTTP,
Kafka or SQL.

| Module | Contents |
|---|---|
| `domain` | `model/upload/UploadSession` (the registration rules and the state of each part) and its values, `service/UploadSessionService`, and the ports: `port/in/RegisterCamera` and `GetUploadSession` (called by api) and `port/out/EventRepository`, `UploadSessionRepository` and `IdempotencyStore` (implemented by infra). Depends on the JDK only. |
| `api` | `controller/UploadSessionController`, the request and response records in `domain/`, `converter/`, `error/ApiExceptionHandler` (problem+json), `filter/CorrelationIdFilter` and `idempotency/IdempotentRequests` |
| `infra` | `repository/` (jOOQ adapters), `converter/` (domain ↔ jOOQ records), the Liquibase changelog in `resources/db/changelog/`, and `kafka/serialization/` (the protobuf serializer for the publishers to come) |
| `application` | `CtrApplication`, `config/StorageProperties`, `wiring/UploadWiring` (builds the domain service), and `application.yml` |
| `component-tests` | Cucumber against the whole app, with Postgres and Kafka in Testcontainers |

Inside each module the first package level says what kind of class lives there, the second the concept (see
`CLAUDE.md`). `ArchitectureTest` in `application` breaks the build if a layer reaches where it shouldn't, or a
class in `controller`/`converter` is not named for it.

## API

The OpenAPI description is served at `/v3/api-docs`.

### `POST /api/v1/events/{eventId}/upload-session/cameras`

A camera registers to upload an event's clips. The first camera of the event opens its upload session and sets the
venue; a second one joins it. A session ends with the cameras `SINGLE`, or `LEFT` and `RIGHT`, all with the same
`partCount`.

```jsonc
// Idempotency-Key: 9b1d6f4e-2c1a-4f7e-9a51-0c7d3e8b2f10
{
  "camera": "LEFT",                 // SINGLE | LEFT | RIGHT
  "partCount": 40,                  // 1..1000, the same for every camera of the session
  "contentType": "video/mp4",       // a video/* type
  "venue": {                        // required on the first camera, ignored (and logged) on later ones
    "format": "FOOTBALL_11V11",     // FUTSAL_5V5 | FOOTBALL_7V7 | FOOTBALL_11V11, checked on the first camera only
    "lengthM": 105,                 // both measures or neither; neither stores the standard of the format:
    "widthM": 68                    // 40 x 20, 65 x 45, 105 x 68 m
  }
}

// 201 Created
{
  "sessionId": "019a5b2e-7f3c-7000-8000-000000000001",
  "eventId": "0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11",
  "status": "OPEN",
  "camera": "LEFT",
  "partCount": 40,
  "prefix": "s3://cdt-videos/events/0b5c3c38-…/sessions/019a5b2e-…/LEFT/",
  "clipKey": "{partIndex}.mp4",     // 0-based: 0.mp4 to 39.mp4
  "cameras": [{ "camera": "LEFT", "partsUploaded": 0, "partCount": 40 }]
}
```

It writes the session (when new), the camera, one `PENDING` row in `upload_clip` per part of this camera, and, with
the first camera only, one `WAITING` row in `upload_part` per part.

Cameras may register at the same time. Two first cameras can't both open a session
(`upload_session_one_open_per_event`), and a camera only joins over the `version` of the session it read. The one
that loses reads the session again and registers against it, so the event still gets one session, each camera once,
and its parts once.

| Header | |
|---|---|
| `Idempotency-Key` (optional, up to 255 characters) | The same key and body within 24 hours return the first `201` again without writing anything. The same key with another body is `422`. |
| `X-Correlation-Id` (optional) | Echoed back on every response and put on every log line of the request (`correlation_id`). When absent, or not 1 to 128 of `A-Z a-z 0-9 . _ : -`, the service makes one: `CTR-<uuidv7>`. |

Errors are `application/problem+json` with a `code`:

| Status | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | A field breaks a rule above, or the first camera sends no venue; `errors` lists `{field, message}`. The body is checked before the event, except the venue format, which is checked when the first camera opens the session. |
| 404 | `EVENT_NOT_FOUND` | ctr-service does not know the event |
| 409 | `CAMERA_SET_CONFLICT` | The camera is already registered (with another `Idempotency-Key`), or doesn't fit: `SINGLE` with `LEFT`/`RIGHT` or the reverse |
| 409 | `PART_COUNT_MISMATCH` | The second camera brings another `partCount` |
| 422 | `IDEMPOTENCY_KEY_REUSED` | The `Idempotency-Key` came with a different request |
| 500 | `INTERNAL_ERROR` | Only the `correlationId`, to find the logs |

### `GET /api/v1/upload-sessions/{sessionId}`

Where the upload stands: the session and its venue, each registered camera with how many clips it has uploaded, and
every part with one clip per registered camera. Read from `upload_session`, `upload_camera`, `upload_clip` and
`upload_part` in four queries, however many parts there are; it writes nothing and calls no storage.

```jsonc
// 200 OK
{
  "sessionId": "019a5b2e-7f3c-7000-8000-000000000001",
  "eventId": "0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11",
  "status": "OPEN",
  "partCount": 40,
  "partsPublished": 2,              // parts with published_at set
  "venue": { "format": "FOOTBALL_11V11", "lengthM": 105.0, "widthM": 68.0 },
  "cameras": [                      // the registered cameras, LEFT before RIGHT
    { "camera": "LEFT", "partCount": 40, "partsUploaded": 3, "prefix": "s3://cdt-videos/events/0b5c3c38-…/sessions/019a5b2e-…/LEFT/" },
    { "camera": "RIGHT", "partCount": 40, "partsUploaded": 2, "prefix": "s3://cdt-videos/events/0b5c3c38-…/sessions/019a5b2e-…/RIGHT/" }
  ],
  "parts": [                        // every index from 0 to partCount - 1, in order
    {
      "partIndex": 2, "status": "COMPLETE", "published": true, "startMs": 600000, "durationMs": 298000,
      "clips": [
        { "camera": "LEFT", "status": "UPLOADED", "sizeBytes": 1073741824, "startMs": 600000, "durationMs": 300000, "uploadedAt": "2026-11-04T18:02:11Z" },
        { "camera": "RIGHT", "status": "UPLOADED", "sizeBytes": 1052000000, "startMs": 598000, "durationMs": 300000, "uploadedAt": "2026-11-04T18:03:40Z" }
      ]
    },
    {
      "partIndex": 3, "status": "WAITING_RIGHT", "published": false, "startMs": null, "durationMs": null,
      "clips": [
        { "camera": "LEFT", "status": "UPLOADED", "sizeBytes": 1073741824, "startMs": 900000, "durationMs": 300000, "uploadedAt": "2026-11-04T18:07:02Z" },
        { "camera": "RIGHT", "status": "PENDING" }
      ]
    }
  ]
}
```

A part's `status`:

| `status` | When |
|---|---|
| `WAITING_LEFT` | Two cameras, and the LEFT clip is not uploaded. Also when neither is: LEFT is named first. |
| `WAITING_RIGHT` | Two cameras, and the RIGHT clip is not uploaded, or RIGHT has not registered yet |
| `WAITING` | A single camera, and its clip is not uploaded |
| `COMPLETE` | Every clip is uploaded and they overlap; `startMs` and `durationMs` are the shared window |
| `NO_OVERLAP` | Every clip is uploaded but they don't overlap, so the part has no window |

A part always has every field: `startMs` and `durationMs` are `null` until it is `COMPLETE`, and stay `null` for
`NO_OVERLAP`. `published` mirrors `upload_part.published_at`. A clip leaves out what it doesn't know: a `PENDING` clip
is only `camera` and `status`. A session with only LEFT registered lists one clip per part.

| Status | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | The `sessionId` is not a UUID; `errors` names it |
| 404 | `SESSION_NOT_FOUND` | No upload session has this id |
| 500 | `INTERNAL_ERROR` | Only the `correlationId`, to find the logs |

## Logs

One JSON object per line, with `service`, `level`, `message`, `logger_name` and the MDC: `correlation_id` for every
line of a request, plus `session_id` and `camera` once the request knows them.

## Running it locally

You need Java 25 and Docker (Docker Desktop, Colima or OrbStack). You also need the contracts jar: see
[Contracts](#contracts).

**To debug in IntelliJ**, start only Postgres and Kafka:

```sh
docker compose up -d        # or: make deps-up, which also waits until they are healthy
```

Then run the shared **CtrApplication** run configuration (from `.run/`). `application.yml` already points at
`localhost:5432` and `localhost:9092`.

**To run it all in Docker**, use:

```sh
make up      # builds the jar, then starts Postgres, Kafka and the app on :8080
make logs
make down    # `make reset` also deletes the database
```

Events have no endpoint yet (ADR-0002), so add one by hand, then register a camera on it:

```sh
docker compose exec postgres psql -U ctr -d ctr \
  -c "insert into event (id, name) values ('0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11', 'Benfica vs Porto')"
curl -i localhost:8080/api/v1/events/0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11/upload-session/cameras \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" \
  -d '{"camera":"LEFT","partCount":40,"contentType":"video/mp4","venue":{"format":"FOOTBALL_11V11"}}'
curl -i localhost:8080/api/v1/upload-sessions/<sessionId from the response>
```

`make help` lists every target. The app reads these environment variables, which default to the compose setup:

| Variable | Default | |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/ctr`, `ctr`, `ctr` | Postgres |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka |
| `CTR_STORAGE_BUCKET` | `cdt-videos` | The S3 bucket cameras upload their clips to (`ctr.storage.bucket`); it makes the `prefix` of the response |

## Tests

| Command | Runs |
|---|---|
| `make build` | Unit tests in every module, plus `ArchitectureTest` |
| `make component-test` | All of that, plus the features in `component-tests/src/test/resources/features` |

`register_camera.feature` registers cameras over HTTP and checks the response, the rows in Postgres and the log lines
of the request (LEFT then RIGHT, SINGLE alone, each 409, the replayed `Idempotency-Key`, standard venue dimensions,
a later camera's venue ignored whatever it holds, 404, 400, and the correlation id). `concurrent_registration.feature`
sends two cameras of each of 20 events at the same time, or one right after the other, and checks that each event
has one session with each camera once and its parts once. `get_upload_session.feature` writes upload rows straight
into Postgres and reads them over HTTP (a fresh SINGLE session; LEFT and RIGHT with uploaded, pending, complete,
published and non-overlapping parts; an unknown session; 1000 parts), and checks through `support/QueryCounter` that
a read runs at most four statements, all `SELECT`s. `openapi.feature` checks what `/v3/api-docs` says about both
endpoints.
`schema.feature` checks that Liquibase applied every changeset to the empty
database (tables, primary and foreign keys, the one-open-session index), that running the changelog again applies
nothing, and that an event can't have two open upload sessions. After a run, the report is at
`component-tests/target/cucumber-report.html`.

Testcontainers does not read the docker CLI context. `make component-test` handles that for you. When running
`CucumberIT` from IntelliJ with Colima (or anything that isn't at `/var/run/docker.sock`), add this to
`~/.testcontainers.properties`:

```properties
docker.host=unix:///Users/<you>/.colima/default/docker.sock
```

## Database schema

Liquibase creates and updates the schema when the service starts. The changelog is
`infra/src/main/resources/db/changelog/db.changelog-master.yaml`; it applies every file in `changes/` in name order,
and records what it ran in the `databasechangelog` table.

| Changeset | Creates |
|---|---|
| `0001-create-event.sql` | `event` |
| `0002-create-upload-tables.sql` | `upload_session` (at most one `OPEN` session per event), `upload_camera`, `upload_clip`, `upload_part` |
| `0003-create-idempotency-key.sql` | `idempotency_key`: the stored response of each request with an `Idempotency-Key` (rows older than 24 hours are ignored) |
| `0004-add-upload-session-version.sql` | `upload_session.version`: 0 when opened, plus one per camera that joins; a join is only stored over the version it read |

To change the schema, add a changeset:

1. Create the next file in `infra/src/main/resources/db/changelog/changes/`, numbered after the last one:
   `0005-<what>.sql`.
2. Start it with the Liquibase header, then a `--changeset` line before each change:
   ```sql
   --liquibase formatted sql

   --changeset ctr:0005-add-event-kickoff
   ALTER TABLE event ADD COLUMN kickoff_at TIMESTAMP WITH TIME ZONE;
   ```
3. Run `make build`. jOOQ generates its classes from these files (no database needed), so the new tables and
   columns show up in `Tables.*`. If the jOOQ parser rejects a Postgres-only statement, put just that statement
   between `-- [jooq ignore start]` and `-- [jooq ignore stop]`; Liquibase still runs it.
4. Run `make component-test`, which applies the whole changelog to an empty Postgres, and add the new changeset to
   the list in `schema.feature`.

**Never edit a changeset once it is merged.** Liquibase stores a checksum of every changeset it ran and refuses to
start when one of those files has changed. To fix or undo a change, add a new changeset.

A local database created before the switch from Flyway has no Liquibase history, so the first changeset fails on the
existing `event` table. Recreate it with `make reset` (it deletes the local data), then start again.

## Contracts

The Kafka messages live in [GlobalProtobufModels](https://github.com/CavaloD3Troia/GlobalProtobufModels), published as
`org.cavalodetroia:global-protobuf-models` on GitHub Packages. GitHub Packages needs a token even for reads.
Create a classic PAT with the `read:packages` scope and add it to `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USER</username>
      <password>YOUR_PAT</password>
    </server>
  </servers>
</settings>
```

Until the first version is tagged, build the contracts locally instead: clone GlobalProtobufModels and run
`make install`.

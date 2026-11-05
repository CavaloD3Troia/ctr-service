# ctr-service

Control Tower: the only backend the UI (gui-service) talks to.

Right now it has the first two endpoints of the clip upload flow (ADR-0001, second series): a camera registers on an
event's upload session and is told where in the bucket to upload its clips, then confirms each clip it uploaded.
See [API](#api).

## Architecture

Hexagonal, with one Maven module per layer. Dependencies point inward, and the domain knows nothing about HTTP,
Kafka or SQL.

| Module | Contents |
|---|---|
| `domain` | `model/upload/UploadSession` (the registration and clip confirmation rules) and its values (`Window` holds the overlap rule), `service/UploadSessionService`, and the ports: `port/in/RegisterCamera` and `CompleteClip` (called by api) and `port/out/EventRepository`, `UploadSessionRepository`, `IdempotencyStore` and `ClipStorage` (implemented by infra). Depends on the JDK only. |
| `api` | `controller/UploadSessionController`, the request and response records in `domain/`, `converter/`, `error/ApiExceptionHandler` (problem+json), `filter/CorrelationIdFilter` and `idempotency/IdempotentRequests` |
| `infra` | `repository/` (jOOQ adapters), `converter/` (domain ↔ jOOQ records), `storage/` (`S3ClipStorage`, the S3 `HEAD` behind `ClipStorage`, and `S3ClientFactory`), the Liquibase changelog in `resources/db/changelog/`, and `kafka/serialization/` (the protobuf serializer for the publishers to come) |
| `application` | `CtrApplication`, `config/StorageProperties`, `wiring/UploadWiring` (builds the domain service and the S3 client), and `application.yml` |
| `component-tests` | Cucumber against the whole app, with Postgres, Kafka and MinIO in Testcontainers |

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

### `POST /api/v1/upload-sessions/{sessionId}/cameras/{camera}/parts/{partIndex}/complete`

A camera confirms clip `partIndex` (0-based) after uploading it to `prefix` + `{partIndex}.mp4`. It sends what S3
answered to its `PUT` and where the clip sits in the recording, which only the camera knows because clips have
variable length:

```jsonc
// POST /api/v1/upload-sessions/019a5b2e-…/cameras/LEFT/parts/3/complete
{
  "sizeBytes": 1073741824,          // positive, the object's size
  "etag": "9b2cf535f27731c9…",      // not blank, the ETag of the PUT, with or without its double quotes
  "startMs": 900000,                // zero or more, where the clip starts in the recording
  "durationMs": 300000              // positive, how long the clip is
}

// 200 OK
{
  "camera": "LEFT",
  "partIndex": 3,
  "status": "UPLOADED",
  "part": { "status": "WAITING_RIGHT", "published": false },
  "session": { "status": "OPEN", "partsPublished": 0, "partCount": 40 }
}
```

ctr-service asks S3 for the object's size and etag with a `HEAD` on
`events/{eventId}/sessions/{sessionId}/{CAMERA}/{partIndex}.mp4`. It never downloads a clip; the `HEAD` is the only
S3 call it makes. Only when the object is there with that size and etag is anything written, in one transaction:

- the clip in `upload_clip` becomes `UPLOADED`, with `size_bytes`, `etag`, `start_ms`, `duration_ms` and
  `uploaded_at`;
- the part in `upload_part` closes when every camera has its clip `partIndex` uploaded. For `SINGLE` its window is the
  clip's own; for `LEFT` and `RIGHT` it is the overlap of the two clips: `start_ms` the later of the two starts,
  `duration_ms` the earlier of the two ends minus that start. The part is `COMPLETE`, or `NO_OVERLAP` (with a `WARN`
  log line) when that duration is 0 or less, and then nothing will be published for it;
- the session in `upload_session` becomes `COMPLETED` when every clip of every camera is uploaded.

`part.status` is `WAITING_LEFT` or `WAITING_RIGHT` while the other camera's clip is missing, then `COMPLETE` or
`NO_OVERLAP`. `published` and `partsPublished` count the parts with `upload_part.published_at` set; nothing publishes
yet, so they stay `false` and `0`.

Rules worth knowing:

- **Expected cameras.** A part waits for `SINGLE` when `SINGLE` is registered, otherwise for `LEFT` and `RIGHT`, even
  while only one of them has registered. So a session with `LEFT` only answers `WAITING_RIGHT` and completes neither a
  part nor itself until `RIGHT` registers and uploads.
- **Etags** are compared as strings once the surrounding double quotes are stripped on both sides, so a multipart
  etag such as `abc-3` is just a string. The size is checked first: an object with both wrong is `SIZE_MISMATCH`.
- **Repeats.** The same body again for an uploaded clip returns the same `200`, built from what is stored, without
  asking S3 or writing anything. Another body for an uploaded clip is `409`. There is no `Idempotency-Key` here: the
  body is the idempotency rule.
- **A completed session is closed.** It is no longer the event's open session, so a camera registering on the same
  event afterwards opens a new session.
- Confirmations of one session run one after the other (the session row is locked for the transaction), so
  `LEFT 3` and `RIGHT 3` arriving together close part 3 once.

| Status | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | The body breaks a rule above, the camera is not registered on the session, or `partIndex` is outside `0 … partCount-1`; `errors` lists `{field, message}` |
| 404 | `SESSION_NOT_FOUND` | ctr-service does not know the session |
| 409 | `CLIP_ALREADY_COMPLETED` | The clip is already uploaded with a different body |
| 422 | `OBJECT_NOT_FOUND`, `SIZE_MISMATCH`, `ETAG_MISMATCH` | The `HEAD` disagrees with the body: no object, another size (`detail` has both), another etag. Nothing is written |
| 503 | `STORAGE_UNAVAILABLE` | S3 is unreachable, too slow (the client gives up after 5 s) or refuses ctr-service's credentials (an `ERROR` log names the bucket). Comes with `Retry-After: 5`. Nothing is written |
| 500 | `INTERNAL_ERROR` | Only the `correlationId`, to find the logs |

`X-Correlation-Id` works as for the registration.

## Logs

One JSON object per line, with `service`, `level`, `message`, `logger_name` and the MDC: `correlation_id` for every
line of a request, plus `session_id`, `camera` and `part_index` once the request knows them.

## Running it locally

You need Java 25 and Docker (Docker Desktop, Colima or OrbStack). You also need the contracts jar: see
[Contracts](#contracts).

**To debug in IntelliJ**, start only Postgres, Kafka and MinIO:

```sh
docker compose up -d        # or: make deps-up, which also waits until they are healthy
```

A one-shot `minio-init` container creates the `cdt-videos` bucket and exits. Then run the shared **CtrApplication**
run configuration (from `.run/`). `application.yml` already points at `localhost:5432`, `localhost:9092` and MinIO on
`localhost:9000` (console on http://localhost:9001, `minioadmin` / `minioadmin`).

**To run it all in Docker**, use:

```sh
make up      # builds the jar, then starts Postgres, Kafka, MinIO and the app on :8080
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
```

To confirm a clip, upload one to the key of the response and read back its size and etag, then confirm it with them
(fill in the ids):

```sh
KEY=events/<eventId>/sessions/<sessionId>/LEFT/0.mp4
docker compose exec minio bash -c "mc alias set local http://localhost:9000 minioadmin minioadmin \
  && head -c 1048576 /dev/urandom > /tmp/clip && mc cp /tmp/clip local/cdt-videos/$KEY && mc stat local/cdt-videos/$KEY"
curl -i localhost:8080/api/v1/upload-sessions/<sessionId>/cameras/LEFT/parts/0/complete \
  -H 'Content-Type: application/json' \
  -d '{"sizeBytes":1048576,"etag":"<ETag from mc stat>","startMs":0,"durationMs":300000}'
```

`make help` lists every target. The app reads these environment variables, which default to the compose setup:

| Variable | Default | |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/ctr`, `ctr`, `ctr` | Postgres |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka |
| `CTR_STORAGE_BUCKET` | `cdt-videos` | The S3 bucket cameras upload their clips to (`ctr.storage.bucket`); it makes the `prefix` of the response |
| `S3_ENDPOINT` | `http://localhost:9000` | Where S3 is (`ctr.storage.endpoint`): MinIO locally. Set it to an empty value on AWS, which then finds the endpoint from the region |
| `S3_REGION` | `us-east-1` | The bucket's region (`ctr.storage.region`) |
| `S3_ACCESS_KEY`, `S3_SECRET_KEY` | `minioadmin`, `minioadmin` | Credentials for the bucket (`ctr.storage.access-key`, `ctr.storage.secret-key`), the local MinIO's by default. ctr-service needs `s3:GetObject` on the clips, for the `HEAD`, and `s3:ListBucket` on the bucket, without which S3 answers a missing clip with 403 (a `503`) instead of 404 (a `422 OBJECT_NOT_FOUND`) |

The S3 client uses path-style URLs and gives up after 5 s: two attempts of at most 3 s each, a 2 s connection timeout
and a 3 s socket timeout (`S3ClientFactory`). That is why a storage outage answers `503` instead of hanging.

## Tests

| Command | Runs |
|---|---|
| `make build` | Unit tests in every module, plus `ArchitectureTest` |
| `make component-test` | All of that, plus the features in `component-tests/src/test/resources/features` |

`complete_clip.feature` registers cameras over HTTP, puts their clips into the MinIO container and confirms them,
then checks the answer, the rows in Postgres and the log lines: an object present, absent, with another size or
another etag; a repeat with the same body (after deleting the object, so a `HEAD` would fail) and with another; MinIO
paused, which hangs every connection, answering `503` within 6 s; the `SINGLE` flow; `LEFT` then `RIGHT` with the
overlap; clips that don't overlap; the last clip completing the session; 404 and 400; and `LEFT` and `RIGHT`
confirming the same part at the same time on 10 sessions, closing it once.
`register_camera.feature` registers cameras over HTTP and checks the response, the rows in Postgres and the log lines
of the request (LEFT then RIGHT, SINGLE alone, each 409, the replayed `Idempotency-Key`, standard venue dimensions,
a later camera's venue ignored whatever it holds, 404, 400, and the correlation id). `concurrent_registration.feature`
sends two cameras of each of 20 events at the same time, or one right after the other, and checks that each event
has one session with each camera once and its parts once. `openapi.feature` checks what `/v3/api-docs` says about both
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

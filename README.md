# ctr-service

Control Tower: the only backend the UI (gui-service) talks to.

Right now it is the simplest slice that goes through every layer:

```
POST /api/v1/events {"name": "Benfica vs Porto"}
  → saved in Postgres (table `event`)
  → published as EventCreated (protobuf) on ctr.event.created.v1
  → consumed back by the service, which logs the payload
```

## Architecture

Hexagonal, with one Maven module per layer. Dependencies point inward, and the domain knows nothing about HTTP,
Kafka or SQL.

| Module | Contents |
|---|---|
| `domain` | `Event`, `EventService`, and the ports: `port/in/CreateEvent` (called by api) and `port/out/EventRepository` and `EventPublisher` (implemented by infra). Depends on the JDK only. |
| `api` | `EventController`, the request and response records, and `EventConverter` (domain → response) |
| `infra` | `persistence/` (jOOQ), the Liquibase changelog in `resources/db/changelog/`, and `kafka/` (publisher, listener, protobuf serde and converter) |
| `application` | `CtrApplication`, `wiring/EventWiring` (builds the domain service), and `application.yml` |
| `component-tests` | Cucumber against the whole app, with Postgres and Kafka in Testcontainers |

`ArchitectureTest` in `application` breaks the build if a layer reaches where it shouldn't.

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

Then create an event and watch the listener log it:

```sh
curl -i localhost:8080/api/v1/events -H 'Content-Type: application/json' -d '{"name":"Benfica vs Porto"}'
```

`make help` lists every target. The app reads `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and
`KAFKA_BOOTSTRAP_SERVERS`, which default to the compose setup.

## Tests

| Command | Runs |
|---|---|
| `make build` | Unit tests in every module, plus `ArchitectureTest` |
| `make component-test` | All of that, plus the features in `component-tests/src/test/resources/features` |

`events.feature` creates an event over HTTP, then checks three things: the row in Postgres, the message on the
topic, and the listener's log line. `schema.feature` checks that Liquibase applied every changeset to the empty
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

To change the schema, add a changeset:

1. Create the next file in `infra/src/main/resources/db/changelog/changes/`, numbered after the last one:
   `0003-<what>.sql`.
2. Start it with the Liquibase header, then a `--changeset` line before each change:
   ```sql
   --liquibase formatted sql

   --changeset ctr:0003-add-event-kickoff
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

`EventCreated` lives in [GlobalProtobufModels](https://github.com/CavaloD3Troia/GlobalProtobufModels), published as
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

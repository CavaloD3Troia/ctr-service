# ctr-service

Control Tower: the only backend the UI (gui-service) talks to.

Right now it is a thin slice that goes through every layer:

| | |
|---|---|
| `POST /api/v1/events` | Creates an event (`name`, `sport`), stores it, and publishes `EventCreated` on `ctr.event.lifecycle.v1` |
| `GET /api/v1/events/{eventId}` | Returns the event |
| `GET /api/v1/events/{eventId}/analysis` | Returns the latest analysis status reported by the pipeline on `pipeline.job.status.v1` |

Errors follow [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) and carry a stable `code` (`VALIDATION_FAILED`,
`EVENT_NOT_FOUND`, `JOB_STATUS_NOT_FOUND`, `INTERNAL`). While the app runs, the OpenAPI docs are at
<http://localhost:8080/swagger-ui.html>.

## Architecture

Hexagonal, with one Maven module per layer. Dependencies point inward, and the domain knows nothing about HTTP, Kafka or SQL.

```
             ┌──────────┐                       ┌──────────┐
  HTTP ────▶ │   api    │ ──▶ ┌──────────┐ ◀── │  infra   │ ◀──▶ Postgres, Kafka
             └──────────┘     │  domain  │      └──────────┘
                              └──────────┘
                                    ▲
                             ┌─────────────┐
                             │ application │  starts the app and wires everything
                             └─────────────┘
```

| Module | Owns | Depends on |
|---|---|---|
| `domain` | Models, domain services, and the ports: `port/in` (use cases the api calls) and `port/out` (what infra implements) | The JDK only |
| `api` | Controllers, request/response DTOs, converters to and from the domain, the error handler, the correlation-id filter | `domain` |
| `infra` | jOOQ repositories, Kafka publisher and listener, protobuf converters, Flyway migrations | `domain`, the contracts |
| `application` | `CtrApplication`, the wiring of the domain services (`wiring/`), `application.yml` | everything |
| `component-tests` | Cucumber scenarios against the whole app, with Postgres and Kafka in Testcontainers | `application` |

`ArchitectureTest` in `application` breaks the build if a layer reaches where it shouldn't.

Domain services have no Spring annotations. `application/wiring` builds them by hand, so the domain stays plain Java.

## Running it locally

You need Java 25 and Docker (Docker Desktop, Colima or OrbStack). You also need the contracts jar: see
[Contracts](#contracts).

**To debug in IntelliJ**, start only the dependencies:

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

```sh
curl -i localhost:8080/api/v1/events -H 'Content-Type: application/json' -d '{"name":"Benfica vs Porto","sport":"football"}'
```

`make help` lists every target.

### Configuration

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/ctr` |
| `DB_USERNAME` / `DB_PASSWORD` | `ctr` / `ctr` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |

## Tests

| Command | Runs |
|---|---|
| `make build` | Unit tests in every module, plus `ArchitectureTest` |
| `make component-test` | All of that, plus the Cucumber features in `component-tests/src/test/resources/features` |

The component tests start the real app on a random port. They talk to it over HTTP, and over Kafka they play
the other services. Each scenario checks both sides of the contract: the HTTP response and the Kafka message.
After a run, the report is at `component-tests/target/cucumber-report.html`.

Testcontainers does not read the docker CLI context. `make component-test` handles that for you. When running
`CucumberIT` from IntelliJ with Colima (or anything that isn't at `/var/run/docker.sock`), add this to
`~/.testcontainers.properties`:

```properties
docker.host=unix:///Users/<you>/.colima/default/docker.sock
```

## Contracts

Kafka messages are protobuf, defined in
[GlobalProtobufModels](https://github.com/CavaloD3Troia/GlobalProtobufModels) and published as the Maven artifact
`org.cavalodetroia:global-protobuf-models` on GitHub Packages.

| Topic | Direction | Key | Message |
|---|---|---|---|
| `ctr.event.lifecycle.v1` | published (compacted) | event id | `cavalodetroia.ctr.v1.EventLifecycle` |
| `pipeline.job.status.v1` | consumed by group `ctr-service.job-status` | event id | `cavalodetroia.pipeline.v1.JobStatusChanged` |

GitHub Packages needs a token even for reads. Create a classic PAT with the `read:packages` scope and add it to
`~/.m2/settings.xml`:

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

Every message carries a `correlationId` header. On the way in, it is taken from the request's `X-Correlation-Id`
header, or generated if there is none, and it shows up in every log line as `[correlationId]`.

## Not in this first slice (on purpose)

These are all in ADR-0001 and will come in later slices:

- a transactional outbox and an inbox for deduplication
- retries and a DLQ
- OpenTelemetry tracing
- JSON logs
- auth
- `Idempotency-Key`
- the full event model
- CI for this repo

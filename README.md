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
| `infra` | `persistence/` (jOOQ + Flyway) and `kafka/` (publisher, listener, protobuf serde and converter) |
| `application` | `CtrApplication`, `wiring/EventWiring` (builds the domain service), `application.yml` and one file per profile |
| `component-tests` | Cucumber against the whole app, with Postgres and Kafka in Testcontainers |

`ArchitectureTest` in `application` breaks the build if a layer reaches where it shouldn't.

## Running it locally

You need Java 25 and Docker (Docker Desktop, Colima or OrbStack). You also need the contracts jar: see
[Contracts](#contracts).

**To debug in IntelliJ**, start only Postgres, Kafka and MinIO:

```sh
docker compose up -d        # or: make deps-up, which also waits until they are healthy
```

Then run the shared **CtrApplication** run configuration (from `.run/`). It activates the `dev` profile, which
already points at `localhost:5432` and `localhost:9092`. MinIO (S3-compatible, nothing uses it yet) serves its API on
`localhost:9000` and its console on `localhost:9001`, user `ctr`, password `ctr-local`.

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

`make help` lists every target.

## Profiles

The app needs an active profile (`SPRING_PROFILES_ACTIVE` or `--spring.profiles.active`); without one it does not
start. Settings shared by every profile are in `application.yml`.

| Profile | Used by | Postgres and Kafka |
|---|---|---|
| `dev` | the IDE run configuration, `make run`, `make up` | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, defaulting to the compose setup |
| `test` | the component tests | supplied by Testcontainers |
| `prod` | deployed environments | the same four variables, **required**: the app stops at startup if one is missing |

Our own settings are typed records bound by prefix:

| Property | Record | Meaning |
|---|---|---|
| `ctr.kafka.topic` | `CtrKafkaProperties` | the topic `EventCreated` is published to and consumed from (`ctr.event.created.v1`) |

## Tests

| Command | Runs |
|---|---|
| `make build` | Unit tests in every module, plus `ArchitectureTest` and the check that `domain` pulls in no Spring, jOOQ or Kafka jar |
| `make integration-test` | All of that, plus the `*IT` tests: `CtrApplicationIT` starts the app with the `dev` profile and checks `/actuator/health` |
| `make component-test` | All of that, plus `component-tests/src/test/resources/features/events.feature` |

The component test creates an event over HTTP, then checks three things: the row in Postgres, the message on the
topic, and the listener's log line. After a run, the report is at `component-tests/target/cucumber-report.html`.

Testcontainers does not read the docker CLI context. `make component-test` handles that for you. When running
`CucumberIT` from IntelliJ with Colima (or anything that isn't at `/var/run/docker.sock`), add this to
`~/.testcontainers.properties`:

```properties
docker.host=unix:///Users/<you>/.colima/default/docker.sock
```

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

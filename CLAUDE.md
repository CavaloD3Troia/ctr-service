# ctr-service — how we build it

Control Tower, the only backend the UI talks to. Spring Boot 4.1, Java 25, Maven, Postgres (jOOQ + Liquibase),
Kafka (protobuf), hexagonal. The platform design is ADR-0001 in `CavaloD3Troia/CavaloDeTroiaDocumentation`.

**Start small.** The code is the first three endpoints of the upload flow and nothing more:
`POST /api/v1/events/{eventId}/upload-session/cameras` (a camera registers) → Postgres,
`GET /api/v1/upload-sessions/{sessionId}` (the app reads where the upload stands) → Postgres, and
`POST /api/v1/upload-sessions/{sessionId}/cameras/{camera}/parts/{partIndex}/complete` (a camera confirms a clip) → an
S3 `HEAD`, then Postgres. Nothing is published to Kafka yet. Build only what
the current feature needs. When a feature does need one of the
patterns below (retries, DLQ, outbox, correlation ids…), implement it **the way this file describes**, not ad hoc.
The [Not built yet](#not-built-yet) section lists what is missing on purpose.

The patterns come from the `kafka-rest-service` blueprint (skill of the same name), which has full code examples.

## Commands

```sh
make build            # clean verify: unit + ArchUnit tests, and the 80% line-coverage gate
make integration-test # + the *IT tests of application (Testcontainers, needs Docker)
make component-test   # + Cucumber against the whole app (Testcontainers, needs Docker)
make deps-up          # Postgres + Kafka + MinIO (with the bucket) only, then run CtrApplication from the IDE (.run/, profile dev)
make up / logs / down # the app in Docker too
```

The contracts jar must be in `~/.m2`: a GitHub Packages `read:packages` token, or `make install` in
`../GlobalProtobufModels` while the version is a SNAPSHOT.

## Architecture

| Module | Owns | Must not |
|---|---|---|
| `domain` | Records, domain services, ports: `port/in` (use cases), `port/out` (what infra implements), domain exceptions | Depend on anything but the JDK: no Spring, no Kafka, no jOOQ, no annotations |
| `api` | Controllers, request/response records, converters, the error handler | Call infra or repositories; hold business rules |
| `infra` | jOOQ repositories, Kafka publishers/listeners, serdes, converters, migrations | Import `api` or `application`; let a driver exception reach the domain |
| `application` | `CtrApplication`, `wiring/` (builds domain services with `new`), `application.yml` plus `application-{dev,test,prod}.yml` | Contain business logic |
| `component-tests` | Cucumber, black box | Reach into internals beyond what a scenario must observe |

- `ArchitectureRulesTest` enforces this with the rules of `HexagonalRules` (both in
  `application/src/test/java/org/cavalodetroia/ctr/application/architecture`). Never weaken a rule to make code
  compile; move the code. The rules:
  - `LAYERS`: dependencies point inward. Nothing uses `application`; `api` and `infra` are used by `application` only.
  - `DOMAIN_DEPENDS_ONLY_ON_THE_JDK`: `domain` references `java..` and itself, nothing else.
  - `NO_CYCLES`: no cycles between the top-level packages, a package that is not one of the four layers included.
  - `API_HAS_NO_PERSISTENCE_OR_MESSAGING_TYPES`: no jOOQ, JDBC, Kafka, protobuf or contract types in `api`.
  - `INFRA_HAS_NO_WEB_TYPES`: no Spring MVC annotations or servlet types in `infra` (Spring's HTTP client is fine).
  - `ENTRY_POINTS_LIVE_IN_THEIR_PACKAGE`: `@RestController` and `@Controller` only in `api/controller`,
    `@RestControllerAdvice` and `@ControllerAdvice` only in `api/error`, `@KafkaListener` only in
    `infra/kafka/listener`.
  - `APPLICATION_HOLDS_NO_ADAPTER_CODE`: no jOOQ, JDBC, Kafka, protobuf or contract types in `application`. It wires
    adapters to ports; it does not adapt.
  - `INFRA_ADAPTERS_ARE_INDEPENDENT`: `infra/repository`, `infra/kafka`, `infra/storage` and `infra/outbox` never
    reference each other. What two adapters share belongs in the domain, behind a port, or in `infra/converter`.
  - `CONTROLLERS_ARE_NAMED_CONTROLLER` and `CONVERTERS_ARE_NAMED_CONVERTER`: a class in a `controller` package ends
    in `Controller`, one in a `converter` package in `Converter`.
- Every rule has a fixture that breaks it under `application/src/test/java/org/cavalodetroia/ctr/archfixtures` (one
  sub-folder per layer, one minimal wrong class per rule, plus a clean resident per layer and per named package).
  `ArchitectureRulesTest` proves each rule rejects its fixture and that no other rule does. A new rule comes with its
  fixture.
- Ports are named for the capability (`UploadSessionRepository`, `IdempotencyStore`), adapters for the technology
  (`JooqUploadSessionRepository`, `JooqIdempotencyStore`).
- Domain services get no annotations; add a `@Bean` per service in `application/wiring/<Feature>Wiring`.
- Packages: kind first, concept second, `org.cavalodetroia.ctr.<module>.<kind>[.<concept>]`. The first level says what
  kind of class lives there; inside the records folder the second level names the concept.
  - `api`: `controller`, `domain/<concept>` (request and response records, e.g. `domain/upload/RegisterCameraRequest`),
    `converter`, `error`, `filter`, `idempotency`.
  - `domain`: `model/<concept>` (entities, value records, enums and their rules, e.g. `model/upload/UploadSession`;
    `model` so the path does not read `domain.domain`), `service`, `port/in`, `port/out`, `exception`, `common`.
  - `infra`: `repository` (jOOQ adapters), `converter`, `kafka/publisher`, `kafka/listener`, `kafka/serialization`,
    `kafka/config`, `storage`, `outbox`, `domain/<concept>` for infra-only records.
  - `application`: `config` (properties records), `wiring`, `health`, `scheduling`.
  - `component-tests`: `features`, `steps`, `support`.

## Conventions

- Constructor injection only, `final` fields, `record` for DTOs, domain values and properties.
- A converter class at every boundary (API ↔ domain, domain ↔ jOOQ record, domain ↔ proto). Never map inline in
  a controller or listener, and never reuse one layer's type in another (no jOOQ record or proto in the API). The
  domain's enums of the ubiquitous language (`Camera`, `VenueFormat`) are values, not layer types: request and
  response records use them, so the allowed values are defined once. Exception: `VenueRequest.format` is a `String`
  with `@Schema(implementation = VenueFormat.class)`. Only the first camera's format is checked (in
  `RequestedVenue.toSpec`), so a later camera's must not fail at binding.
- Logs are structured: `LOGGER.info("operation=create, message=Event created eventId='{}'", id)`. They go out as
  JSON lines (Spring Boot's `logstash` format, plus `service`), so every MDC entry becomes a field.
- MDC: `CorrelationIdFilter` puts `correlation_id` for the whole request and clears the MDC at the end. Add
  `session_id`, `camera` and `part_index` as soon as the request knows them (the controller knows the camera, the
  repository the session). The domain can't use SLF4J: it logs through the JDK's `System.Logger`, which Spring Boot
  bridges to the same logs and MDC.
- Configuration: one `@ConfigurationProperties` record per concern in `application/config`, bound by prefix
  (`StorageProperties` for `ctr.storage`). No `@Value`.
- Profiles: `application.yml` holds what every profile shares. `application-dev.yml` defaults to `docker-compose.yml`,
  `application-test.yml` leaves the connections to Testcontainers, and `application-prod.yml` reads environment
  variables with no defaults. The app needs an active profile; without one it does not start. A new setting goes into
  the three profile files, and prod never gets a default (`ProfilesTest` checks).

## REST

- Paths are `/api/v1/...`. A breaking response change gets a new version in the path; the old controller stays
  alive and `@Deprecated`.
- A controller only validates, converts, delegates to a `port/in` interface, converts back and picks the status.
- Structural validation (Bean Validation on the request record) at the edge → 400. Semantic rules that need data
  live in the domain.
- Errors are RFC 9457 `application/problem+json`, all from **one** `@RestControllerAdvice`,
  `api/error/ApiExceptionHandler`. A new domain exception gets a handler there: its status, a stable `code`
  (`EVENT_NOT_FOUND`) and the `type` `https://cavalodetroia.dev/problems/<code-in-kebab-case>`. Validation failures
  are `400 VALIDATION_FAILED` with `errors: [{field, message}]`. A 500 carries only the correlation id, never internals.
- Every response has `X-Correlation-Id` (`api/filter/CorrelationIdFilter`): the caller's when it is a safe token,
  otherwise `CTR-<uuidv7>`.
- A `POST` that creates something takes an optional `Idempotency-Key` header and goes through
  `api/idempotency/IdempotentRequests`: same key and body within 24 hours replays the stored response; same key and
  another body is `422 IDEMPOTENCY_KEY_REUSED`.
- Every operation carries springdoc annotations (`@Operation`, an `@ApiResponse` per status with its codes, the
  headers); request and response records get `@Schema` for fields that need explaining. Served at `/v3/api-docs`.
- For a list body, put `@Valid` inside the generic: `List<@Valid Item>`.
- Batch endpoints are best effort: per-element `status`, `200` when all succeed, `207` when mixed, `422` when all
  fail.

## Kafka

**Contracts.** Protobuf from `CavaloD3Troia/GlobalProtobufModels` only; never a local `.proto` or hand-written
serialization. To change a message: a PR there (new field numbers only, `buf breaking` must pass), tag a version,
bump `global-protobuf-models.version` here. Topics are `<producer>.<entity>.<fact>.v<N>`, and the key is the id
of the entity whose ordering matters (`event_id`).

**Publishing**
- The domain calls a port; the adapter only converts and sends.
- Never `.get()` on the send future. Handle it with `whenComplete` and log failures.
- Delivery: `acks=all` and idempotence (the client defaults; keep them). Set `delivery.timeout.ms` explicitly when
  delivery matters. Producer retries are configuration, never a hand-written loop.
- When a second publisher appears, extract an abstract `KafkaPublisher<K,V>` that owns sending, headers and
  logging, so each publisher only converts.
- Serializers pass `null` through: that is a tombstone. If deleting is a domain concept, put it on the port
  (`delete(id)`), not a raw `null` in the domain.
- Saving then publishing is a dual write: a crash in between loses the message. ADR-0001's answer is a
  transactional outbox (the outbox row is written in the same DB transaction, and a relay publishes it). Build that
  before anything depends on not losing a message.

**Consuming**
- `ErrorHandlingDeserializer` always wraps the real deserializer, so a poison message is logged and skipped instead
  of blocking the partition. The deserializer throws a `SerializationException` that names the topic.
- The listener only does three things: (1) correlation id into MDC (header → payload → generated `ctr-service-<uuid>`,
  and `MDC.clear()` in `finally`); (2) a cheap validation; (3) convert and call a domain `port/in`. No business
  logic. There is no listener yet; the first one follows these three steps.
- Today `@KafkaListener` and `spring.kafka.*` are enough. Once a listener needs a filter, its own concurrency, or a
  custom error handler, follow the blueprint instead:
  - a plain class implementing `MessageListener`;
  - a `<Feature>ListenerConfiguration` that wires the consumer factory and container;
  - a properties record for its settings.
- **Retries and DLQ.** The default is Spring Kafka's `DefaultErrorHandler` with no backoff and 9 retries;
  deserialization errors are never retried. When a consumer does real work, configure:
  - a `DefaultErrorHandler` with an exponential backoff;
  - a `DeadLetterPublishingRecoverer` to `<topic>-dlt` (its default name in Spring Kafka 4);
  - `addNotRetryableExceptions(...)` for conversion and validation failures, which fail the same way every time.
- Delivery is at least once, so handlers must be idempotent. ADR-0001 plans an inbox to dedupe consumed messages.
- Tombstones: filter them out with a `RecordFilterStrategy` unless deletes mean something to the domain.
- Several payload types on one topic: one strategy per `oneof` case, chosen by a factory. An unknown case logs a
  warning and moves on, never throws.
- Container concurrency must not exceed the partition count.

## Persistence

- Schema changes are Liquibase changesets in formatted SQL, applied on start. A new change is a new file
  `infra/src/main/resources/db/changelog/changes/NNNN-<what>.sql` (next number), starting with
  `--liquibase formatted sql` and a `--changeset ctr:NNNN-<what>` line; `db.changelog-master.yaml` picks it up with
  `includeAll`. Add it to the changeset list in `schema.feature`.
- **Never edit a changeset once it is merged**: Liquibase checksums every changeset it ran and fails on a mismatch.
  Fixes are new changesets. Make changes additive first: add the column, deploy, backfill, switch reads, and only
  then drop the old one.
- jOOQ classes are generated from those SQL files at build time (`DDLDatabase`, no DB needed). After adding a
  changeset, run `make build` and use the generated `Tables.*`, never table names in strings. A Postgres-only
  statement the jOOQ parser rejects goes between `-- [jooq ignore start]` and `-- [jooq ignore stop]`.
- The generator runs the SQL on H2, which reads `TEXT` as a CLOB and can't index it: a `TEXT` column in a key is
  created as `VARCHAR` and turned into `TEXT` in an ignored `ALTER` (see `0002-create-upload-tables.sql`).
- The port speaks domain types only. The adapter owns the converter and **translates exceptions**: jOOQ/JDBC
  exceptions become domain exceptions (for example, a violation of `upload_session_one_open_per_event` becomes
  `OpenSessionAlreadyExistsException`).
- A write that depends on what was read is guarded by the row's `version`: `UPDATE … WHERE status = 'OPEN' AND
  version = <read>`, and no row updated becomes a domain exception (`UploadSessionChangedException`). The service
  then reads again and decides again (`UploadSessionService`); never lock across port calls.
- Once a converter goes both ways, test the round trip (`toDomain(toRecord(x)).equals(x)`).
- A decision the domain takes over the session as stored, where the next request must see this one's result (a clip
  confirmation closing a part), goes through one port call that takes a function:
  `UploadSessionRepository.update(sessionId, session -> session.completeClip(...))`. The adapter locks the session row
  (`SELECT … FOR UPDATE`) inside one transaction, applies the function and writes only what it changed. Remote calls
  (the S3 `HEAD`) happen before it, outside the transaction.
- **Retries.** Retry only transient failures (connection loss, timeouts), with resilience4j `@Retry`:
  - Limit it to those exceptions with a retry predicate.
  - The fallback logs and **rethrows**. It never returns empty or null, because that turns an outage into "not
    found".
  - Constraint violations and bad SQL are never retried.
- The domain never sees `@Transactional`. A transaction that spans several writes (the outbox, for example) is
  opened in infra or application, around the port calls.

## Object storage (S3)

- The domain sees the bucket through `port/out/ClipStorage` only; `infra/storage/S3ClipStorage` implements it with the
  AWS SDK v2 `S3Client` built by `S3ClientFactory` (path-style, endpoint override for MinIO, credentials and region
  from `StorageProperties`).
- ctr-service never downloads a clip: `HEAD` is the only S3 call. Clients upload and download straight to the bucket.
- Every S3 call is bounded: 5 s for the call, 3 s per attempt, 2 attempts. An SDK failure (timeouts and 403 included)
  becomes `StorageUnavailableException` → `503 STORAGE_UNAVAILABLE` with `Retry-After`; only a 404 means "not there".
- Locally and in component tests S3 is MinIO (Chainguard's image, pinned by digest, the same in `docker-compose.yml`
  and `Containers`). `MinioSupport` plays the camera's side: puts objects, deletes them, pauses the container.

## Testing

| You changed… | Tier | Where | Command |
|---|---|---|---|
| Domain logic, converter, validation, the statements a repository sends | Unit `*Test`: no Spring context, mock ports only (jOOQ's `MockConnection` for a repository, see `infra/.../repository/MockPostgres`) | the module's `src/test` | `make build`, with the architecture rules |
| Startup, repository query, migration, serde, Kafka listener/publisher wiring | Integration `*IT`: Testcontainers or MockMvc | the module's `src/test`; failsafe runs them with `-Pintegration-tests` (bound in `application`; another module binds the plugin the same way) | `make integration-test` |
| Endpoint, request/response shape, outbound message | Component: Cucumber | `component-tests/src/test/resources/features` | `make component-test` |

- Names `should_<expected>_when_<condition>`, AssertJ only, Mockito for ports only. Fixtures are ObjectMother
  builders with sensible defaults.
- Every serializer has a `null` (tombstone) test; every two-way converter has a round-trip test.
- **Coverage gate.** JaCoCo fails `make build` when the unit tests cover less than **80% of the lines** of `domain`,
  `api` or `infra` (per module, `BUNDLE`/`LINE`, configured in the root `pom.xml` and bound in each of the three).
  Reports: `<module>/target/site/jacoco/index.html`. Excluded: the generated jOOQ classes and `CtrApplication`,
  nothing else; `application` (covered by `CtrApplicationIT`) and `component-tests` are not gated. Never lower the
  threshold or widen the exclusions to pass: add the unit test. A run that selects no tests in a module
  (`-Dtest=...`) writes no coverage data and the check has nothing to measure there; only `make build` is the gate.
- A component scenario asserts **both** sides: the HTTP response and what landed in Postgres or Kafka. Log lines
  and their MDC are read through `support/LogCapture`.
- Component-test flakes to avoid:
  - Await asynchronous effects with Awaitility; never `sleep`.
  - Before a step that depends on consumed state, wait until the service has consumed the message.
  - Keep scenario state in the step class or a `@ScenarioScope` bean, and use `pollInSameThread()` when the
    assertion touches one.
- Containers are created once, in `component-tests/.../config/Containers` (and in each `*IT`), with the same images as
  `docker-compose.yml`. Keep them in step.

## Adding a field end to end

1. GlobalProtobufModels: a new field number, then release.
2. `domain` record.
3. Liquibase changeset, `make build` (jOOQ regenerates), record converter.
4. The Kafka converter.
5. The API request/response record, its validation, and the converter.
6. Unit tests for each converter.
7. The component scenario asserts the field in the HTTP response **and** in the Kafka message.

## Not built yet

Left out of the first slice on purpose. Each item gets built, following this file, when a feature needs it:

- the transactional outbox (publishing) and the inbox with deduplication (consuming)
- consumer retries with backoff, and a DLQ
- persistence retries (resilience4j)
- the correlation id as a Kafka header (the HTTP filter and the MDC are built)
- the explicit `MessageListener` wiring
- metrics: `@Timed` on controllers, listeners and repositories, plus `KafkaClientMetrics`
- OpenTelemetry tracing
- auth
- purging `idempotency_key` rows older than 24 hours (they are ignored, not deleted)
- CI (it needs package access to fetch the contracts)

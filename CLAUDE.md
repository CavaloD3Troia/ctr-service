# ctr-service — how we build it

Control Tower, the only backend the UI talks to. Spring Boot 4.1, Java 25, Maven, Postgres (jOOQ + Flyway),
Kafka (protobuf), hexagonal. The platform design is ADR-0001 in `CavaloD3Troia/CavaloDeTroiaDocumentation`.

**Start small.** The code is a deliberately thin slice: `POST /api/v1/events` → Postgres → `ctr.event.created.v1`
→ our own listener logs it. Build only what the current feature needs. When a feature does need one of the
patterns below (retries, DLQ, outbox, correlation ids…), implement it **the way this file describes**, not ad hoc.
The [Not built yet](#not-built-yet) section lists what is missing on purpose.

The patterns come from the `kafka-rest-service` blueprint (skill of the same name), which has full code examples.

## Commands

```sh
make build            # clean verify: unit + ArchUnit tests
make integration-test # + *IT tests (Testcontainers, needs Docker)
make component-test   # + Cucumber against the whole app (Testcontainers, needs Docker)
make deps-up          # Postgres + Kafka + MinIO only, then run CtrApplication from the IDE (.run/, profile dev)
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
| `application` | `CtrApplication`, `wiring/` (builds domain services with `new`), `application.yml` | Contain business logic |
| `component-tests` | Cucumber, black box | Reach into internals beyond what a scenario must observe |

- `ArchitectureTest` enforces this. Never weaken a rule to make code compile; move the code.
- Ports are named for the capability (`EventPublisher`), adapters for the technology (`EventCreatedPublisher`,
  `JooqEventRepository`).
- Domain services get no annotations; add a `@Bean` per service in `application/wiring/<Feature>Wiring`.
- Packages: `org.cavalodetroia.ctr.<module>.<feature>`.

## Conventions

- Constructor injection only, `final` fields, `record` for DTOs, domain values and properties.
- A converter class at every boundary (API ↔ domain, domain ↔ jOOQ record, domain ↔ proto). Never map inline in
  a controller or listener, and never reuse one layer's type in another (no jOOQ record or proto in the API).
- Logs are structured: `LOGGER.info("operation=create, message=Event created eventId='{}'", id)`.
- Configuration: one `@Validated` `@ConfigurationProperties` record per concern, bound by prefix and living next to
  the adapter that uses it (`infra/kafka/CtrKafkaProperties` for `ctr.kafka.*`). No `@Value`.
- Profiles: `application.yml` holds what every profile shares. `application-dev.yml` defaults to `docker-compose.yml`,
  `application-test.yml` leaves connections to Testcontainers, and `application-prod.yml` reads environment variables
  with no defaults. The app needs an active profile; without one it does not start.

## REST

- Paths are `/api/v1/...`. A breaking response change gets a new version in the path; the old controller stays
  alive and `@Deprecated`.
- A controller only validates, converts, delegates to a `port/in` interface, converts back and picks the status.
- Structural validation (Bean Validation on the request record) at the edge → 400. Semantic rules that need data
  live in the domain.
- Errors are RFC 9457 ProblemDetail. Today that is Spring's built-in (`spring.mvc.problemdetails.enabled`). When
  the first domain exception appears, add **one** `@RestControllerAdvice` extending `ResponseEntityExceptionHandler`
  that maps each domain exception to its status and adds a stable `code` property. A 500 carries only the
  correlation id, never internals.
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
  logic. Today's `EventCreatedListener` only logs; anything more must go through a domain port.
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

- Schema changes are new Flyway files: `infra/src/main/resources/db/migration/V<n>__<what>.sql`. Never edit a
  migration once it is on `main`. Make changes additive first: add the column, deploy, backfill, switch reads, and
  only then drop the old one.
- jOOQ classes are generated from those migrations at build time (`DDLDatabase`, no DB needed). After adding a
  migration, run `make build` and use the generated `Tables.*`, never table names in strings.
- The port speaks domain types only. The adapter owns the converter and **translates exceptions**: jOOQ/JDBC
  exceptions become domain exceptions (for example, a duplicate key becomes `EventAlreadyExistsException`).
- Once a converter goes both ways, test the round trip (`toDomain(toRecord(x)).equals(x)`).
- **Retries.** Retry only transient failures (connection loss, timeouts), with resilience4j `@Retry`:
  - Limit it to those exceptions with a retry predicate.
  - The fallback logs and **rethrows**. It never returns empty or null, because that turns an outage into "not
    found".
  - Constraint violations and bad SQL are never retried.
- The domain never sees `@Transactional`. A transaction that spans several writes (the outbox, for example) is
  opened in infra or application, around the port calls.

## Testing

| You changed… | Tier | Where |
|---|---|---|
| Domain logic, converter, validation | Unit `*Test`: no Spring context, mock ports only | the module's `src/test` |
| Repository query, migration, serde, Kafka listener/publisher wiring, startup | Integration `*IT`: Testcontainers or MockMvc | the module's `src/test`; failsafe runs them with `-Pintegration-tests` (add the failsafe plugin to the module's pom like `application` does) |
| Endpoint, request/response shape, outbound message | Component: Cucumber | `component-tests/src/test/resources/features` |

- Names `should_<expected>_when_<condition>`, AssertJ only, Mockito for ports only. Fixtures are ObjectMother
  builders with sensible defaults.
- Every serializer has a `null` (tombstone) test; every two-way converter has a round-trip test.
- A component scenario asserts **both** sides: the HTTP response and what landed in Postgres or Kafka.
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
3. Flyway migration, `make build` (jOOQ regenerates), record converter.
4. The Kafka converter.
5. The API request/response record, its validation, and the converter.
6. Unit tests for each converter.
7. The component scenario asserts the field in the HTTP response **and** in the Kafka message.

## Not built yet

Left out of the first slice on purpose. Each item gets built, following this file, when a feature needs it:

- the transactional outbox (publishing) and the inbox with deduplication (consuming)
- consumer retries with backoff, and a DLQ
- persistence retries (resilience4j)
- correlation ids: an HTTP filter, the Kafka header, and the MDC log pattern
- the `@RestControllerAdvice` with stable error `code`s
- the explicit `MessageListener` wiring
- metrics: `@Timed` on controllers, listeners and repositories, plus `KafkaClientMetrics`
- OpenTelemetry tracing and JSON logs
- auth, and `Idempotency-Key` on `POST`
- an S3 adapter (MinIO runs in `docker-compose.yml`, but nothing uses it yet)
- OpenAPI docs
- CI (it needs package access to fetch the contracts)

package org.cavalodetroia.ctr.application;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-3: the app starts with the {@code dev} profile and reports its health over HTTP. Postgres, Kafka and MinIO run
 * in Testcontainers with the images of {@code docker-compose.yml}, the set-up of the component tests; only their
 * addresses and credentials replace the dev defaults.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class CtrApplicationIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine");
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.2");
    // Chainguard's build of MinIO (MinIO no longer publishes images): same server, same command line
    private static final MinIOContainer MINIO = new MinIOContainer(DockerImageName
            .parse("cgr.dev/chainguard/minio@sha256:cee9a8e35885e93f14b3cb32960e5017aff74e2adfb52ba87c65003b0163886b")
            .asCompatibleSubstituteFor("minio/minio"));

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void containers(final DynamicPropertyRegistry registry) {
        Startables.deepStart(POSTGRES, KAFKA, MINIO).join();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("ctr.storage.endpoint", MINIO::getS3URL);
        registry.add("ctr.storage.access-key", MINIO::getUserName);
        registry.add("ctr.storage.secret-key", MINIO::getPassword);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void should_report_UP_when_started_with_the_dev_profile(final String path) {
        final ResponseEntity<String> response = RestClient.create("http://localhost:" + port)
                .get()
                .uri(path)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(JsonMapper.shared().readTree(response.getBody()).path("status").asString()).isEqualTo("UP");
    }
}

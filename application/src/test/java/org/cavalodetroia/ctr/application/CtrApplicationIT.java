package org.cavalodetroia.ctr.application;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-3: the app starts with the {@code dev} profile and reports its health over HTTP. Postgres and Kafka run in
 * Testcontainers with the images from {@code docker-compose.yml}; only their addresses replace the dev defaults.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class CtrApplicationIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine");
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.2");

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void containers(final DynamicPropertyRegistry registry) {
        Startables.deepStart(POSTGRES, KAFKA).join();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
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
        assertThat((String) JsonPath.read(response.getBody(), "$.status")).isEqualTo("UP");
    }
}

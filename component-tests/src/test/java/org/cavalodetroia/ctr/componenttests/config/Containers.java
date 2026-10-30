package org.cavalodetroia.ctr.componenttests.config;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One Postgres and one Kafka for the whole run, started in parallel the first time this class is used.
 * Same images as {@code docker-compose.yml}. Ryuk removes them when the JVM exits.
 */
final class Containers {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.2");

    static {
        Startables.deepStart(POSTGRES, KAFKA).join();
    }

    private Containers() {
    }
}

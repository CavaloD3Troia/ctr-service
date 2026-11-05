package org.cavalodetroia.ctr.componenttests.config;

import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One Postgres, one Kafka and one MinIO for the whole run, started in parallel the first time this class is used.
 * Same images as {@code docker-compose.yml}. Ryuk removes them when the JVM exits.
 */
public final class Containers {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.2");
    // Chainguard's build of MinIO (MinIO no longer publishes images): same server, same command line
    public static final MinIOContainer MINIO = new MinIOContainer(DockerImageName
            .parse("cgr.dev/chainguard/minio@sha256:cee9a8e35885e93f14b3cb32960e5017aff74e2adfb52ba87c65003b0163886b")
            .asCompatibleSubstituteFor("minio/minio"));

    static {
        Startables.deepStart(POSTGRES, KAFKA, MINIO).join();
    }

    private Containers() {
    }
}

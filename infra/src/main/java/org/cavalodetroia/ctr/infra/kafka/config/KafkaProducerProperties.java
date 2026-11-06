package org.cavalodetroia.ctr.infra.kafka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

/**
 * What ctr-service publishes to ({@code ctr.kafka.*}). The topic names are the ADR's, set once in
 * {@code application.yml} for every profile, since the topics are the same everywhere (TH-135); an environment may
 * still override them, for example {@code CTR_KAFKA_TOPICS_CLIP_READY}. Lives in infra, not in application/config:
 * the Kafka adapter and the local topics read it, and application may not hold Kafka types to hand it over.
 *
 * @param topics the topic of each message
 */
@ConfigurationProperties("ctr.kafka")
public record KafkaProducerProperties(Topics topics) {

    public KafkaProducerProperties {
        Objects.requireNonNull(topics, "ctr.kafka.topics must be set");
    }

    /**
     * @param clipPairUploaded where a LEFT and RIGHT part goes as {@code ClipPairUploaded},
     *                         {@code clip-pair-uploaded.v1}
     * @param clipReady        where a SINGLE part goes as {@code ClipReady}, {@code clip-ready.v1}
     */
    public record Topics(String clipPairUploaded, String clipReady) {

        public Topics {
            requireSet("clip-pair-uploaded", clipPairUploaded);
            requireSet("clip-ready", clipReady);
        }

        private static void requireSet(final String name, final String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("ctr.kafka.topics." + name + " must be set");
            }
        }
    }
}

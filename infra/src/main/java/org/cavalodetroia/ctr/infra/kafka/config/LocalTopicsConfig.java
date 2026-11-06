package org.cavalodetroia.ctr.infra.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.List;
import java.util.stream.Stream;

/**
 * The topics ctr-service publishes to, with their {@code .retry} and {@code .dlq} siblings, created on start-up when
 * missing: one partition, one replica. Only in the {@code local} profile, whose Kafka (docker-compose) creates no
 * topic by itself. Everywhere else the topics come from {@code cdtv1-data-dependencies} (TH-135), there is no
 * {@link NewTopic} bean and {@code spring.kafka.admin.auto-create} is off, so {@link KafkaAdmin} never calls the
 * broker.
 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
public class LocalTopicsConfig {

    static final String RETRY_SUFFIX = ".retry";
    static final String DLQ_SUFFIX = ".dlq";

    @Bean
    public KafkaAdmin.NewTopics localTopics(final KafkaProducerProperties properties) {
        return new KafkaAdmin.NewTopics(topics(properties.topics()).toArray(NewTopic[]::new));
    }

    static List<NewTopic> topics(final KafkaProducerProperties.Topics topics) {
        return Stream.of(topics.clipPairUploaded(), topics.clipReady())
                .flatMap(topic -> Stream.of(topic, topic + RETRY_SUFFIX, topic + DLQ_SUFFIX))
                .map(topic -> TopicBuilder.name(topic).partitions(1).replicas(1).build())
                .toList();
    }
}

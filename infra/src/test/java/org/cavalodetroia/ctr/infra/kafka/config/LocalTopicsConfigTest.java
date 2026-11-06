package org.cavalodetroia.ctr.infra.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaAdmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * TH-152 AC-8: the six topics are declared in the {@code local} profile only, one partition and one replica each.
 * The topics a broker ends up with are checked by the component tests.
 */
class LocalTopicsConfigTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(LocalTopicsConfig.class, Properties.class)
            .withPropertyValues(
                    "ctr.kafka.topics.clip-pair-uploaded=clip-pair-uploaded.v1",
                    "ctr.kafka.topics.clip-ready=clip-ready.v1");

    @Test
    void should_declareTheTwoTopicsWithTheirRetryAndDlq_when_theProfileIsLocal() {
        context.withPropertyValues("spring.profiles.active=dev,local").run(started -> {
            assertThat(started).hasSingleBean(KafkaAdmin.NewTopics.class);
            assertThat(LocalTopicsConfig.topics(started.getBean(KafkaProducerProperties.class).topics()))
                        .extracting(NewTopic::name, NewTopic::numPartitions, NewTopic::replicationFactor)
                        .containsExactlyInAnyOrder(
                                tuple("clip-pair-uploaded.v1", 1, (short) 1),
                                tuple("clip-pair-uploaded.v1.retry", 1, (short) 1),
                                tuple("clip-pair-uploaded.v1.dlq", 1, (short) 1),
                                tuple("clip-ready.v1", 1, (short) 1),
                                tuple("clip-ready.v1.retry", 1, (short) 1),
                                tuple("clip-ready.v1.dlq", 1, (short) 1));
        });
    }

    @Test
    void should_declareNoTopic_when_theProfileIsNotLocal() {
        context.withPropertyValues("spring.profiles.active=prod").run(started -> {
            assertThat(started).doesNotHaveBean(KafkaAdmin.NewTopics.class);
            assertThat(started).doesNotHaveBean(NewTopic.class);
        });
    }

    @EnableConfigurationProperties(KafkaProducerProperties.class)
    static class Properties {
    }
}

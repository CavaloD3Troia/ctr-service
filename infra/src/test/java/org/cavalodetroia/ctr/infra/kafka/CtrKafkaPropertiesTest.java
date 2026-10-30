package org.cavalodetroia.ctr.infra.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

// AC-4: Kafka settings are bound to a typed record, not read with @Value
class CtrKafkaPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void should_bind_the_topic_when_ctr_kafka_topic_is_set() {
        runner.withPropertyValues("ctr.kafka.topic=ctr.event.created.v1")
                .run(context -> assertThat(context.getBean(CtrKafkaProperties.class).topic())
                        .isEqualTo("ctr.event.created.v1"));
    }

    @Test
    void should_fail_to_start_when_the_topic_is_missing() {
        runner.run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("ctr.kafka.topic"));
    }

    @Test
    void should_fail_to_start_when_the_topic_is_blank() {
        runner.withPropertyValues("ctr.kafka.topic= ")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("ctr.kafka.topic"));
    }

    @EnableConfigurationProperties(CtrKafkaProperties.class)
    static class PropertiesConfiguration {
    }
}

package org.cavalodetroia.ctr.infra.kafka.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.cavalodetroia.contracts.ctr.v1.EventLifecycle;
import org.cavalodetroia.ctr.domain.event.port.out.EventPublisher;
import org.cavalodetroia.ctr.infra.kafka.KafkaConnectionProperties;
import org.cavalodetroia.ctr.infra.kafka.ProtobufSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

@Configuration
public class EventLifecyclePublisherConfiguration {

    /**
     * Compacted: Kafka keeps the latest message of every event, so a new consumer can rebuild them all.
     */
    @Bean
    public NewTopic eventLifecycleTopic(final EventLifecyclePublisherProperties properties) {
        return TopicBuilder.name(properties.topic()).compact().build();
    }

    @Bean
    public ProducerFactory<String, EventLifecycle> eventLifecycleProducerFactory(
            final KafkaConnectionProperties connection) {
        final Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, connection.bootstrapServers(),
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config, new StringSerializer(), new ProtobufSerializer<>());
    }

    @Bean
    public KafkaTemplate<String, EventLifecycle> eventLifecycleKafkaTemplate(
            final ProducerFactory<String, EventLifecycle> eventLifecycleProducerFactory) {
        return new KafkaTemplate<>(eventLifecycleProducerFactory);
    }

    /**
     * Exposed as the domain port: nothing outside infra knows it is Kafka.
     */
    @Bean
    public EventPublisher eventPublisher(final KafkaTemplate<String, EventLifecycle> eventLifecycleKafkaTemplate,
                                         final EventLifecyclePublisherProperties properties) {
        return new EventLifecycleKafkaPublisher(eventLifecycleKafkaTemplate, properties.topic(),
                new EventLifecycleConverter());
    }
}

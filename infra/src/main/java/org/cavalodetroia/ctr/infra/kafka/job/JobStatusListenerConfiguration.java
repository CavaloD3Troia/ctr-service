package org.cavalodetroia.ctr.infra.kafka.job;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.cavalodetroia.contracts.pipeline.v1.JobStatusChanged;
import org.cavalodetroia.ctr.domain.job.port.in.ProjectJobStatus;
import org.cavalodetroia.ctr.infra.kafka.KafkaConnectionProperties;
import org.cavalodetroia.ctr.infra.kafka.ProtobufDeserializer;
import org.cavalodetroia.ctr.infra.kafka.TombstoneFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.adapter.FilteringMessageListenerAdapter;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

@Configuration
public class JobStatusListenerConfiguration {

    @Bean
    public ConsumerFactory<String, JobStatusChanged> jobStatusConsumerFactory(
            final KafkaConnectionProperties connection, final JobStatusListenerProperties properties) {
        final Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, connection.bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, properties.groupId(),
                // A new group reads the topic from the start, so the projection can always be rebuilt
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // A message that cannot be parsed goes to the error handler instead of stopping the consumer
        return new DefaultKafkaConsumerFactory<>(config,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(new ProtobufDeserializer<>(JobStatusChanged.parser())));
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, JobStatusChanged> jobStatusListenerContainer(
            final ConsumerFactory<String, JobStatusChanged> jobStatusConsumerFactory,
            final JobStatusListenerProperties properties,
            final ProjectJobStatus projectJobStatus) {

        final ContainerProperties containerProperties = new ContainerProperties(properties.topic());
        containerProperties.setMessageListener(new FilteringMessageListenerAdapter<>(
                new JobStatusListener(projectJobStatus, new JobStatusConverter()), new TombstoneFilter<>()));

        final var container = new ConcurrentMessageListenerContainer<>(jobStatusConsumerFactory, containerProperties);
        container.setConcurrency(properties.concurrency());
        // Two retries, then the record is logged and skipped. A dead letter topic comes later.
        container.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1_000L, 2L)));
        return container;
    }
}

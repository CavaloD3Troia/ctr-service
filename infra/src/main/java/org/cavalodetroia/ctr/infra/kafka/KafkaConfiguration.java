package org.cavalodetroia.ctr.infra.kafka;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.Map;

@Configuration
public class KafkaConfiguration {

    /**
     * Creates the topics this service owns (every {@code NewTopic} bean) at startup.
     */
    @Bean
    public KafkaAdmin kafkaAdmin(final KafkaConnectionProperties connection) {
        return new KafkaAdmin(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, connection.bootstrapServers()));
    }
}

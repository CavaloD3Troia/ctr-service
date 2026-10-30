package org.cavalodetroia.ctr.infra.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ctr.kafka")
public record KafkaConnectionProperties(String bootstrapServers) {
}

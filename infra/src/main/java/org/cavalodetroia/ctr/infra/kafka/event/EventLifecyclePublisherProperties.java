package org.cavalodetroia.ctr.infra.kafka.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ctr.kafka.publisher.event-lifecycle")
public record EventLifecyclePublisherProperties(String topic) {
}

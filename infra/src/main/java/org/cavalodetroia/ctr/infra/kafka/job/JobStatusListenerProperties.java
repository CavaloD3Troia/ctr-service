package org.cavalodetroia.ctr.infra.kafka.job;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param concurrency consumer threads; more than the topic's partition count just sit idle
 */
@ConfigurationProperties(prefix = "ctr.kafka.listener.job-status")
public record JobStatusListenerProperties(String topic, String groupId, int concurrency) {
}

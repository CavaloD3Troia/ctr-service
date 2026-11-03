package org.cavalodetroia.ctr.infra.kafka;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Our own Kafka settings, bound from {@code ctr.kafka.*}. Connection and serde settings stay under
 * {@code spring.kafka.*}. The app refuses to start when a setting is missing.
 *
 * @param topic where {@link EventCreatedPublisher} publishes and {@link EventCreatedListener} consumes
 *              ({@code ctr.kafka.topic})
 */
@Validated
@ConfigurationProperties("ctr.kafka")
public record CtrKafkaProperties(@NotBlank String topic) {
}

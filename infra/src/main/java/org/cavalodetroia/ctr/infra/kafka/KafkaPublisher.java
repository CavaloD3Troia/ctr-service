package org.cavalodetroia.ctr.infra.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Sending, headers and logging, written once. Each publisher only converts its domain object and calls
 * {@link #publish}.
 */
public abstract class KafkaPublisher<K, V> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaPublisher.class);

    private final KafkaTemplate<K, V> template;
    private final String topic;

    protected KafkaPublisher(final KafkaTemplate<K, V> template, final String topic) {
        this.template = template;
        this.topic = topic;
    }

    protected void publish(final K key, final V message) {
        final ProducerRecord<K, V> record = new ProducerRecord<>(topic, null, key, message, CorrelationId.fromMdc());

        // Never block the caller on the broker: the outcome is logged when it arrives
        template.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                LOGGER.error("operation=publish, message=Send failed topic='{}' key='{}'", topic, key, ex);
            } else {
                LOGGER.debug("operation=publish, message=Sent topic='{}' key='{}' partition={} offset={}",
                        topic, key, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }
}

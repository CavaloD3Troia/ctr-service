package org.cavalodetroia.ctr.archfixtures.application;

import org.springframework.kafka.annotation.KafkaListener;

/** AC6: a Kafka listener outside infra. */
public final class ListenerInApplication {

    @KafkaListener(topics = "ctr.event.created.v1")
    void onMessage(final String message) {
    }
}

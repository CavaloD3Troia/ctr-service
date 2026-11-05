package org.cavalodetroia.ctr.archfixtures.infra.kafka.publisher;

import org.springframework.kafka.annotation.KafkaListener;

/** ENTRY_POINTS_LIVE_IN_THEIR_PACKAGE: a Kafka listener outside infra/kafka/listener. */
public final class ListenerAmongPublishers {

    @KafkaListener(topics = "ctr.upload-session.completed.v1")
    void onMessage(final String message) {
    }
}

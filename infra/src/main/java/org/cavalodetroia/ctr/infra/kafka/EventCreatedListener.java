package org.cavalodetroia.ctr.infra.kafka;

import com.google.protobuf.TextFormat;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.cavalodetroia.contracts.ctr.v1.EventCreated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads back what {@link EventCreatedPublisher} sends, and only logs it.
 */
@Component
public class EventCreatedListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventCreatedListener.class);

    @KafkaListener(topics = "${ctr.kafka.topic}")
    public void onEventCreated(final ConsumerRecord<String, EventCreated> record) {
        LOGGER.info("operation=consume, message=Event created key='{}' partition={} offset={} payload={{}}",
                record.key(), record.partition(), record.offset(),
                TextFormat.printer().emittingSingleLine(true).printToString(record.value()));
    }
}

package org.cavalodetroia.ctr.infra.kafka.event;

import org.cavalodetroia.contracts.ctr.v1.EventLifecycle;
import org.cavalodetroia.ctr.domain.event.Event;
import org.cavalodetroia.ctr.domain.event.port.out.EventPublisher;
import org.cavalodetroia.ctr.infra.kafka.KafkaPublisher;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Keyed by event id: every message about one event lands on the same partition, in order.
 */
public class EventLifecycleKafkaPublisher extends KafkaPublisher<String, EventLifecycle> implements EventPublisher {

    private final EventLifecycleConverter converter;

    public EventLifecycleKafkaPublisher(final KafkaTemplate<String, EventLifecycle> template,
                                        final String topic,
                                        final EventLifecycleConverter converter) {
        super(template, topic);
        this.converter = converter;
    }

    @Override
    public void eventCreated(final Event event) {
        publish(event.id().toString(), converter.toEventCreated(event));
    }
}

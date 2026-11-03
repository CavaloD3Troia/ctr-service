package org.cavalodetroia.ctr.infra.kafka;

import org.cavalodetroia.contracts.ctr.v1.EventCreated;
import org.cavalodetroia.ctr.domain.event.Event;
import org.cavalodetroia.ctr.domain.event.port.out.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventCreatedPublisher implements EventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventCreatedPublisher.class);

    private final KafkaTemplate<String, EventCreated> template;
    private final EventCreatedConverter converter;
    private final String topic;

    public EventCreatedPublisher(final KafkaTemplate<String, EventCreated> template,
                                 final EventCreatedConverter converter,
                                 final CtrKafkaProperties properties) {
        this.template = template;
        this.converter = converter;
        this.topic = properties.topic();
    }

    @Override
    public void publish(final Event event) {
        final String key = event.id().toString();

        // Never block the request on the broker: the outcome is logged when it arrives
        template.send(topic, key, converter.toMessage(event)).whenComplete((result, ex) -> {
            if (ex != null) {
                LOGGER.error("operation=publish, message=Send failed topic='{}' key='{}'", topic, key, ex);
            }
        });
    }
}

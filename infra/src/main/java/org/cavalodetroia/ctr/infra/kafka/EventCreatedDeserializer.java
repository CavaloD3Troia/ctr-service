package org.cavalodetroia.ctr.infra.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.cavalodetroia.contracts.ctr.v1.EventCreated;

/**
 * Wrapped in {@code ErrorHandlingDeserializer} (see {@code application.yml}), so a message that is not an
 * {@link EventCreated} is logged and skipped instead of stopping the consumer.
 */
public class EventCreatedDeserializer implements Deserializer<EventCreated> {

    @Override
    public EventCreated deserialize(final String topic, final byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            return EventCreated.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new SerializationException("Cannot parse an EventCreated from topic " + topic, e);
        }
    }
}

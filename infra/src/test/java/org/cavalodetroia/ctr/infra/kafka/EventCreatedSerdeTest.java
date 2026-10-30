package org.cavalodetroia.ctr.infra.kafka;

import org.apache.kafka.common.errors.SerializationException;
import org.cavalodetroia.contracts.ctr.v1.EventCreated;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventCreatedSerdeTest {

    private static final String TOPIC = "ctr.event.created.v1";

    private final ProtobufSerializer<EventCreated> serializer = new ProtobufSerializer<>();
    private final EventCreatedDeserializer deserializer = new EventCreatedDeserializer();

    @Test
    void should_roundTripMessage_when_serialized() {
        final EventCreated message = EventCreated.newBuilder()
                .setEventId("0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11")
                .setName("Benfica vs Porto")
                .build();

        assertThat(deserializer.deserialize(TOPIC, serializer.serialize(TOPIC, message))).isEqualTo(message);
    }

    @Test
    void should_throwSerializationException_when_bytesAreNotProtobuf() {
        assertThatThrownBy(() -> deserializer.deserialize(TOPIC, new byte[]{(byte) 0xFF, 0x00}))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining(TOPIC);
    }
}

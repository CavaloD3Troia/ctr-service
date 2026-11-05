package org.cavalodetroia.ctr.infra.kafka.serialization;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProtobufSerializerTest {

    private static final String TOPIC = "ctr.any.topic.v1";

    private final ProtobufSerializer<StringValue> serializer = new ProtobufSerializer<>();

    @Test
    void should_writeTheProtobufBytes_when_serializingAMessage() throws InvalidProtocolBufferException {
        final StringValue message = StringValue.of("Benfica vs Porto");

        assertThat(StringValue.parseFrom(serializer.serialize(TOPIC, message))).isEqualTo(message);
    }

    @Test
    void should_returnNull_when_serializingATombstone() {
        assertThat(serializer.serialize(TOPIC, null)).isNull();
    }
}

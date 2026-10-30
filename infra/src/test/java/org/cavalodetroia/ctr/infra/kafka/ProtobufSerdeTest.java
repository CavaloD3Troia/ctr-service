package org.cavalodetroia.ctr.infra.kafka;

import org.apache.kafka.common.errors.SerializationException;
import org.cavalodetroia.contracts.pipeline.v1.JobState;
import org.cavalodetroia.contracts.pipeline.v1.JobStatusChanged;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtobufSerdeTest {

    private static final String TOPIC = "pipeline.job.status.v1";

    private final ProtobufSerializer<JobStatusChanged> serializer = new ProtobufSerializer<>();
    private final ProtobufDeserializer<JobStatusChanged> deserializer =
            new ProtobufDeserializer<>(JobStatusChanged.parser());

    @Test
    void should_roundTripMessage_when_serialized() {
        final JobStatusChanged message = JobStatusChanged.newBuilder()
                .setEventId("0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11")
                .setState(JobState.JOB_STATE_PROCESSING)
                .setOccurredAt(ProtoTimestamps.from(Instant.parse("2026-10-30T10:15:30Z")))
                .build();

        assertThat(deserializer.deserialize(TOPIC, serializer.serialize(TOPIC, message))).isEqualTo(message);
    }

    @Test
    void should_passNullThrough_when_tombstone() {
        assertThat(serializer.serialize(TOPIC, null)).isNull();
        assertThat(deserializer.deserialize(TOPIC, null)).isNull();
    }

    @Test
    void should_throwSerializationException_when_bytesAreNotProtobuf() {
        assertThatThrownBy(() -> deserializer.deserialize(TOPIC, new byte[]{(byte) 0xFF, 0x00}))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining(TOPIC);
    }
}

package org.cavalodetroia.ctr.infra.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.cavalodetroia.contracts.ctr.v1.EventCreated;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class EventCreatedListenerTest {

    private final EventCreatedListener listener = new EventCreatedListener();

    @Test
    void should_logKeyPositionAndPayload_when_messageArrives(final CapturedOutput output) {
        final EventCreated message = EventCreated.newBuilder()
                .setEventId("0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11")
                .setName("Benfica vs Porto")
                .build();

        listener.onEventCreated(new ConsumerRecord<>(
                "ctr.event.created.v1", 2, 7L, "0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11", message));

        assertThat(output).contains(
                "operation=consume",
                "key='0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11'",
                "partition=2",
                "offset=7",
                "name: \"Benfica vs Porto\"");
    }
}

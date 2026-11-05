package org.cavalodetroia.ctr.infra.kafka;

import org.apache.kafka.common.KafkaException;
import org.cavalodetroia.contracts.ctr.v1.EventCreated;
import org.cavalodetroia.ctr.domain.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class EventCreatedPublisherTest {

    private static final String TOPIC = "ctr.event.created.v1";

    @Mock
    private KafkaTemplate<String, EventCreated> template;

    private EventCreatedPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new EventCreatedPublisher(template, new EventCreatedConverter(), new CtrKafkaProperties(TOPIC));
    }

    @Test
    void should_sendTheConvertedMessageKeyedByEventId_when_publishing() {
        final Event event = new Event(UUID.randomUUID(), "Benfica vs Porto");
        when(template.send(anyString(), anyString(), any(EventCreated.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(event);

        verify(template).send(TOPIC, event.id().toString(), EventCreated.newBuilder()
                .setEventId(event.id().toString())
                .setName("Benfica vs Porto")
                .build());
    }

    @Test
    void should_logTheFailureWithoutThrowing_when_theSendFails(final CapturedOutput output) {
        final Event event = new Event(UUID.randomUUID(), "Benfica vs Porto");
        when(template.send(anyString(), anyString(), any(EventCreated.class)))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("broker unavailable")));

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();

        assertThat(output).contains(
                "operation=publish",
                "Send failed",
                "topic='" + TOPIC + "'",
                "key='" + event.id() + "'",
                "broker unavailable");
    }
}

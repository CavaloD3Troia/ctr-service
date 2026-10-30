package org.cavalodetroia.ctr.componenttests.support;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Plays the services around ctr-service: reads what it publishes and sends what the pipeline would.
 * The topic names are written out on purpose. They are part of the contract, so renaming one should
 * break these tests.
 */
public class KafkaTestClient {

    public static final String EVENT_LIFECYCLE_TOPIC = "ctr.event.lifecycle.v1";
    public static final String JOB_STATUS_TOPIC = "pipeline.job.status.v1";

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final String bootstrapServers;

    public KafkaTestClient(@Value("${ctr.kafka.bootstrap-servers}") final String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    /**
     * The first message with this key, reading every partition of the topic from the beginning.
     */
    public ConsumerRecord<String, byte[]> awaitMessage(final String topic, final String key) {
        try (var consumer = new KafkaConsumer<>(
                Map.<String, Object>of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers),
                new StringDeserializer(),
                new ByteArrayDeserializer())) {
            var partitions = consumer.partitionsFor(topic, TIMEOUT).stream()
                    .map(partition -> new TopicPartition(topic, partition.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);

            var deadline = Instant.now().plus(TIMEOUT);
            while (Instant.now().isBefore(deadline)) {
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("No message with key '" + key + "' on " + topic + " within " + TIMEOUT);
    }

    public void send(final String topic, final String key, final byte[] value) {
        try (var producer = new KafkaProducer<>(
                Map.<String, Object>of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers),
                new StringSerializer(),
                new ByteArraySerializer())) {
            producer.send(new ProducerRecord<>(topic, key, value)).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending to " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Could not send to " + topic, e);
        }
    }
}

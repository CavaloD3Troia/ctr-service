package org.cavalodetroia.ctr.componenttests.support;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.cavalodetroia.ctr.componenttests.config.Containers;
import org.testcontainers.DockerClientFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Plays the platform's side of the Kafka container: creates the topics ctr-service publishes to (as
 * cdtv1-data-dependencies does), lists them, reads what lands on them, and makes the broker stop answering. Its own
 * clients, so the app's producer is not involved.
 */
public final class KafkaSupport {

    public static final String CLIP_PAIR_UPLOADED = "clip-pair-uploaded.v1";
    public static final String CLIP_READY = "clip-ready.v1";

    private static Admin admin;

    private KafkaSupport() {
    }

    /**
     * Creates the topics, one partition each, unless an earlier feature of the run did.
     */
    public static void createTopics(final String... names) {
        for (final String name : names) {
            try {
                admin().createTopics(List.of(new NewTopic(name, 1, (short) 1))).all().get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof TopicExistsException)) {
                    throw new IllegalStateException("Could not create the topic " + name, e);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Could not create the topic " + name, e);
            }
        }
    }

    /**
     * @return every topic on the broker but Kafka's internal ones
     */
    public static Set<String> topics() {
        try {
            return admin().listTopics().names().get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Could not list the topics", e);
        }
    }

    /**
     * @return whether the broker answers again: it describes the topics within a second
     */
    public static boolean answers() {
        try {
            admin().describeTopics(List.of(CLIP_PAIR_UPLOADED, CLIP_READY)).allTopicNames().get(1, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Freezes the broker: connections still open, but nothing answers, like a broker that hangs.
     */
    public static void pause() {
        DockerClientFactory.instance().client().pauseContainerCmd(Containers.KAFKA.getContainerId()).exec();
    }

    public static void unpause() {
        DockerClientFactory.instance().client().unpauseContainerCmd(Containers.KAFKA.getContainerId()).exec();
    }

    /**
     * A reader of the topics from their current end: it sees only what is published after it is opened.
     */
    public static Reader readFromNow(final String... topics) {
        return new Reader(topics);
    }

    private static synchronized Admin admin() {
        if (admin == null) {
            admin = Admin.create(Map.of(
                    AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, Containers.KAFKA.getBootstrapServers(),
                    AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000,
                    AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 10_000));
        }
        return admin;
    }

    /**
     * Every record of the topics since it was opened, with the raw value: the step parses it with the contract's
     * message for the topic. No consumer group, the partitions are assigned, so nothing is committed.
     */
    public static final class Reader implements AutoCloseable {

        private final KafkaConsumer<String, byte[]> consumer;
        private final List<ConsumerRecord<String, byte[]>> read = new ArrayList<>();

        private Reader(final String... topics) {
            consumer = new KafkaConsumer<>(Map.of(
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, Containers.KAFKA.getBootstrapServers(),
                    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false),
                    new StringDeserializer(), new ByteArrayDeserializer());
            final List<TopicPartition> partitions = Arrays.stream(topics)
                    .flatMap(topic -> consumer.partitionsFor(topic, Duration.ofSeconds(10)).stream()
                            .map(partition -> new TopicPartition(topic, partition.partition())))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToEnd(partitions);
            // seekToEnd is lazy: asking for the positions fixes them now, before anything is published
            partitions.forEach(partition -> consumer.position(partition, Duration.ofSeconds(10)));
        }

        /**
         * Polls once more, then gives everything read so far, oldest first. Not thread-safe, like the consumer: poll
         * it from the step's thread ({@code pollInSameThread()}).
         */
        public List<ConsumerRecord<String, byte[]>> records() {
            consumer.poll(Duration.ofMillis(200)).forEach(read::add);
            return List.copyOf(read);
        }

        @Override
        public void close() {
            consumer.close(Duration.ofSeconds(2));
        }
    }
}

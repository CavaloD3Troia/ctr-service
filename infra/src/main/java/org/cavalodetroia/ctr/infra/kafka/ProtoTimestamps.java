package org.cavalodetroia.ctr.infra.kafka;

import com.google.protobuf.Timestamp;

import java.time.Instant;

public final class ProtoTimestamps {

    private ProtoTimestamps() {
    }

    public static Timestamp from(final Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    public static Instant toInstant(final Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}

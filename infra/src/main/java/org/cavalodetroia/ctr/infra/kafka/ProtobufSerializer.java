package org.cavalodetroia.ctr.infra.kafka;

import com.google.protobuf.MessageLite;
import org.apache.kafka.common.serialization.Serializer;

public class ProtobufSerializer<T extends MessageLite> implements Serializer<T> {

    @Override
    public byte[] serialize(final String topic, final T data) {
        // null must stay null: that is how a tombstone is published
        return data == null ? null : data.toByteArray();
    }
}

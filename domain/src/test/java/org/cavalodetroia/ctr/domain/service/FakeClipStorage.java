package org.cavalodetroia.ctr.domain.service;

import org.cavalodetroia.ctr.domain.exception.StorageUnavailableException;
import org.cavalodetroia.ctr.domain.model.storage.ObjectMetadata;
import org.cavalodetroia.ctr.domain.port.out.ClipStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A bucket in memory that counts how often it is asked.
 */
final class FakeClipStorage implements ClipStorage {

    private final Map<String, ObjectMetadata> objects = new HashMap<>();
    private boolean unavailable;
    private int heads;

    void put(final String objectKey, final ObjectMetadata metadata) {
        objects.put(objectKey, metadata);
    }

    void becomeUnavailable() {
        unavailable = true;
    }

    int heads() {
        return heads;
    }

    @Override
    public Optional<ObjectMetadata> head(final String objectKey) {
        heads++;
        if (unavailable) {
            throw new StorageUnavailableException("The bucket did not answer", null);
        }
        return Optional.ofNullable(objects.get(objectKey));
    }
}

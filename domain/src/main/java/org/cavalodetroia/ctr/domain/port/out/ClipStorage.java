package org.cavalodetroia.ctr.domain.port.out;

import org.cavalodetroia.ctr.domain.exception.StorageUnavailableException;
import org.cavalodetroia.ctr.domain.model.storage.ObjectMetadata;

import java.util.Optional;

/**
 * The bucket the cameras upload their clips to. ctr-service only ever asks about an object; it never downloads one.
 */
public interface ClipStorage {

    /**
     * Asks for an object's size and etag without its content.
     *
     * @param objectKey the key of the object in the bucket
     * @return its size and etag, or empty when there is no such object
     * @throws StorageUnavailableException when the storage is unreachable, too slow, or refuses ctr-service
     */
    Optional<ObjectMetadata> head(String objectKey);
}

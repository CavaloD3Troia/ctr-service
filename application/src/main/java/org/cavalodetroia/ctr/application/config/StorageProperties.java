package org.cavalodetroia.ctr.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the cameras upload their clips ({@code ctr.storage.*}).
 *
 * @param bucket the S3 bucket, {@code CTR_STORAGE_BUCKET}; {@code cdt-videos} by default
 */
@ConfigurationProperties("ctr.storage")
public record StorageProperties(String bucket) {

    public StorageProperties {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("ctr.storage.bucket must be set");
        }
    }
}

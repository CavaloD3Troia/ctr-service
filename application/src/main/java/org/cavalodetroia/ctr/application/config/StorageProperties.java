package org.cavalodetroia.ctr.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the cameras upload their clips, and how ctr-service reaches it to check them ({@code ctr.storage.*}). The
 * defaults in {@code application-dev.yml} point at the MinIO of {@code docker-compose.yml}; {@code application-prod.yml}
 * has none.
 *
 * @param bucket    the S3 bucket, {@code CTR_STORAGE_BUCKET}; {@code cdt-videos} by default
 * @param endpoint  where S3 is, {@code S3_ENDPOINT}, for example {@code http://localhost:9000} for MinIO; empty for
 *                  AWS, which then finds the endpoint from the region
 * @param region    the region, {@code S3_REGION}
 * @param accessKey the access key id, {@code S3_ACCESS_KEY}
 * @param secretKey the secret access key, {@code S3_SECRET_KEY}
 */
@ConfigurationProperties("ctr.storage")
public record StorageProperties(String bucket, String endpoint, String region, String accessKey, String secretKey) {

    public StorageProperties {
        requireSet("bucket", bucket);
        requireSet("region", region);
        requireSet("access-key", accessKey);
        requireSet("secret-key", secretKey);
    }

    // The secret stays out of the logs and of any startup failure report
    @Override
    public String toString() {
        return "StorageProperties[bucket=" + bucket + ", endpoint=" + endpoint + ", region=" + region + "]";
    }

    private static void requireSet(final String name, final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ctr.storage." + name + " must be set");
        }
    }
}

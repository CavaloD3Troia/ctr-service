package org.cavalodetroia.ctr.infra.storage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;
import java.time.Duration;

/**
 * Builds the {@link S3Client} for AWS or for anything that speaks S3, such as MinIO. The timeouts are short on
 * purpose: a confirmation waits on the HEAD, so an unreachable storage must turn into a quick
 * {@code 503 STORAGE_UNAVAILABLE} rather than a hanging request. Two attempts of at most 3 s each, 5 s in all.
 */
public final class S3ClientFactory {

    static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(2);
    static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(3);
    static final Duration API_CALL_ATTEMPT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(5);
    static final int MAX_ATTEMPTS = 2;

    private S3ClientFactory() {
    }

    /**
     * @param endpoint  where S3 is, for example {@code http://localhost:9000} for MinIO; {@code null} or blank for
     *                  AWS, which then finds the endpoint from the region
     * @param region    the region, for example {@code eu-west-1}
     * @param accessKey the access key id
     * @param secretKey the secret access key
     * @return a client with path-style access, so a bucket name never has to resolve as a host name
     */
    public static S3Client create(final String endpoint,
                                  final String region,
                                  final String accessKey,
                                  final String secretKey) {
        final S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(CONNECTION_TIMEOUT)
                        .socketTimeout(SOCKET_TIMEOUT))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(API_CALL_ATTEMPT_TIMEOUT)
                        .apiCallTimeout(API_CALL_TIMEOUT)
                        .retryStrategy(retry -> retry.maxAttempts(MAX_ATTEMPTS))
                        .build());
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}

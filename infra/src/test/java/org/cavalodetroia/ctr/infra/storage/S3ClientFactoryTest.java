package org.cavalodetroia.ctr.infra.storage;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NFR-2: the client gives up within 5 s, two attempts of 3 s at most, so a storage outage becomes a 503 instead of
 * a hanging request. AC-11: the endpoint, region and path-style access that let it talk to MinIO.
 */
class S3ClientFactoryTest {

    private static final String MINIO = "http://localhost:9000";

    @Test
    void should_giveUpWithinFiveSecondsInTwoAttempts_when_built() {
        try (S3Client s3 = S3ClientFactory.create(MINIO, "us-east-1", "access", "secret")) {
            final ClientOverrideConfiguration overrides = s3.serviceClientConfiguration().overrideConfiguration();

            assertThat(overrides.apiCallTimeout()).contains(Duration.ofSeconds(5));
            assertThat(overrides.apiCallAttemptTimeout()).contains(Duration.ofSeconds(3));
            assertThat(overrides.retryStrategy()).hasValueSatisfying(
                    retry -> assertThat(retry.maxAttempts()).isEqualTo(2));
        }
    }

    @Test
    void should_talkToTheEndpointWithPathStyleUrls_when_anEndpointIsSet() {
        try (S3Client s3 = S3ClientFactory.create(MINIO, "us-east-1", "access", "secret")) {
            assertThat(s3.serviceClientConfiguration().endpointOverride()).contains(URI.create(MINIO));
            assertThat(s3.serviceClientConfiguration().region()).isEqualTo(Region.US_EAST_1);
            assertThat(s3.utilities().getUrl(GetUrlRequest.builder().bucket("cdt-videos").key("a/0.mp4").build()))
                    .hasToString(MINIO + "/cdt-videos/a/0.mp4");
        }
    }

    @Test
    void should_letTheRegionFindTheEndpoint_when_noEndpointIsSet() {
        try (S3Client s3 = S3ClientFactory.create(" ", "eu-west-1", "access", "secret")) {
            assertThat(s3.serviceClientConfiguration().endpointOverride()).isEmpty();
            assertThat(s3.serviceClientConfiguration().region()).isEqualTo(Region.EU_WEST_1);
        }
    }
}

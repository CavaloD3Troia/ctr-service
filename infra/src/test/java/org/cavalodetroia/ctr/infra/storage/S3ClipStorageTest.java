package org.cavalodetroia.ctr.infra.storage;

import org.cavalodetroia.ctr.domain.exception.StorageUnavailableException;
import org.cavalodetroia.ctr.domain.model.storage.ObjectMetadata;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC-4, AC-5, NFR-1: how the answers of S3 {@code HeadObject} map to the port. The client is a hand-written stub
 * that only answers {@code headObject}: any other S3 call fails the test, so a download would too. The real
 * exchange with MinIO is covered by the component tests.
 */
class S3ClipStorageTest {

    private static final String BUCKET = "cdt-videos";
    private static final String KEY = "events/e/sessions/s/LEFT/3.mp4";

    @Test
    void should_giveTheSizeAndTheUnquotedEtag_when_theObjectExists() {
        final StubS3Client s3 = new StubS3Client(HeadObjectResponse.builder()
                .contentLength(1_073_741_824L)
                .eTag("\"9b2cf535f27731c974343645a3985328\"")
                .build());

        final Optional<ObjectMetadata> metadata = new S3ClipStorage(s3, BUCKET).head(KEY);

        assertThat(metadata).contains(new ObjectMetadata(1_073_741_824L, "9b2cf535f27731c974343645a3985328"));
        assertThat(metadata.orElseThrow().etag()).isEqualTo("9b2cf535f27731c974343645a3985328");
        assertThat(s3.requests).singleElement().satisfies(request -> {
            assertThat(request.bucket()).isEqualTo(BUCKET);
            assertThat(request.key()).isEqualTo(KEY);
        });
    }

    @Test
    void should_beEmpty_when_s3SaysNoSuchKey() {
        final StubS3Client s3 = new StubS3Client(NoSuchKeyException.builder().statusCode(404).build());

        assertThat(new S3ClipStorage(s3, BUCKET).head(KEY)).isEmpty();
    }

    @Test
    void should_beEmpty_when_s3AnswersAPlain404() {
        final StubS3Client s3 = new StubS3Client(S3Exception.builder().statusCode(404).build());

        assertThat(new S3ClipStorage(s3, BUCKET).head(KEY)).isEmpty();
    }

    // Wrong credentials are the operator's problem: the client gets a 503, never a 4xx it can't fix
    @Test
    void should_throwStorageUnavailable_when_s3RefusesTheCredentials() {
        final StubS3Client s3 = new StubS3Client(S3Exception.builder().statusCode(403).build());

        assertThatThrownBy(() -> new S3ClipStorage(s3, BUCKET).head(KEY))
                .isInstanceOf(StorageUnavailableException.class)
                .hasMessageContaining("403");
    }

    @Test
    void should_throwStorageUnavailable_when_s3Fails() {
        final StubS3Client s3 = new StubS3Client(S3Exception.builder().statusCode(500).build());

        assertThatThrownBy(() -> new S3ClipStorage(s3, BUCKET).head(KEY))
                .isInstanceOf(StorageUnavailableException.class);
    }

    @Test
    void should_throwStorageUnavailable_when_s3IsUnreachable() {
        final StubS3Client s3 = new StubS3Client(SdkClientException.create("Unable to execute HTTP request"));

        assertThatThrownBy(() -> new S3ClipStorage(s3, BUCKET).head(KEY))
                .isInstanceOf(StorageUnavailableException.class)
                .hasCauseInstanceOf(SdkClientException.class);
    }

    @Test
    void should_throwStorageUnavailable_when_theCallTimesOut() {
        final StubS3Client s3 = new StubS3Client(ApiCallTimeoutException.create(5_000));

        assertThatThrownBy(() -> new S3ClipStorage(s3, BUCKET).head(KEY))
                .isInstanceOf(StorageUnavailableException.class);
    }

    /**
     * Answers {@code headObject} only; every other operation keeps the SDK's default, which throws
     * {@link UnsupportedOperationException}.
     */
    private static final class StubS3Client implements S3Client {

        private final HeadObjectResponse response;
        private final SdkException failure;
        private final List<HeadObjectRequest> requests = new ArrayList<>();

        StubS3Client(final HeadObjectResponse response) {
            this.response = response;
            this.failure = null;
        }

        StubS3Client(final SdkException failure) {
            this.response = null;
            this.failure = failure;
        }

        @Override
        public HeadObjectResponse headObject(final HeadObjectRequest request) {
            requests.add(request);
            if (failure != null) {
                throw failure;
            }
            return response;
        }

        @Override
        public String serviceName() {
            return SERVICE_NAME;
        }

        @Override
        public void close() {
        }
    }
}

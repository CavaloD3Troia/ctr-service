package org.cavalodetroia.ctr.infra.storage;

import org.cavalodetroia.ctr.domain.exception.StorageUnavailableException;
import org.cavalodetroia.ctr.domain.model.storage.ObjectMetadata;
import org.cavalodetroia.ctr.domain.port.out.ClipStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Objects;
import java.util.Optional;

/**
 * The clips' bucket through S3 {@code HeadObject}, the only S3 call ctr-service makes: it never downloads a clip.
 * A missing object is {@link Optional#empty()}; every other failure, the SDK's timeouts included, is
 * {@link StorageUnavailableException}, so no SDK exception reaches the domain.
 */
public class S3ClipStorage implements ClipStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3ClipStorage.class);
    private static final int NOT_FOUND = 404;
    private static final int FORBIDDEN = 403;

    private final S3Client s3;
    private final String bucket;

    public S3ClipStorage(final S3Client s3, final String bucket) {
        this.s3 = s3;
        this.bucket = Objects.requireNonNull(bucket, "bucket");
    }

    @Override
    public Optional<ObjectMetadata> head(final String objectKey) {
        try {
            final HeadObjectResponse head = s3.headObject(request -> request.bucket(bucket).key(objectKey));
            return Optional.of(new ObjectMetadata(head.contentLength(), head.eTag()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            return notFoundOrUnavailable(objectKey, e);
        } catch (SdkException e) {
            LOGGER.warn("operation=headClip, message=Storage unavailable bucket='{}' key='{}' reason='{}'", bucket,
                    objectKey, e.getMessage());
            throw new StorageUnavailableException("The storage did not answer for '" + objectKey + "'", e);
        }
    }

    private Optional<ObjectMetadata> notFoundOrUnavailable(final String objectKey, final S3Exception e) {
        if (e.statusCode() == NOT_FOUND) {
            return Optional.empty();
        }
        if (e.statusCode() == FORBIDDEN) {
            // The credentials or the bucket policy are wrong: an operator's problem, never the client's
            LOGGER.error("operation=headClip, message=Storage refused ctr-service's credentials bucket='{}' key='{}'",
                    bucket, objectKey, e);
        } else {
            LOGGER.warn("operation=headClip, message=Storage failed bucket='{}' key='{}' status={}", bucket,
                    objectKey, e.statusCode());
        }
        throw new StorageUnavailableException("The storage refused the HEAD of '" + objectKey + "' with "
                + e.statusCode(), e);
    }
}

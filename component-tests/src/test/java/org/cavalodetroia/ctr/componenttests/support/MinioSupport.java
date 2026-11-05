package org.cavalodetroia.ctr.componenttests.support;

import org.cavalodetroia.ctr.componenttests.config.Containers;
import org.testcontainers.DockerClientFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

import java.net.URI;

/**
 * Plays the cameras' side of the bucket in the MinIO container: creates the bucket, puts and deletes clips, and
 * makes the storage stop answering. Its own S3 client, so the app's is not involved.
 */
public final class MinioSupport {

    /**
     * The bucket the app uses: its default {@code ctr.storage.bucket}.
     */
    public static final String BUCKET = "cdt-videos";

    private static S3Client s3;

    private MinioSupport() {
    }

    public static void createBucket() {
        try {
            s3().createBucket(request -> request.bucket(BUCKET));
        } catch (BucketAlreadyOwnedByYouException alreadyThere) {
            // Created by an earlier feature of the run
        }
    }

    /**
     * @return the ETag MinIO answered, quoted as S3 sends it, as a camera would forward it
     */
    public static String put(final String objectKey, final byte[] content) {
        return s3().putObject(request -> request.bucket(BUCKET).key(objectKey), RequestBody.fromBytes(content))
                .eTag();
    }

    public static void delete(final String objectKey) {
        s3().deleteObject(request -> request.bucket(BUCKET).key(objectKey));
    }

    /**
     * Freezes the container: connections still open, but nothing answers, like a storage that hangs.
     */
    public static void pause() {
        DockerClientFactory.instance().client().pauseContainerCmd(Containers.MINIO.getContainerId()).exec();
    }

    public static void unpause() {
        DockerClientFactory.instance().client().unpauseContainerCmd(Containers.MINIO.getContainerId()).exec();
    }

    private static synchronized S3Client s3() {
        if (s3 == null) {
            s3 = S3Client.builder()
                    .endpointOverride(URI.create(Containers.MINIO.getS3URL()))
                    .forcePathStyle(true)
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                            Containers.MINIO.getUserName(), Containers.MINIO.getPassword())))
                    .build();
        }
        return s3;
    }
}

package org.cavalodetroia.ctr.application.wiring;

import org.cavalodetroia.ctr.application.config.StorageProperties;
import org.cavalodetroia.ctr.domain.port.out.ClipStorage;
import org.cavalodetroia.ctr.domain.port.out.EventRepository;
import org.cavalodetroia.ctr.domain.port.out.UploadSessionRepository;
import org.cavalodetroia.ctr.domain.service.UploadSessionService;
import org.cavalodetroia.ctr.infra.storage.S3ClientFactory;
import org.cavalodetroia.ctr.infra.storage.S3ClipStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class UploadWiring {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public S3Client s3Client(final StorageProperties storage) {
        return S3ClientFactory.create(storage.endpoint(), storage.region(), storage.accessKey(), storage.secretKey());
    }

    @Bean
    public ClipStorage clipStorage(final S3Client s3Client, final StorageProperties storage) {
        return new S3ClipStorage(s3Client, storage.bucket());
    }

    @Bean
    public UploadSessionService uploadSessionService(final EventRepository events,
                                                     final UploadSessionRepository sessions,
                                                     final ClipStorage clipStorage,
                                                     final Clock clock,
                                                     final StorageProperties storage) {
        return new UploadSessionService(events, sessions, clipStorage, clock, storage.bucket());
    }
}

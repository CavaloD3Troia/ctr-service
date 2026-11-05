package org.cavalodetroia.ctr.application.wiring;

import org.cavalodetroia.ctr.application.config.StorageProperties;
import org.cavalodetroia.ctr.domain.port.out.EventRepository;
import org.cavalodetroia.ctr.domain.port.out.UploadSessionRepository;
import org.cavalodetroia.ctr.domain.service.UploadSessionService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class UploadWiring {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public UploadSessionService uploadSessionService(final EventRepository events,
                                                     final UploadSessionRepository sessions,
                                                     final Clock clock,
                                                     final StorageProperties storage) {
        return new UploadSessionService(events, sessions, clock, storage.bucket());
    }
}

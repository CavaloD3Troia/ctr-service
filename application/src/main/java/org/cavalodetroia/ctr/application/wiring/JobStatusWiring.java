package org.cavalodetroia.ctr.application.wiring;

import org.cavalodetroia.ctr.domain.job.JobStatusService;
import org.cavalodetroia.ctr.domain.job.port.out.JobStatusReadModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JobStatusWiring {

    /**
     * Serves both inbound ports, {@code GetJobStatus} and {@code ProjectJobStatus}.
     */
    @Bean
    public JobStatusService jobStatusService(final JobStatusReadModel jobStatusReadModel) {
        return new JobStatusService(jobStatusReadModel);
    }
}

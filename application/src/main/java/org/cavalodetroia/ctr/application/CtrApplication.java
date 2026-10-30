package org.cavalodetroia.ctr.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Composition root. The domain has no annotations, so it is built by hand in {@code wiring}; the adapters in api
 * and infra are picked up by component scan, and their {@code @ConfigurationProperties} records by the properties
 * scan.
 */
@SpringBootApplication(scanBasePackages = {
        "org.cavalodetroia.ctr.application",
        "org.cavalodetroia.ctr.api",
        "org.cavalodetroia.ctr.infra"})
@ConfigurationPropertiesScan("org.cavalodetroia.ctr")
public class CtrApplication {

    public static void main(final String[] args) {
        SpringApplication.run(CtrApplication.class, args);
    }
}

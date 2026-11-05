package org.cavalodetroia.ctr.componenttests.config;

import io.cucumber.spring.CucumberContextConfiguration;
import org.cavalodetroia.ctr.application.CtrApplication;
import org.cavalodetroia.ctr.componenttests.support.QueryCounter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The real app on a random port with the {@code test} profile, pointed at the containers, with its SQL statements
 * recorded. One context for every scenario.
 */
@CucumberContextConfiguration
@SpringBootTest(classes = CtrApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(QueryCounter.class)
public class CucumberSpringConfiguration {

    @DynamicPropertySource
    static void containers(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", Containers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", Containers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", Containers.POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", Containers.KAFKA::getBootstrapServers);
        registry.add("ctr.storage.endpoint", Containers.MINIO::getS3URL);
        registry.add("ctr.storage.access-key", Containers.MINIO::getUserName);
        registry.add("ctr.storage.secret-key", Containers.MINIO::getPassword);
    }
}

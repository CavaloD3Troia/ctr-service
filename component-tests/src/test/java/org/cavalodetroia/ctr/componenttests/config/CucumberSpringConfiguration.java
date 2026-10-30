package org.cavalodetroia.ctr.componenttests.config;

import io.cucumber.spring.CucumberContextConfiguration;
import org.cavalodetroia.ctr.application.CtrApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The real app on a random port, pointed at the containers. One context for every scenario.
 */
@CucumberContextConfiguration
@SpringBootTest(
        classes = CtrApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.level.org.apache.kafka=WARN")
public class CucumberSpringConfiguration {

    @DynamicPropertySource
    static void containers(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", Containers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", Containers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", Containers.POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", Containers.KAFKA::getBootstrapServers);
    }
}

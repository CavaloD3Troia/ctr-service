package org.cavalodetroia.ctr.application;

import org.cavalodetroia.ctr.infra.kafka.CtrKafkaProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

/**
 * AC-4: the dev, test and prod profiles exist and bind the typed properties. Loads {@code application.yml} plus the
 * profile file the way Spring Boot does, without starting the app.
 */
class ProfilesTest {

    private static ApplicationContextRunner withProfile(final String profile) {
        return new ApplicationContextRunner()
                // The machine's own DB_URL, KAFKA_BOOTSTRAP_SERVERS... must not change the outcome
                .withInitializer(context -> context.getEnvironment().getPropertySources()
                        .remove(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profile)
                .withUserConfiguration(PropertiesConfiguration.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test", "prod"})
    void should_load_the_profile_file_and_bind_the_kafka_topic_when_profile_is_active(final String profile) {
        withProfile(profile).run(context -> {
            assertThat(context.getEnvironment().getPropertySources().stream().map(PropertySource::getName))
                    .anySatisfy(name -> assertThat(name).contains("application-" + profile + ".yml"));
            assertThat(context.getBean(CtrKafkaProperties.class).topic()).isEqualTo("ctr.event.created.v1");
        });
    }

    @Test
    void should_point_at_the_docker_compose_stack_when_profile_is_dev() {
        withProfile("dev").run(context -> {
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://localhost:5432/ctr");
            assertThat(context.getEnvironment().getProperty("spring.kafka.bootstrap-servers"))
                    .isEqualTo("localhost:9092");
        });
    }

    @Test
    void should_refuse_to_guess_the_database_when_profile_is_prod_and_DB_URL_is_unset() {
        withProfile("prod").run(context -> assertThatIllegalArgumentException()
                .isThrownBy(() -> context.getEnvironment().getProperty("spring.datasource.url"))
                .withMessageContaining("DB_URL"));
    }

    @Test
    void should_read_connections_from_the_environment_when_profile_is_prod() {
        withProfile("prod")
                .withPropertyValues(
                        "DB_URL=jdbc:postgresql://db:5432/ctr",
                        "DB_USERNAME=app",
                        "DB_PASSWORD=from-the-secret-store",
                        "KAFKA_BOOTSTRAP_SERVERS=broker:9092")
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                            .isEqualTo("jdbc:postgresql://db:5432/ctr");
                    assertThat(context.getEnvironment().getProperty("spring.datasource.username")).isEqualTo("app");
                    assertThat(context.getEnvironment().getProperty("spring.kafka.bootstrap-servers"))
                            .isEqualTo("broker:9092");
                });
    }

    @Test
    void should_quiet_the_kafka_client_logs_when_profile_is_test() {
        withProfile("test").run(context -> assertThat(
                context.getEnvironment().getProperty("logging.level.org.apache.kafka")).isEqualTo("WARN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test", "prod"})
    void should_expose_only_health_and_info_over_http_in_every_profile(final String profile) {
        // AC-3: health is reachable over HTTP; nothing else from actuator is
        withProfile(profile).run(context -> assertThat(
                context.getEnvironment().getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info"));
    }

    @EnableConfigurationProperties(CtrKafkaProperties.class)
    static class PropertiesConfiguration {
    }
}

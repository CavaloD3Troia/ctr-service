package org.cavalodetroia.ctr.application;

import org.cavalodetroia.ctr.application.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

/**
 * AC-1: the dev, test and prod profiles. dev points at the compose stack, test leaves the connections to
 * Testcontainers, and prod reads the datasource and the storage from the environment with no local default. Loads
 * {@code application.yml} plus the profile file the way Spring Boot does, without starting the app.
 */
class ProfilesTest {

    private static final List<String> DATASOURCE_AND_STORAGE = List.of(
            "spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
            "ctr.storage.bucket", "ctr.storage.endpoint", "ctr.storage.region",
            "ctr.storage.access-key", "ctr.storage.secret-key");

    private static ApplicationContextRunner withProfile(final String profile) {
        return new ApplicationContextRunner()
                // The machine's own DB_URL, S3_ENDPOINT... must not change the outcome
                .withInitializer(context -> context.getEnvironment().getPropertySources()
                        .remove(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profile);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test", "prod"})
    void should_load_the_profile_file_when_profile_is_active(final String profile) {
        withProfile(profile).run(context ->
                assertThat(context.getEnvironment().getPropertySources().stream().map(PropertySource::getName))
                        .anySatisfy(name -> assertThat(name).contains("application-" + profile + ".yml")));
    }

    @Test
    void should_point_at_the_compose_stack_when_profile_is_dev() {
        withProfile("dev").withUserConfiguration(StorageConfiguration.class).run(context -> {
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://localhost:5432/ctr");
            assertThat(context.getEnvironment().getProperty("spring.datasource.username")).isEqualTo("ctr");
            final StorageProperties storage = context.getBean(StorageProperties.class);
            assertThat(storage.bucket()).isEqualTo("cdt-videos");
            assertThat(storage.endpoint()).isEqualTo("http://localhost:9000");
            assertThat(storage.region()).isEqualTo("us-east-1");
            assertThat(storage.accessKey()).isEqualTo("minioadmin");
        });
    }

    @Test
    void should_leave_the_connections_to_testcontainers_when_profile_is_test() {
        withProfile("test").run(context -> {
            assertThat(context.getEnvironment().getProperty("spring.datasource.url")).isNull();
            assertThat(context.getEnvironment().getProperty("ctr.storage.endpoint")).isNull();
            assertThat(context.getEnvironment().getProperty("ctr.storage.access-key")).isNull();
            assertThat(context.getEnvironment().getProperty("ctr.storage.secret-key")).isNull();
            // What no container decides: the bucket the tests create, and a region MinIO accepts
            assertThat(context.getEnvironment().getProperty("ctr.storage.bucket")).isEqualTo("cdt-videos");
            assertThat(context.getEnvironment().getProperty("ctr.storage.region")).isEqualTo("us-east-1");
        });
    }

    @Test
    void should_quiet_the_kafka_client_logs_when_profile_is_test() {
        withProfile("test").run(context -> assertThat(
                context.getEnvironment().getProperty("logging.level.org.apache.kafka")).isEqualTo("WARN"));
    }

    @Test
    void should_carry_no_local_default_for_the_datasource_and_the_storage_when_profile_is_prod() {
        // The prod file as YAML: every datasource and storage key is a bare ${VARIABLE}, nothing after a colon
        final YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yml"));
        final Properties prod = yaml.getObject();

        assertThat(prod).isNotNull();
        for (final String key : DATASOURCE_AND_STORAGE) {
            assertThat(prod.getProperty(key)).as(key).matches("\\$\\{[A-Z0-9_]+}");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "spring.datasource.url, DB_URL",
            "spring.datasource.username, DB_USERNAME",
            "spring.datasource.password, DB_PASSWORD",
            "ctr.storage.bucket, CTR_STORAGE_BUCKET",
            "ctr.storage.endpoint, S3_ENDPOINT",
            "ctr.storage.region, S3_REGION",
            "ctr.storage.access-key, S3_ACCESS_KEY",
            "ctr.storage.secret-key, S3_SECRET_KEY"})
    void should_refuse_to_guess_when_profile_is_prod_and_the_variable_is_unset(final String property,
                                                                                final String variable) {
        withProfile("prod").run(context -> assertThatIllegalArgumentException()
                .isThrownBy(() -> context.getEnvironment().getProperty(property))
                .withMessageContaining(variable));
    }

    @Test
    void should_read_the_datasource_and_the_storage_from_the_environment_when_profile_is_prod() {
        withProfile("prod")
                .withUserConfiguration(StorageConfiguration.class)
                .withPropertyValues(
                        "DB_URL=jdbc:postgresql://db:5432/ctr",
                        "DB_USERNAME=app",
                        "DB_PASSWORD=from-the-secret-store",
                        "CTR_STORAGE_BUCKET=cdt-videos-prod",
                        // Empty on AWS, which then finds the endpoint from the region
                        "S3_ENDPOINT=",
                        "S3_REGION=eu-west-1",
                        "S3_ACCESS_KEY=from-the-secret-store",
                        "S3_SECRET_KEY=from-the-secret-store")
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                            .isEqualTo("jdbc:postgresql://db:5432/ctr");
                    assertThat(context.getEnvironment().getProperty("spring.datasource.username")).isEqualTo("app");
                    final StorageProperties storage = context.getBean(StorageProperties.class);
                    assertThat(storage.bucket()).isEqualTo("cdt-videos-prod");
                    assertThat(storage.endpoint()).isEmpty();
                    assertThat(storage.region()).isEqualTo("eu-west-1");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test", "prod"})
    void should_expose_only_health_and_info_over_http_in_every_profile(final String profile) {
        // Health is reachable over HTTP in every environment; nothing else from actuator is
        withProfile(profile).run(context -> assertThat(
                context.getEnvironment().getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info"));
    }

    @EnableConfigurationProperties(StorageProperties.class)
    static class StorageConfiguration {
    }
}

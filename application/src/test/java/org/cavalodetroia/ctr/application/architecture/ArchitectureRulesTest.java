package org.cavalodetroia.ctr.application.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.cavalodetroia.ctr.archfixtures.api.ApiReachingIntoApplication;
import org.cavalodetroia.ctr.archfixtures.api.ApiReachingIntoInfra;
import org.cavalodetroia.ctr.archfixtures.api.ApiResident;
import org.cavalodetroia.ctr.archfixtures.api.ApiUsingContracts;
import org.cavalodetroia.ctr.archfixtures.api.ApiUsingJooq;
import org.cavalodetroia.ctr.archfixtures.api.controller.ResidentController;
import org.cavalodetroia.ctr.archfixtures.api.controller.UploadEndpoint;
import org.cavalodetroia.ctr.archfixtures.api.filter.AdviceAmongFilters;
import org.cavalodetroia.ctr.archfixtures.application.ApplicationResident;
import org.cavalodetroia.ctr.archfixtures.application.ApplicationUsingJooq;
import org.cavalodetroia.ctr.archfixtures.application.ApplicationUsingShared;
import org.cavalodetroia.ctr.archfixtures.application.ControllerInApplication;
import org.cavalodetroia.ctr.archfixtures.domain.DomainResident;
import org.cavalodetroia.ctr.archfixtures.domain.DomainUsingSpring;
import org.cavalodetroia.ctr.archfixtures.infra.InfraReachingIntoApi;
import org.cavalodetroia.ctr.archfixtures.infra.InfraResident;
import org.cavalodetroia.ctr.archfixtures.infra.InfraUsingWebTypes;
import org.cavalodetroia.ctr.archfixtures.infra.converter.RecordMapper;
import org.cavalodetroia.ctr.archfixtures.infra.converter.ResidentConverter;
import org.cavalodetroia.ctr.archfixtures.infra.kafka.publisher.ListenerAmongPublishers;
import org.cavalodetroia.ctr.archfixtures.infra.kafka.publisher.PublisherUsingRepository;
import org.cavalodetroia.ctr.archfixtures.infra.repository.ResidentRepository;
import org.cavalodetroia.ctr.archfixtures.shared.SharedReachingIntoApplication;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC-4: the boundary rules of {@link HexagonalRules} hold in the production code, and each of them rejects the fixture
 * written to break it. The fixtures under {@code org.cavalodetroia.ctr.archfixtures} mirror the layers and are test
 * classes, so the production check, which imports production code only, never sees them. Each fixture check imports
 * the residents (one clean class per layer and per named package, so none is empty) plus the fixture, and expects
 * exactly that one violation, from that one rule and no other.
 */
class ArchitectureRulesTest {

    private static final String PRODUCTION_ROOT = "org.cavalodetroia.ctr";
    private static final String FIXTURES_ROOT = PRODUCTION_ROOT + ".archfixtures";
    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(PRODUCTION_ROOT);
    private static final HexagonalRules RULES = new HexagonalRules(FIXTURES_ROOT);
    private static final List<Class<?>> RESIDENTS = List.of(
            DomainResident.class,
            ApiResident.class, ResidentController.class,
            InfraResident.class, ResidentConverter.class,
            ApplicationResident.class);

    @ParameterizedTest(name = "{0}")
    @MethodSource("productionRules")
    void should_hold_in_the_production_code(final ArchRule rule) {
        assertThatCode(() -> rule.check(PRODUCTION)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} rejects {1}")
    @MethodSource("fixtures")
    void should_reject_the_fixture_with_its_rule_and_no_other(final ArchRule rule,
                                                               final Class<?> fixture,
                                                               final List<Class<?>> companions) {
        final JavaClasses classes = importWithResidents(fixture, companions);

        assertThatThrownBy(() -> rule.check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(fixture.getName());
        assertThat(rule.evaluate(classes).getFailureReport().getDetails())
                .as("exactly the fixture's violation")
                .hasSize(1);
        assertThat(RULES.all().stream()
                .filter(other -> other.evaluate(classes).hasViolation())
                .map(ArchRule::getDescription))
                .as("the rule the fixture was written for, and no other")
                .containsExactly(rule.getDescription());
    }

    @Test
    void should_pass_every_rule_when_only_the_residents_are_imported() {
        final JavaClasses residents = importWithResidents(null, List.of());

        for (final ArchRule rule : RULES.all()) {
            assertThatCode(() -> rule.check(residents)).as(rule.getDescription()).doesNotThrowAnyException();
        }
    }

    @Test
    void should_keep_the_fixtures_out_of_the_production_check() {
        assertThat(PRODUCTION.that(resideInAPackage(PRODUCTION_ROOT + ".domain.."))).isNotEmpty();
        assertThat(PRODUCTION.that(resideInAPackage(FIXTURES_ROOT + ".."))).isEmpty();
    }

    private static Stream<Arguments> productionRules() {
        return new HexagonalRules(PRODUCTION_ROOT).all().stream()
                .map(rule -> Arguments.of(Named.of(name(rule), rule)));
    }

    private static Stream<Arguments> fixtures() {
        return Stream.of(
                fixture(RULES.layers(), ApiReachingIntoInfra.class),
                fixture(RULES.layers(), InfraReachingIntoApi.class),
                fixture(RULES.layers(), ApiReachingIntoApplication.class),
                fixture(RULES.domainDependsOnlyOnTheJdk(), DomainUsingSpring.class),
                fixture(RULES.noCycles(), ApplicationUsingShared.class, SharedReachingIntoApplication.class),
                fixture(RULES.apiHasNoPersistenceOrMessagingTypes(), ApiUsingJooq.class),
                fixture(RULES.apiHasNoPersistenceOrMessagingTypes(), ApiUsingContracts.class),
                fixture(RULES.infraHasNoWebTypes(), InfraUsingWebTypes.class),
                fixture(RULES.entryPointsLiveInTheirPackage(), ControllerInApplication.class),
                fixture(RULES.entryPointsLiveInTheirPackage(), AdviceAmongFilters.class),
                fixture(RULES.entryPointsLiveInTheirPackage(), ListenerAmongPublishers.class),
                fixture(RULES.applicationHoldsNoAdapterCode(), ApplicationUsingJooq.class),
                fixture(RULES.infraAdaptersAreIndependent(), PublisherUsingRepository.class, ResidentRepository.class),
                fixture(RULES.controllersAreNamedController(), UploadEndpoint.class),
                fixture(RULES.convertersAreNamedConverter(), RecordMapper.class));
    }

    /**
     * @param companions clean classes the fixture needs on the classpath to violate its rule
     */
    private static Arguments fixture(final ArchRule rule, final Class<?> fixture, final Class<?>... companions) {
        return Arguments.of(Named.of(name(rule), rule), fixture, List.of(companions));
    }

    /** The rule's name, as CLAUDE.md lists it: the description up to the colon. */
    private static String name(final ArchRule rule) {
        final String description = rule.getDescription();
        return description.substring(0, description.indexOf(':'));
    }

    private static JavaClasses importWithResidents(final Class<?> fixture, final List<Class<?>> companions) {
        final List<Class<?>> all = new ArrayList<>(RESIDENTS);
        if (fixture != null) {
            all.add(fixture);
        }
        all.addAll(companions);
        return new ClassFileImporter().importClasses(all);
    }
}

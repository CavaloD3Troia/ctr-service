package org.cavalodetroia.ctr.application;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.cavalodetroia.ctr.archfixtures.api.ApiReachingIntoInfra;
import org.cavalodetroia.ctr.archfixtures.api.ApiResident;
import org.cavalodetroia.ctr.archfixtures.api.ApiUsingContracts;
import org.cavalodetroia.ctr.archfixtures.api.ApiUsingJooq;
import org.cavalodetroia.ctr.archfixtures.application.ApplicationResident;
import org.cavalodetroia.ctr.archfixtures.application.ApplicationUsingJooq;
import org.cavalodetroia.ctr.archfixtures.application.ControllerInApplication;
import org.cavalodetroia.ctr.archfixtures.application.ListenerInApplication;
import org.cavalodetroia.ctr.archfixtures.domain.DomainReachingIntoApplication;
import org.cavalodetroia.ctr.archfixtures.domain.DomainResident;
import org.cavalodetroia.ctr.archfixtures.domain.DomainUsingSpring;
import org.cavalodetroia.ctr.archfixtures.infra.InfraReachingIntoApi;
import org.cavalodetroia.ctr.archfixtures.infra.InfraResident;
import org.cavalodetroia.ctr.archfixtures.infra.InfraUsingWebTypes;
import org.cavalodetroia.ctr.archfixtures.infra.kafka.KafkaAdapterUsingPersistence;
import org.cavalodetroia.ctr.archfixtures.infra.persistence.PersistenceAdapter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC2–AC9: every boundary rule rejects a class that breaks it. The fixtures under
 * {@code org.cavalodetroia.ctr.archfixtures} mirror the layers and are test classes, so {@link ArchitectureTest}, which
 * imports production code only, never sees them. Each check imports the residents (one clean class per layer, so no
 * layer is empty) plus the fixture under test, and expects exactly that one violation.
 */
class ArchitectureRulesTest {

    private static final String FIXTURES = "org.cavalodetroia.ctr.archfixtures";
    private static final HexagonalRules RULES = new HexagonalRules(FIXTURES);
    private static final List<Class<?>> RESIDENTS = List.of(
            DomainResident.class, ApiResident.class, InfraResident.class, ApplicationResident.class);

    // AC2: dependency direction

    @Test
    void should_rejectApiClass_when_itReachesIntoInfra() {
        assertRejects(RULES.layers(), ApiReachingIntoInfra.class);
    }

    @Test
    void should_rejectInfraClass_when_itReachesIntoApi() {
        assertRejects(RULES.layers(), InfraReachingIntoApi.class);
    }

    @Test
    void should_rejectDomainClass_when_itReachesIntoApplication() {
        assertRejects(RULES.layers(), DomainReachingIntoApplication.class);
    }

    // AC3: domain is JDK-only

    @Test
    void should_rejectDomainClass_when_itUsesSpring() {
        assertRejects(RULES.domainDependsOnlyOnTheJdk(), DomainUsingSpring.class);
    }

    // AC4: no persistence or messaging types in api

    @Test
    void should_rejectApiClass_when_itUsesJooq() {
        assertRejects(RULES.apiHasNoPersistenceOrMessagingTypes(), ApiUsingJooq.class);
    }

    @Test
    void should_rejectApiClass_when_itUsesAKafkaContract() {
        assertRejects(RULES.apiHasNoPersistenceOrMessagingTypes(), ApiUsingContracts.class);
    }

    // AC5: no web types in infra

    @Test
    void should_rejectInfraClass_when_itUsesSpringMvc() {
        assertRejects(RULES.infraHasNoWebTypes(), InfraUsingWebTypes.class);
    }

    // AC6: entry points in their layer

    @Test
    void should_rejectRestController_when_itLivesOutsideApi() {
        assertRejects(RULES.entryPointsLiveInTheirLayer(), ControllerInApplication.class);
    }

    @Test
    void should_rejectKafkaListener_when_itLivesOutsideInfra() {
        assertRejects(RULES.entryPointsLiveInTheirLayer(), ListenerInApplication.class);
    }

    // AC7: application holds no adapter code

    @Test
    void should_rejectApplicationClass_when_itUsesJooq() {
        assertRejects(RULES.applicationHoldsNoAdapterCode(), ApplicationUsingJooq.class);
    }

    // AC8: adapters are independent

    @Test
    void should_rejectKafkaAdapter_when_itUsesThePersistenceAdapter() {
        assertRejects(RULES.infraAdaptersAreIndependent(), KafkaAdapterUsingPersistence.class, PersistenceAdapter.class);
    }

    // AC9: the fixtures are what trips the rules, and the production check never sees them

    @Test
    void should_passEveryRule_when_onlyTheResidentsAreImported() {
        final JavaClasses residents = importWithResidents(List.of());

        for (final ArchRule rule : allRules()) {
            assertThatCode(() -> rule.check(residents)).as(rule.getDescription()).doesNotThrowAnyException();
        }
    }

    @Test
    void should_keepTheFixturesOutOfTheProductionCheck_when_testClassesAreExcluded() {
        final JavaClasses production = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("org.cavalodetroia.ctr");

        assertThat(production.that(resideInAPackage("org.cavalodetroia.ctr.domain.."))).isNotEmpty();
        assertThat(production.that(resideInAPackage(FIXTURES + ".."))).isEmpty();
    }

    private static void assertRejects(final ArchRule rule, final Class<?> fixture, final Class<?>... alsoImported) {
        final List<Class<?>> fixtures = new ArrayList<>();
        fixtures.add(fixture);
        fixtures.addAll(List.of(alsoImported));
        final JavaClasses classes = importWithResidents(fixtures);

        assertThatThrownBy(() -> rule.check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(fixture.getName());
        assertThat(rule.evaluate(classes).getFailureReport().getDetails())
                .as("exactly the fixture's violation")
                .hasSize(1);
    }

    private static JavaClasses importWithResidents(final List<Class<?>> fixtures) {
        final List<Class<?>> all = new ArrayList<>(RESIDENTS);
        all.addAll(fixtures);
        return new ClassFileImporter().importClasses(all);
    }

    private static List<ArchRule> allRules() {
        return List.of(
                RULES.layers(),
                RULES.domainDependsOnlyOnTheJdk(),
                RULES.noCycles(),
                RULES.apiHasNoPersistenceOrMessagingTypes(),
                RULES.infraHasNoWebTypes(),
                RULES.entryPointsLiveInTheirLayer(),
                RULES.applicationHoldsNoAdapterCode(),
                RULES.infraAdaptersAreIndependent());
    }
}

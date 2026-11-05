package org.cavalodetroia.ctr.application;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.containAnyMethodsThat;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * The hexagonal boundary rules of DEC-0005 §Enforcement, for the shipped layout ({@code domain}, {@code api},
 * {@code infra}, {@code application}) under any root package. {@link ArchitectureTest} applies them to the production
 * code under {@code org.cavalodetroia.ctr}; {@link ArchitectureRulesTest} applies the same definitions to the
 * fixtures under {@code org.cavalodetroia.ctr.archfixtures} to prove each rule rejects its violation.
 */
final class HexagonalRules {

    /** Persistence and messaging technology. Only {@code infra} adapts it; everything else sees domain types. */
    static final String[] PERSISTENCE_AND_MESSAGING = {
            "org.jooq..",
            "java.sql..",
            "org.apache.kafka..",
            "org.springframework.kafka..",
            "com.google.protobuf..",
            "org.cavalodetroia.contracts.."};

    private static final DescribedPredicate<CanBeAnnotated> WEB_ENTRY_POINTS = annotatedWith(RestController.class)
            .or(annotatedWith(Controller.class))
            .or(annotatedWith(RestControllerAdvice.class))
            .or(annotatedWith(ControllerAdvice.class))
            .as("are web entry points (@RestController, @Controller, @RestControllerAdvice or @ControllerAdvice)");

    private static final DescribedPredicate<JavaClass> KAFKA_LISTENERS =
            containAnyMethodsThat(annotatedWith(KafkaListener.class))
                    .or(annotatedWith(KafkaListener.class))
                    .as("are Kafka listeners (@KafkaListener on the class or on a method)");

    private final String root;
    private final String domain;
    private final String api;
    private final String infra;
    private final String application;

    /**
     * @param root the package holding the four layers as direct sub-packages, e.g. {@code org.cavalodetroia.ctr}
     */
    HexagonalRules(final String root) {
        this.root = root;
        this.domain = root + ".domain..";
        this.api = root + ".api..";
        this.infra = root + ".infra..";
        this.application = root + ".application..";
    }

    /** Dependencies point inward: nothing uses {@code application}; {@code api} and {@code infra} are used by it only. */
    ArchRule layers() {
        return layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy(domain)
                .layer("Api").definedBy(api)
                .layer("Infra").definedBy(infra)
                .layer("Application").definedBy(application)
                .whereLayer("Application").mayNotBeAccessedByAnyLayer()
                .whereLayer("Api").mayOnlyBeAccessedByLayers("Application")
                .whereLayer("Infra").mayOnlyBeAccessedByLayers("Application");
    }

    ArchRule domainDependsOnlyOnTheJdk() {
        return noClasses()
                .that().resideInAPackage(domain)
                .should().dependOnClassesThat().resideOutsideOfPackages(domain, "java..");
    }

    ArchRule noCycles() {
        return slices()
                .matching(root + ".(*)..")
                .should().beFreeOfCycles();
    }

    ArchRule apiHasNoPersistenceOrMessagingTypes() {
        return noClasses()
                .that().resideInAPackage(api)
                .should().dependOnClassesThat().resideInAnyPackage(PERSISTENCE_AND_MESSAGING)
                .because("DEC-0005: generated protobuf and database types never reach business logic and are mapped at "
                        + "the edge; the REST adapter reaches the database and the broker through the domain's ports only");
    }

    /** Spring's HTTP client ({@code org.springframework.web.client}) is not web-layer code and stays allowed. */
    ArchRule infraHasNoWebTypes() {
        return noClasses()
                .that().resideInAPackage(infra)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web.bind.annotation..",
                        "org.springframework.web.servlet..",
                        api)
                .because("DEC-0005: adapters never depend on each other; HTTP belongs to the inbound adapter (api), "
                        + "the outbound adapters only implement ports");
    }

    ArchRule entryPointsLiveInTheirLayer() {
        return CompositeArchRule
                .of(classes().that(WEB_ENTRY_POINTS).should().resideInAPackage(api).allowEmptyShould(true))
                .and(classes().that(KAFKA_LISTENERS).should().resideInAPackage(infra).allowEmptyShould(true))
                .as("entry points live in their layer: web entry points in '" + api + "', Kafka listeners in '"
                        + infra + "'")
                .because("DEC-0005: @RestController only in the web adapter and @KafkaListener only in the Kafka adapter, "
                        + "so every way into the service is found in one place");
    }

    ArchRule applicationHoldsNoAdapterCode() {
        return noClasses()
                .that().resideInAPackage(application)
                .should().dependOnClassesThat().resideInAnyPackage(PERSISTENCE_AND_MESSAGING)
                .because("DEC-0005: the composition root wires adapters to ports; it has every jar on its classpath, "
                        + "so only this rule stops adapter code from growing there");
    }

    /** A service with a single adapter has nothing to compare, hence {@code allowEmptyShould}. */
    ArchRule infraAdaptersAreIndependent() {
        return slices()
                .matching(root + ".infra.(*)..")
                .should().notDependOnEachOther()
                .allowEmptyShould(true)
                .because("DEC-0005: adapters never depend on each other; what two adapters share belongs in the domain "
                        + "or behind a port");
    }
}

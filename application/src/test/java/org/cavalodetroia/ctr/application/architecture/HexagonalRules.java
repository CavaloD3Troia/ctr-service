package org.cavalodetroia.ctr.application.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.library.dependencies.Slice;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.containAnyMethodsThat;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * The hexagonal boundary rules of CLAUDE.md ("Architecture"), for the kind-first layout ({@code domain}, {@code api},
 * {@code infra} and {@code application}, each split by kind of class) under any root package.
 * {@link ArchitectureRulesTest} applies them to the production code under {@code org.cavalodetroia.ctr}, and the same
 * definitions to the fixtures under {@code org.cavalodetroia.ctr.archfixtures} to prove each rule rejects its
 * violation. Every rule is named after the line of CLAUDE.md it enforces, so a failure says which one.
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

    /**
     * The infra packages that each adapt one technology. {@code converter}, {@code domain} and the generated jOOQ
     * classes are what the adapters share, so they are not adapters themselves.
     */
    static final Set<String> INFRA_ADAPTERS = Set.of("repository", "kafka", "storage", "outbox");

    private static final DescribedPredicate<CanBeAnnotated> CONTROLLERS = annotatedWith(RestController.class)
            .or(annotatedWith(Controller.class))
            .as("are controllers (@RestController or @Controller)");

    private static final DescribedPredicate<CanBeAnnotated> CONTROLLER_ADVICE =
            annotatedWith(RestControllerAdvice.class)
                    .or(annotatedWith(ControllerAdvice.class))
                    .as("are controller advice (@RestControllerAdvice or @ControllerAdvice)");

    private static final DescribedPredicate<JavaClass> KAFKA_LISTENERS =
            containAnyMethodsThat(annotatedWith(KafkaListener.class))
                    .or(annotatedWith(KafkaListener.class))
                    .as("are Kafka listeners (@KafkaListener on the class or on a method)");

    private static final DescribedPredicate<Slice> ADAPTERS = DescribedPredicate.describe(
            "are adapters (" + String.join(", ", new TreeSet<>(INFRA_ADAPTERS)) + ")",
            slice -> INFRA_ADAPTERS.contains(slice.getNamePart(1)));

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

    /** Every rule, in the order CLAUDE.md lists them. */
    List<ArchRule> all() {
        return List.of(
                layers(),
                domainDependsOnlyOnTheJdk(),
                noCycles(),
                apiHasNoPersistenceOrMessagingTypes(),
                infraHasNoWebTypes(),
                entryPointsLiveInTheirPackage(),
                applicationHoldsNoAdapterCode(),
                infraAdaptersAreIndependent(),
                controllersAreNamedController(),
                convertersAreNamedConverter());
    }

    ArchRule layers() {
        return layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy(domain)
                .layer("Api").definedBy(api)
                .layer("Infra").definedBy(infra)
                .layer("Application").definedBy(application)
                .whereLayer("Application").mayNotBeAccessedByAnyLayer()
                .whereLayer("Api").mayOnlyBeAccessedByLayers("Application")
                .whereLayer("Infra").mayOnlyBeAccessedByLayers("Application")
                .as("LAYERS: dependencies point inward, nothing uses application, "
                        + "and api and infra are used by application only");
    }

    ArchRule domainDependsOnlyOnTheJdk() {
        return noClasses()
                .that().resideInAPackage(domain)
                .should().dependOnClassesThat().resideOutsideOfPackages(domain, "java..")
                .as("DOMAIN_DEPENDS_ONLY_ON_THE_JDK: domain references java.. and itself, nothing else");
    }

    /**
     * {@link #layers()} already forbids every cycle between the four layers; this one also sees a package outside
     * them, which LAYERS ignores.
     */
    ArchRule noCycles() {
        return slices()
                .matching(root + ".(*)..")
                .should().beFreeOfCycles()
                .as("NO_CYCLES: no cycles between the top-level packages");
    }

    ArchRule apiHasNoPersistenceOrMessagingTypes() {
        return noClasses()
                .that().resideInAPackage(api)
                .should().dependOnClassesThat().resideInAnyPackage(PERSISTENCE_AND_MESSAGING)
                .as("API_HAS_NO_PERSISTENCE_OR_MESSAGING_TYPES: no jOOQ, JDBC, Kafka, protobuf or contract types in api")
                .because("generated protobuf and database types are mapped at the edge and never reach business "
                        + "logic; the REST adapter reaches the database and the broker through the domain's ports only");
    }

    /** Spring's HTTP client ({@code org.springframework.web.client}) is not web-layer code and stays allowed. */
    ArchRule infraHasNoWebTypes() {
        return noClasses()
                .that().resideInAPackage(infra)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web.bind.annotation..",
                        "org.springframework.web.servlet..")
                .as("INFRA_HAS_NO_WEB_TYPES: no Spring MVC annotations or servlet types in infra")
                .because("HTTP belongs to the inbound adapter (api); the outbound adapters only implement ports");
    }

    ArchRule entryPointsLiveInTheirPackage() {
        return CompositeArchRule
                .of(classes().that(CONTROLLERS)
                        .should().resideInAPackage(root + ".api.controller..").allowEmptyShould(true))
                .and(classes().that(CONTROLLER_ADVICE)
                        .should().resideInAPackage(root + ".api.error..").allowEmptyShould(true))
                .and(classes().that(KAFKA_LISTENERS)
                        .should().resideInAPackage(root + ".infra.kafka.listener..").allowEmptyShould(true))
                .as("ENTRY_POINTS_LIVE_IN_THEIR_PACKAGE: controllers in api/controller, controller advice in "
                        + "api/error, Kafka listeners in infra/kafka/listener")
                .because("every way into the service is found in one place");
    }

    ArchRule applicationHoldsNoAdapterCode() {
        return noClasses()
                .that().resideInAPackage(application)
                .should().dependOnClassesThat().resideInAnyPackage(PERSISTENCE_AND_MESSAGING)
                .as("APPLICATION_HOLDS_NO_ADAPTER_CODE: no jOOQ, JDBC, Kafka, protobuf or contract types in "
                        + "application")
                .because("the composition root wires adapters to ports and has every jar on its classpath, so only "
                        + "this rule stops adapter code from growing there");
    }

    /** A service with a single adapter has nothing to compare, hence {@code allowEmptyShould}. */
    ArchRule infraAdaptersAreIndependent() {
        return slices()
                .matching(root + ".infra.(*)..")
                .that(ADAPTERS)
                .should().notDependOnEachOther()
                .as("INFRA_ADAPTERS_ARE_INDEPENDENT: infra/repository, infra/kafka, infra/storage and infra/outbox "
                        + "never reference each other")
                .because("what two adapters share belongs in the domain, behind a port, or in infra's converter "
                        + "and domain packages")
                .allowEmptyShould(true);
    }

    // Packages say what kind of class lives in them (see CLAUDE.md, "Packages"), so the names must agree

    ArchRule controllersAreNamedController() {
        return classes()
                .that().resideInAPackage(root + "..controller..")
                .should().haveSimpleNameEndingWith("Controller")
                .as("CONTROLLERS_ARE_NAMED_CONTROLLER: classes in a controller package end in Controller");
    }

    ArchRule convertersAreNamedConverter() {
        return classes()
                .that().resideInAPackage(root + "..converter..")
                .should().haveSimpleNameEndingWith("Converter")
                .as("CONVERTERS_ARE_NAMED_CONVERTER: classes in a converter package end in Converter");
    }
}

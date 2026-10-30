package org.cavalodetroia.ctr.application;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * The hexagonal boundaries, enforced by the build instead of by code review.
 */
@AnalyzeClasses(packages = "org.cavalodetroia.ctr", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule LAYERS = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("org.cavalodetroia.ctr.domain..")
            .layer("Api").definedBy("org.cavalodetroia.ctr.api..")
            .layer("Infra").definedBy("org.cavalodetroia.ctr.infra..")
            .layer("Application").definedBy("org.cavalodetroia.ctr.application..")
            .whereLayer("Application").mayNotBeAccessedByAnyLayer()
            .whereLayer("Api").mayOnlyBeAccessedByLayers("Application")
            .whereLayer("Infra").mayOnlyBeAccessedByLayers("Application");

    @ArchTest
    static final ArchRule DOMAIN_DEPENDS_ONLY_ON_THE_JDK = noClasses()
            .that().resideInAPackage("org.cavalodetroia.ctr.domain..")
            .should().dependOnClassesThat()
            .resideOutsideOfPackages("org.cavalodetroia.ctr.domain..", "java..");

    @ArchTest
    static final ArchRule NO_CYCLES = slices()
            .matching("org.cavalodetroia.ctr.(*)..")
            .should().beFreeOfCycles();
}

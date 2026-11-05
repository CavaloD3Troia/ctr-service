package org.cavalodetroia.ctr.application;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The hexagonal boundaries, enforced by the build instead of by code review. The rules are defined once in
 * {@link HexagonalRules}; {@link ArchitectureRulesTest} proves each of them rejects a violation.
 */
@AnalyzeClasses(packages = "org.cavalodetroia.ctr", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final HexagonalRules RULES = new HexagonalRules("org.cavalodetroia.ctr");

    @ArchTest
    static final ArchRule LAYERS = RULES.layers();

    @ArchTest
    static final ArchRule DOMAIN_DEPENDS_ONLY_ON_THE_JDK = RULES.domainDependsOnlyOnTheJdk();

    @ArchTest
    static final ArchRule NO_CYCLES = RULES.noCycles();

    @ArchTest
    static final ArchRule API_HAS_NO_PERSISTENCE_OR_MESSAGING_TYPES = RULES.apiHasNoPersistenceOrMessagingTypes();

    @ArchTest
    static final ArchRule INFRA_HAS_NO_WEB_TYPES = RULES.infraHasNoWebTypes();

    @ArchTest
    static final ArchRule ENTRY_POINTS_LIVE_IN_THEIR_LAYER = RULES.entryPointsLiveInTheirLayer();

    @ArchTest
    static final ArchRule APPLICATION_HOLDS_NO_ADAPTER_CODE = RULES.applicationHoldsNoAdapterCode();

    @ArchTest
    static final ArchRule INFRA_ADAPTERS_ARE_INDEPENDENT = RULES.infraAdaptersAreIndependent();
}

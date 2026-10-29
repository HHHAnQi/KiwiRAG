package com.xxx.ragdoc.parser.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Keeps the parser application core independent from transport and persistence adapters. */
@AnalyzeClasses(packages = "com.xxx.ragdoc.parser")
class ParserArchitectureTest {

    @ArchTest
    static final ArchRule applicationDoesNotDependOnParserInfrastructure =
            noClasses()
                    .that()
                    .resideInAPackage("..parser.application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..parser.infrastructure..");

    @ArchTest
    static final ArchRule parserPackagesAreFreeOfCycles =
            slices().matching("com.xxx.ragdoc.parser.(*)..").should().beFreeOfCycles();
}

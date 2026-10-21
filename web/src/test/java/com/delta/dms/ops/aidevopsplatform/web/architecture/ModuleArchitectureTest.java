package com.delta.dms.ops.aidevopsplatform.web.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

class ModuleArchitectureTest {
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.delta.dms.ops.aidevopsplatform");

    @Test void modelDependsOnlyOnTheJdkAndModel() {
        classes().that().resideInAPackage("..model..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..model..")
                .check(classes);
    }
    @Test void serviceDependsOnlyOnTheJdkAndCore() {
        classes().that().resideInAPackage("..service..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..model..", "..service..")
                .check(classes);
    }
    @Test void adaptersUsePortsInsteadOfApplicationImplementations() {
        noClasses().that().resideInAPackage("..adapter..")
                .should().dependOnClassesThat().resideInAnyPackage("..service.work..", "..service.event..", "..web..")
                .check(classes);
    }
    @Test void portTypesAreInterfacesOrImmutableContracts() {
        classes().that().resideInAnyPackage("..service.port.in", "..service.port.out")
                .and().areTopLevelClasses().should().beInterfaces().check(classes);
    }
    @Test void runtimeDoesNotDependOnEvaluationOrTestFixtures() {
        noClasses().that().resideOutsideOfPackages("..evaluation..", "..fixture..")
                .should().dependOnClassesThat().resideInAnyPackage("..evaluation..", "..fixture..")
                .check(classes);
    }
}

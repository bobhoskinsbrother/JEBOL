package org.jebol.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dependency rule points inward: adapters may know about the application,
 * the application may know about the domain, and the domain knows about
 * neither. Enforced here rather than by convention, so that swapping an
 * adapter cannot quietly reach into the middle.
 */
class DependencyRuleTest {

    private static JavaClasses production;
    private static List<Path> importedPaths;

    @BeforeAll
    static void importProductionCode() {
        String classesDirs = System.getProperty("jebol.mainClassesDirs");
        if (classesDirs == null || classesDirs.isBlank()) {
            throw new IllegalStateException(
                    "jebol.mainClassesDirs is not set; the build must pass the "
                            + "production classes directory to the architecture tests");
        }
        importedPaths = Arrays.stream(classesDirs.split(File.pathSeparator))
                .map(Path::of)
                .filter(Files::isDirectory)
                .toList();
        production = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPaths(importedPaths);
    }

    /**
     * An architecture rule that matches nothing passes for the wrong reason.
     * This fails loudly if the importer stops finding production classes, so
     * the rules below cannot quietly become decoration.
     */
    @Test
    @DisplayName("the importer actually found the production classes")
    void importerFoundProductionClasses() {
        assertThat(production)
                .as("classesDirs=%s resolved=%s imported=%s",
                        System.getProperty("jebol.mainClassesDirs"),
                        importedPaths,
                        production.stream().map(JavaClass::getName).toList())
                .isNotEmpty();
    }

    @Test
    @DisplayName("the domain does not depend on the application layer")
    void domainDoesNotDependOnApplication() {
        noClasses()
                .that().resideInAPackage("org.jebol.domain..")
                .should().dependOnClassesThat().resideInAPackage("org.jebol.application..")
                .check(production);
    }

    @Test
    @DisplayName("the domain does not depend on any adapter")
    void domainDoesNotDependOnAdapters() {
        noClasses()
                .that().resideInAPackage("org.jebol.domain..")
                .should().dependOnClassesThat().resideInAPackage("org.jebol.adapter..")
                .check(production);
    }

    @Test
    @DisplayName("the application does not depend on any adapter")
    void applicationDoesNotDependOnAdapters() {
        noClasses()
                .that().resideInAPackage("org.jebol.application..")
                .should().dependOnClassesThat().resideInAPackage("org.jebol.adapter..")
                .check(production);
    }

    @Test
    @DisplayName("the domain does not reach the outside world")
    void domainPerformsNoInputOrOutput() {
        noClasses()
                .that().resideInAPackage("org.jebol.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.io..", "java.nio.file..", "java.net..")
                .because("the domain is pure; reading and writing belong to adapters")
                .check(production);
    }
}

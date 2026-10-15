package org.jebol.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EveryPropertyIsActuallyRunTest {

    private static final String JUPITER_CONTAINER = "org.junit.jupiter.api.Nested";
    private static final List<String> JQWIK_CHECKS =
            List.of("net.jqwik.api.Property", "net.jqwik.api.Example");

    private static JavaClasses tests;

    private static List<Path> everySuitesClasses;

    @BeforeAll
    static void importTheTestClasses() {
        String classesDirs = System.getProperty("jebol.testClassesDirs", "");
        everySuitesClasses = Arrays.stream(classesDirs.split(File.pathSeparator))
                .filter(directory -> !directory.isBlank())
                .map(Path::of)
                .toList();
        tests = new ClassFileImporter().importPaths(everySuitesClasses);
    }

    @Test
    @DisplayName("the classes of all three suites are handed over, unit, functional and end to end")
    void everySuiteIsHandedOver() {
        assertThat(everySuitesClasses)
                .as("jebol.testClassesDirs=%s", System.getProperty("jebol.testClassesDirs"))
                .anyMatch(directory -> directory.endsWith(Path.of("java", "unitTest")))
                .anyMatch(directory -> directory.endsWith(Path.of("java", "functionalTest")))
                .anyMatch(directory -> directory.endsWith(Path.of("java", "endToEndTest")));
    }

    private static boolean isAJupiterContainer(JavaClass type) {
        return type.isAnnotatedWith(JUPITER_CONTAINER);
    }

    private static boolean isAJqwikCheck(JavaMethod method) {
        return JQWIK_CHECKS.stream().anyMatch(method::isAnnotatedWith);
    }

    private static List<String> jqwikChecksDeclaredIn(JavaClass type) {
        return type.getMethods().stream()
                .filter(EveryPropertyIsActuallyRunTest::isAJqwikCheck)
                .map(JavaMethod::getFullName)
                .toList();
    }

    @Test
    @DisplayName("the importer actually found the test classes")
    void importerFoundTheTestClasses() {
        assertThat(tests).isNotEmpty();
    }

    @Test
    @DisplayName("and there are jqwik checks for the rule below to find")
    void thereAreJqwikChecksToFind() {
        assertThat(tests.stream()
                .map(EveryPropertyIsActuallyRunTest::jqwikChecksDeclaredIn)
                .flatMap(List::stream)
                .toList())
                .as("with none of these the rule below holds vacuously")
                .isNotEmpty();
    }

    @Test
    @DisplayName("and there are Jupiter containers for it to recognise")
    void thereAreJupiterContainersToRecognise() {
        assertThat(tests.stream()
                .filter(EveryPropertyIsActuallyRunTest::isAJupiterContainer)
                .map(JavaClass::getName)
                .toList())
                .as("with none of these the rule below holds vacuously")
                .isNotEmpty();
    }

    @Test
    @DisplayName("no jqwik check sits inside a Jupiter container, where nothing runs it")
    void noJqwikCheckSitsInsideAJupiterContainer() {
        List<String> unreachable = tests.stream()
                .filter(EveryPropertyIsActuallyRunTest::isAJupiterContainer)
                .map(EveryPropertyIsActuallyRunTest::jqwikChecksDeclaredIn)
                .flatMap(List::stream)
                .toList();

        assertThat(unreachable)
                .as("Jupiter claims the enclosing class and does not know jqwik, "
                        + "so these are neither run nor reported skipped; move each "
                        + "to a class of its own")
                .isEmpty();
    }
}

package org.jebol.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EveryPropertyIsActuallyRunTest {

    private static final String JUPITER_CONTAINER = "org.junit.jupiter.api.Nested";
    private static final List<String> JQWIK_CHECKS =
            List.of("net.jqwik.api.Property", "net.jqwik.api.Example");

    private static JavaClasses tests;

    @BeforeAll
    static void importTheTestClasses() throws URISyntaxException {
        tests = new ClassFileImporter().importPath(whereTheTestClassesWereCompiled());
    }

    private static Path whereTheTestClassesWereCompiled() throws URISyntaxException {
        return Path.of(EveryPropertyIsActuallyRunTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
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

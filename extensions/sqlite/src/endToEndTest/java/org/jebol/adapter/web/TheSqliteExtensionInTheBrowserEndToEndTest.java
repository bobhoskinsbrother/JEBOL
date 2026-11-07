package org.jebol.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Order(1)
@ExtendWith(TheSuitesPageBrowsers.class)
class TheSqliteExtensionInTheBrowserEndToEndTest {

    private static final Path REFERENCE = Path.of("extensions/sqlite/src/functionalTest/resources/reference");

    private static final Path WITH_THE_EXTENSION = Path.of(System.getProperty("jebol.webBuildWithExtensions"));

    private static final String STARTED = "=== started";

    private static final String FINISHED = "=== finished";

    private final PageBrowsers browsers;

    TheSqliteExtensionInTheBrowserEndToEndTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    private List<String> runOnThePage(ShippedRunner runner, String script) throws IOException {
        runner.servingFile("/" + script, Files.readAllBytes(REFERENCE.resolve(script)), "text/plain; charset=utf-8")
                .running("""
                        system/options/quiet: true
                        system/options/args: []
                        print {%s}
                        do to url! join {THE-ORIGIN} {/%s}
                        print {%s}
                        """.formatted(STARTED, script, FINISHED));
        try (AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, "chrome", runner).opensThePage()) {
            operator.waitUntilOutputContains(FINISHED);
            List<String> printed = operator.readOutput();
            return printed.subList(printed.indexOf(STARTED) + 1, printed.indexOf(FINISHED));
        }
    }

    private static List<String> transcript(String name) throws IOException {
        return Files.readString(REFERENCE.resolve(name), StandardCharsets.UTF_8).replace("\r\n", "\n").lines().toList();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"module-commands", "module-scheme", "module-files"})
    @DisplayName("a site built with the extension answers each reference script as the C does")
    void answersAsTheC(String script) throws IOException {
        try (ShippedRunner runner = new ShippedRunner(WITH_THE_EXTENSION)) {
            assertThat(runOnThePage(runner, script + ".r3")).containsExactlyElementsOf(transcript(script + ".txt"));
        }
    }

    @Test
    @DisplayName("a site built with the extension traces as the C does, timings aside")
    void tracesAsTheC() throws IOException {
        try (ShippedRunner runner = new ShippedRunner(WITH_THE_EXTENSION)) {
            assertThat(runOnThePage(runner, "module-trace.r3").stream()
                    .map(line -> line.replaceAll("PROFILE: [0-9]+ns", "PROFILE: <n>ns")).toList())
                    .containsExactlyElementsOf(transcript("module-trace.txt"));
        }
    }

    @Test
    @DisplayName("a site built without it has no sqlite module and no sqlite: scheme")
    void isAbsentWithoutIt() throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                print reduce [
                    {sqlite}
                    none? select system/modules 'sqlite
                    error? try [import 'sqlite]
                    none? select system/schemes 'sqlite
                ]
                print {done}
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, "chrome", runner).opensThePage()) {
            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains("sqlite true true true");
        }
    }
}

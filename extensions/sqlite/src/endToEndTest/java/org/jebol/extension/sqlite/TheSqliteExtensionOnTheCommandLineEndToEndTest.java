package org.jebol.extension.sqlite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TheSqliteExtensionOnTheCommandLineEndToEndTest {

    private static final Path SHIPPED_JAR = Path.of(System.getProperty("jebol.shippedJar"));

    private static final Path EXTENSION_JAR = Path.of(System.getProperty("jebol.extensionJar"));

    private static final Path REFERENCE = Path.of("extensions/sqlite/src/functionalTest/resources/reference");

    private static final String JAVA = ProcessHandle.current().info().command().orElse("java");

    private static final long PATIENCE_SECONDS = 120;

    @TempDir
    Path directory;

    private record Ran(int exit, List<String> out, String errors) {
    }

    private Ran run(boolean withTheExtension, String script) throws IOException, InterruptedException {
        String classPath = withTheExtension
                ? SHIPPED_JAR + File.pathSeparator + EXTENSION_JAR
                : SHIPPED_JAR.toString();
        Path out = directory.resolve("out.txt");
        Path errors = directory.resolve("errors.txt");
        Process process = new ProcessBuilder(JAVA, "-Djava.awt.headless=true", "-cp", classPath,
                "org.jebol.adapter.cli.JebolLauncher", "-qs", script)
                .directory(directory.toFile())
                .redirectOutput(out.toFile())
                .redirectError(errors.toFile())
                .start();
        assertThat(process.waitFor(PATIENCE_SECONDS, TimeUnit.SECONDS)).as("the command line finished").isTrue();
        return new Ran(process.exitValue(),
                Files.readString(out, StandardCharsets.UTF_8).replace("\r\n", "\n").lines().toList(),
                Files.readString(errors, StandardCharsets.UTF_8));
    }

    private void copyScript(String script) throws IOException {
        Files.copy(REFERENCE.resolve(script), directory.resolve(script));
    }

    private static List<String> transcript(String name) throws IOException {
        return new ArrayList<>(Files.readString(REFERENCE.resolve(name), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").lines().toList());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"module-commands", "module-files", "module-scheme"})
    @DisplayName("with the jar on -cp, each reference script answers as the C does, and nothing reaches stderr")
    void answersAsTheC(String script) throws IOException, InterruptedException {
        copyScript(script + ".r3");

        Ran ran = run(true, script + ".r3");

        assertThat(ran.errors()).isEmpty();
        assertThat(ran.out()).containsExactlyElementsOf(transcript(script + ".txt"));
    }

    @Test
    @DisplayName("without the jar, import 'sqlite fails as a module that cannot be found, and nothing else is said")
    void failsWithoutIt() throws IOException, InterruptedException {
        Files.writeString(directory.resolve("without.r3"), """
                Rebol []
                e: try [import 'sqlite]
                print mold reduce [e/type e/id e/arg1 e/arg2 select system/modules 'sqlite]
                """);

        Ran ran = run(false, "without.r3");

        assertThat(ran.errors()).isEmpty();
        assertThat(ran.out()).containsExactly("""
                [access cannot-open sqlite "module not found" _]""");
    }

    @Test
    @DisplayName("what one run writes to a database file, the next run reads")
    void keepsTheFileBetweenRuns() throws IOException, InterruptedException {
        Files.writeString(directory.resolve("write.r3"), """
                Rebol []
                s: import 'sqlite
                db: s/open %kept.db
                s/exec db {create table t(a); insert into t values ('kept')}
                """);
        Files.writeString(directory.resolve("read.r3"), """
                Rebol []
                s: import 'sqlite
                db: s/open %kept.db
                print mold s/eval db {select a from t}
                """);

        run(true, "write.r3");
        Ran ran = run(true, "read.r3");

        assertThat(ran.errors()).isEmpty();
        assertThat(ran.out()).containsExactly("""
                ["kept"]""");
    }
}

package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class LaunchingOnAJavaTooOldEndToEndTest {

    private static final String REFUSAL = "JEBOL needs Java 25 or later; this is Java 21.";

    private final Path installedLauncher = Path.of(System.getProperty("jebol.installedLauncher"));
    private final Path shippedJar = Path.of(System.getProperty("jebol.shippedJar"));
    private final Path olderJava = Path.of(System.getProperty("jebol.olderJavaHome"));
    private final Path supportedJava = Path.of(System.getProperty("jebol.supportedJavaHome"));

    private record Finished(int exitCode, String standardOutput, String standardError) {
    }

    private Finished launch(List<String> command, Path javaHome) throws IOException, InterruptedException {
        ProcessBuilder starting = new ProcessBuilder(command);
        Map<String, String> environment = starting.environment();
        environment.put("JAVA_HOME", javaHome.toString());
        environment.put("PATH", javaHome.resolve("bin") + ":" + environment.getOrDefault("PATH", ""));
        Process running = starting.start();
        running.getOutputStream().close();
        String output = new String(running.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String errors = new String(running.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Finished(running.waitFor(), output, errors);
    }

    private String javaIn(Path javaHome) {
        return javaHome.resolve("bin/java").toString();
    }

    @Test
    @DisplayName("the installed jebol on Java 21 says it needs 25 and stops with 1")
    void theInstalledLauncherTellsJavaTwentyOne() throws Exception {
        Finished finished = launch(List.of(installedLauncher.toString(), "--do", "print 1 + 2"), olderJava);

        assertThat(finished.exitCode()).isEqualTo(1);
        assertThat(finished.standardError()).contains(REFUSAL).doesNotContain("UnsupportedClassVersionError");
        assertThat(finished.standardOutput()).doesNotContain("3");
    }

    @Test
    @DisplayName("java -jar on Java 21 says the same and stops with 1")
    void theJarTellsJavaTwentyOne() throws Exception {
        Finished finished = launch(List.of(javaIn(olderJava), "-jar", shippedJar.toString(), "--do", "print 1 + 2"),
                olderJava);

        assertThat(finished.exitCode()).isEqualTo(1);
        assertThat(finished.standardError()).contains(REFUSAL).doesNotContain("UnsupportedClassVersionError");
    }

    @Test
    @DisplayName("the installed jebol on Java 25 runs what it was given")
    void theInstalledLauncherRunsOnTwentyFive() throws Exception {
        Finished finished = launch(List.of(installedLauncher.toString(), "--do", "print 1 + 2"), supportedJava);

        assertThat(finished.exitCode()).isZero();
        assertThat(finished.standardOutput()).contains("3");
        assertThat(finished.standardError()).doesNotContain("needs Java");
    }

    @Test
    @DisplayName("java -jar on Java 25 runs what it was given, and passes its exit code through")
    void theJarRunsOnTwentyFive() throws Exception {
        Finished printed = launch(List.of(javaIn(supportedJava), "-jar", shippedJar.toString(), "--do", "print 1 + 2"),
                supportedJava);
        Finished quitting = launch(List.of(javaIn(supportedJava), "-jar", shippedJar.toString(), "--do", "quit/return 7"),
                supportedJava);

        assertThat(printed.exitCode()).isZero();
        assertThat(printed.standardOutput()).contains("3");
        assertThat(quitting.exitCode()).isEqualTo(7);
    }
}

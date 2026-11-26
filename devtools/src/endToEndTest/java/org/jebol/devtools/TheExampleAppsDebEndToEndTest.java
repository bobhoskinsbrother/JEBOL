package org.jebol.devtools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("opens-a-real-window")
@Timeout(600)
@EnabledOnOs(value = OS.LINUX, disabledReason = "jpackage builds a .deb only on Linux")
class TheExampleAppsDebEndToEndTest {

    private static final Duration PATIENCE = Duration.ofSeconds(60);

    @TempDir
    Path scratch;

    private Process app;

    @AfterEach
    void closeTheApp() {
        if (app != null) {
            app.destroyForcibly();
        }
    }

    private static String run(String... command) throws IOException, InterruptedException {
        Process running = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(running.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(running.waitFor()).as(String.join(" ", command) + "\n" + output).isZero();
        return output;
    }

    private static void waitFor(String what, java.util.function.BooleanSupplier done) throws InterruptedException {
        long giveUpAt = System.nanoTime() + PATIENCE.toNanos();
        while (!done.getAsBoolean()) {
            assertThat(System.nanoTime()).as("waited " + PATIENCE + " for " + what).isLessThan(giveUpAt);
            Thread.sleep(200);
        }
    }

    @Test
    @DisplayName("the example's .deb, unpacked anywhere, starts on its own Java with none on the path, opens its window and keeps a store under its identity")
    void theDebRunsOnItsOwn() throws Exception {
        TheExampleApp example = TheExampleApp.copiedInto(scratch);
        example.run("packageDeb");
        Path deb;
        try (Stream<Path> made = Files.list(example.directory().resolve("build/jebol/linux/package"))) {
            deb = made.filter(path -> path.toString().endsWith(".deb")).findFirst().orElseThrow();
        }
        Path root = scratch.resolve("root");
        run("dpkg-deb", "-x", deb.toString(), root.toString());
        Path launcher = root.resolve("opt/" + TheExampleApp.IDENTITY + "/bin/notes");
        Path dataHome = scratch.resolve("data");

        ProcessBuilder starting = new ProcessBuilder(launcher.toString()).redirectErrorStream(true);
        Map<String, String> environment = starting.environment();
        environment.clear();
        environment.put("PATH", "/usr/bin:/bin");
        environment.put("HOME", scratch.resolve("home").toString());
        environment.put("XDG_DATA_HOME", dataHome.toString());
        for (String passed : List.of("DISPLAY", "XAUTHORITY", "WAYLAND_DISPLAY", "XDG_RUNTIME_DIR")) {
            String value = System.getenv(passed);
            if (value != null) {
                environment.put(passed, value);
            }
        }
        app = starting.start();
        List<String> lines = new CopyOnWriteArrayList<>();
        Thread reading = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(app.getInputStream(), StandardCharsets.UTF_8))) {
                reader.lines().forEach(lines::add);
            } catch (IOException closed) {
                lines.add("closed: " + closed.getMessage());
            }
        });
        reading.setDaemon(true);
        reading.start();

        waitFor("the app to say it is open, having said " + lines, () -> lines.contains("open"));
        waitFor("a window titled " + TheExampleApp.TITLE, () -> windowList().contains('"' + TheExampleApp.TITLE + '"'));
        assertThat(app.isAlive()).isTrue();
        assertThat(dataHome.resolve(TheExampleApp.IDENTITY)).isDirectory();
        assertThat(lines).noneMatch(line -> line.contains("Error"));
    }

    private static String windowList() {
        try {
            return run("xwininfo", "-root", "-tree");
        } catch (IOException | InterruptedException unlisted) {
            return "";
        }
    }
}

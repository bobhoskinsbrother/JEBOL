package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class APackagedAppKeepsAStoreOverItsCarriedFilesEndToEndTest {

    private static final String IDENTITY = "com.example.notes";

    private final Path shippedJar = Path.of(System.getProperty("jebol.shippedJar"));
    private final Path javaHome = Path.of(System.getProperty("jebol.java25Home"));

    @TempDir
    Path scratch;

    private record Finished(int exitCode, String standardOutput, String standardError) {
    }

    private Path carried() throws IOException {
        Path carried = Files.createDirectories(scratch.resolve("installed/jebol-app"));
        Files.writeString(carried.resolve("data.txt"), "carried");
        Files.createDirectories(carried.resolve("start"));
        Files.writeString(carried.resolve("start/notes.reb"), """
                REBOL [title: "Notes"]
                print ["where:" what-dir]
                print ["read:" read/string %/data.txt]
                write %/data.txt "changed"
                write %/notes.txt "kept"
                print ["again:" read/string %/data.txt]
                print ["listed:" mold sort read %/]
                """);
        return carried;
    }

    private Finished launch(Path carried, Path dataHome, String entry) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(javaHome.resolve("bin/java").toString(), "-jar",
                shippedJar.toString(), "--packaged", IDENTITY, "--carried", carried.toString(), entry));
        ProcessBuilder starting = new ProcessBuilder(command).directory(scratch.toFile());
        Map<String, String> environment = starting.environment();
        environment.put("XDG_DATA_HOME", dataHome.toString());
        environment.put("HOME", scratch.resolve("home").toString());
        Process running = starting.start();
        running.getOutputStream().close();
        String output = new String(running.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String errors = new String(running.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Finished(running.waitFor(), output, errors);
    }

    @Test
    @DisplayName("a packaged app starts from its entry, reads its carried files, and writes into a store of its own named by its identity")
    void aPackagedAppWritesIntoItsStore() throws Exception {
        Path carried = carried();
        Path dataHome = scratch.resolve("data");

        Finished finished = launch(carried, dataHome, "start/notes.reb");

        assertThat(finished.standardError()).isEmpty();
        assertThat(finished.exitCode()).isZero();
        assertThat(finished.standardOutput())
                .contains("where: /start/")
                .contains("read: carried")
                .contains("again: changed")
                .contains("listed: [%.rebol/ %data.txt %notes.txt %start/]");
        Path store = dataHome.resolve(IDENTITY);
        assertThat(store.resolve("data.txt")).hasContent("changed");
        assertThat(store.resolve("notes.txt")).hasContent("kept");
    }

    @Test
    @DisplayName("the carried files are never changed, and the next launch finds what the last one wrote")
    void theCarriedFilesAreNeverChanged() throws Exception {
        Path carried = carried();
        Path dataHome = scratch.resolve("data");

        launch(carried, dataHome, "start/notes.reb");
        Finished second = launch(carried, dataHome, "start/notes.reb");

        assertThat(carried.resolve("data.txt")).hasContent("carried");
        assertThat(carried.resolve("notes.txt")).doesNotExist();
        assertThat(second.standardOutput()).contains("read: changed");
    }

    @Test
    @DisplayName("with no XDG_DATA_HOME, the store is in ~/.local/share")
    void theStoreDefaultsToLocalShare() throws Exception {
        Path carried = carried();
        List<String> command = List.of(javaHome.resolve("bin/java").toString(), "-jar", shippedJar.toString(),
                "--packaged", IDENTITY, "--carried", carried.toString(), "start/notes.reb");
        ProcessBuilder starting = new ProcessBuilder(command).directory(scratch.toFile());
        starting.environment().remove("XDG_DATA_HOME");
        starting.environment().put("HOME", scratch.resolve("home").toString());
        Process running = starting.start();
        running.getOutputStream().close();
        running.getInputStream().readAllBytes();
        running.waitFor();

        assertThat(scratch.resolve("home/.local/share/" + IDENTITY + "/notes.txt")).hasContent("kept");
    }
}

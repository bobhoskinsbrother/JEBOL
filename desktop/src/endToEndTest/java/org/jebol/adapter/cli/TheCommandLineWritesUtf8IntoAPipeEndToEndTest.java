package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TheCommandLineWritesUtf8IntoAPipeEndToEndTest {

    private static final int PROCESS_TIMEOUT_SECONDS = 60;

    private static byte[] runWithTheCLocale(Path directory, String doing, String stream) throws Exception {
        Path script = directory.resolve("script.r3");
        Files.writeString(script, "Rebol []\n" + doing + "\n", StandardCharsets.UTF_8);
        Path output = directory.resolve(stream + ".bin");
        ProcessBuilder builder = new ProcessBuilder(List.of(
                ProcessHandle.current().info().command().orElseThrow(),
                "-cp", System.getProperty("java.class.path"),
                Repl.class.getName(), script.toString()))
                .directory(directory.toFile());
        builder.environment().put("LC_ALL", "C");
        builder.environment().put("LANG", "C");
        if (stream.equals("out")) {
            builder.redirectOutput(output.toFile()).redirectError(directory.resolve("ignored.bin").toFile());
        } else {
            builder.redirectError(output.toFile()).redirectOutput(directory.resolve("ignored.bin").toFile());
        }
        Process process = builder.start();
        if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("the console was still running after " + PROCESS_TIMEOUT_SECONDS + " seconds");
        }
        return Files.readAllBytes(output);
    }

    @Test
    @DisplayName("what a script prints into a pipe is UTF-8 whatever the locale says, as R3 writes it")
    void writesUtf8IntoAPipe(@TempDir Path directory) throws Exception {
        byte[] written = runWithTheCLocale(directory, "prin {á🙂}", "out");

        assertThat(written).isEqualTo("á🙂".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("and so is what it writes to the error stream")
    void writesUtf8ErrorsIntoAPipe(@TempDir Path directory) throws Exception {
        byte[] written = runWithTheCLocale(directory, "do make error! {á🙂}", "err");

        assertThat(new String(written, StandardCharsets.UTF_8)).contains("á🙂");
    }
}

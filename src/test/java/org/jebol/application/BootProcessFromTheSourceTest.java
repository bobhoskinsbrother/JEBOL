package org.jebol.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import org.jebol.adapter.host.JavaProcesses;
import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The whole journey a person takes: {@code system/options/boot} names the
 * runnable this interpreter was started from, a shell runs it with {@code --do},
 * and the status the child left with comes back as the answer. Rebol's own
 * {@code evaluation-test.r3} writes these two calls exactly this way, and they
 * are the only assertions in the suite that leave the process.
 */
class BootProcessFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = interpreterWithAHost();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static Interpreter interpreterWithAHost() {
        Bounds bounds = Bounds.standard();
        for (HostService service : HostService.values()) {
            bounds = bounds.granting(service);
        }
        Interpreter interpreter = Interpreter.withBounds(bounds);
        try {
            interpreter.useFileSystem(FileSystemPort.rootedAt(
                    Files.createTempDirectory("jebol-boot")));
        } catch (IOException noDirectory) {
            throw new UncheckedIOException(noDirectory);
        }
        interpreter.useEnvironment(new ProcessEnvironment());
        interpreter.useProcesses(new JavaProcesses());
        return interpreter;
    }

    @Test
    @DisplayName("the boot option names a file a script can hand to the shell")
    void theBootOptionIsAFile() {
        assertThat(answerTo("""
                file? system/options/boot""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a child asked to quit leaves with a status of zero")
    void aChildThatQuitsLeavesWithZero() {
        assertThat(answerTo("""
                0 = call/shell/wait append to-local-file system/options/boot
                    { --do "quit"}""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and one asked to quit with a number leaves with that number")
    void aChildThatQuitsWithANumberReportsIt() {
        assertThat(answerTo("""
                100 = call/shell/wait append to-local-file system/options/boot
                    { --do "quit/return 100"}""")).isEqualTo("#(true)");
    }
}

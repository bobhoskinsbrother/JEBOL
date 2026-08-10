package org.jebol.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reading and writing files needs the FILES grant and a real adapter.
 *
 * <p>Specified in {@code spec/embed.allium}.
 *
 * <p>Two separate things must both hold: the host must grant the kind, and
 * the host must supply somewhere for the reading to go. A grant with no
 * adapter reaches nothing, and an adapter with no grant is not asked.
 */
class FileServiceGrantTest {

    private static String errorIdOf(Interpreter interpreter, String source) {
        String wrapped = "e: try [" + source + "] either error? e [e/id] ['no-error]";
        interpreter.defineFreshWordsIn(wrapped);
        return interpreter.display(interpreter.run(wrapped));
    }

    /** An interpreter that reads and writes inside one directory. */
    private static Interpreter readingUnder(Bounds bounds, Path directory) {
        Interpreter interpreter = Interpreter.withBounds(bounds);
        interpreter.useFileSystem(FileSystemPort.rootedAt(directory));
        return interpreter;
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("without the grant, reading raises no-service")
    void theGrantIsNeeded(@TempDir Path directory) {
        Interpreter interpreter = readingUnder(Bounds.standard(), directory);
        assertThat(errorIdOf(interpreter, "read %a.txt")).isEqualTo("no-service");
    }

    @Test
    @DisplayName("with the grant and an adapter, a file can be written and read")
    void theWholePathWorks(@TempDir Path directory) {
        Interpreter interpreter = readingUnder(
                Bounds.standard().granting(HostService.FILES), directory);
        assertThat(answerTo(interpreter, "write %a.txt \"hello\" read %a.txt"))
                .isEqualTo("\"hello\"");
    }

    @Test
    @DisplayName("EXISTS? answers before and after a write")
    void existsFollowsTheWrite(@TempDir Path directory) {
        Interpreter interpreter = readingUnder(
                Bounds.standard().granting(HostService.FILES), directory);
        assertThat(answerTo(interpreter,
                "before: exists? %b.txt write %b.txt \"x\" reduce [before exists? %b.txt]"))
                .isEqualTo("[#(false) #(true)]");
    }

    @Test
    @DisplayName("a relative path counts from the interpreter's own directory")
    void thePathIsRelativeToTheAdapter(@TempDir Path directory) throws Exception {
        Interpreter interpreter = readingUnder(
                Bounds.standard().granting(HostService.FILES), directory);
        answerTo(interpreter, "write %c.txt \"there\"");
        assertThat(Files.readString(directory.resolve("c.txt"))).isEqualTo("there");
    }

    @Test
    @DisplayName("a path outside the directory is refused")
    void theAdapterKeepsTheScriptInside(@TempDir Path directory) {
        // The port holds the directory, thus a script cannot reach above
        // it by asking for a path with two dots in it.
        Interpreter interpreter = readingUnder(
                Bounds.standard().granting(HostService.FILES), directory);
        assertThat(errorIdOf(interpreter, "read %../secret.txt")).isEqualTo("outside-root");
    }

    @Test
    @DisplayName("reading a file that is not there raises")
    void theMissingFileRaises(@TempDir Path directory) {
        Interpreter interpreter = readingUnder(
                Bounds.standard().granting(HostService.FILES), directory);
        assertThat(errorIdOf(interpreter, "read %nothing.txt")).isEqualTo("cannot-open");
    }

    @Test
    @DisplayName("with the grant and no adapter, reading still raises")
    void anAdapterIsAlsoNeeded() {
        // The grant says the host is willing. It does not say where the
        // reading goes.
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.FILES));
        assertThat(errorIdOf(interpreter, "read %a.txt")).isNotEqualTo("no-error");
    }
}

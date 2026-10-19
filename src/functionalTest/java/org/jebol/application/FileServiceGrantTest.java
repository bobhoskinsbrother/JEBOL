package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileServiceGrantTest {

    private static String readTheErrorIdOf(Interpreter interpreter, String source) {
        String wrapped = "e: try [" + source + "] either error? e [e/id] ['no-error]";
        interpreter.defineFreshWordsIn(wrapped);
        return interpreter.display(interpreter.run(wrapped));
    }

    private static Interpreter makeAnInterpreterReadingUnder(Bounds bounds, Path directory) {
        Interpreter interpreter = Interpreter.withBounds(bounds);
        interpreter.useFileSystem(FileSystemPort.rootFileSystem(directory));
        return interpreter;
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("without the grant, reading raises no-service")
    void needsTheGrant(@TempDir Path directory) {
        Interpreter interpreter = makeAnInterpreterReadingUnder(Bounds.standard(), directory);
        assertThat(readTheErrorIdOf(interpreter, "read %a.txt")).isEqualTo("no-service");
    }

    @Test
    @DisplayName("with the grant and an adapter, a file can be written and read")
    void readsTheWholePath(@TempDir Path directory) {
        Interpreter interpreter = makeAnInterpreterReadingUnder(
                Bounds.standard().grant(HostService.FILES), directory);
        assertThat(answerTo(interpreter, "write %a.txt {hello} read/string %a.txt"))
                .isEqualTo("\"hello\"");
    }

    @Test
    @DisplayName("EXISTS? answers before and after a write")
    void existsFollowsTheWrite(@TempDir Path directory) {
        Interpreter interpreter = makeAnInterpreterReadingUnder(
                Bounds.standard().grant(HostService.FILES), directory);
        assertThat(answerTo(interpreter,
                "before: exists? %b.txt write %b.txt \"x\" "
                + "reduce [true? before  true? exists? %b.txt]"))
                .isEqualTo("[#(false) #(true)]");
    }

    @Test
    @DisplayName("a relative path counts from the interpreter's own directory")
    void readsThePathRelativeToTheAdapter(@TempDir Path directory) throws Exception {
        Interpreter interpreter = makeAnInterpreterReadingUnder(
                Bounds.standard().grant(HostService.FILES), directory);
        answerTo(interpreter, "write %c.txt \"there\"");
        assertThat(Files.readString(directory.resolve("c.txt"))).isEqualTo("there");
    }

    @Test
    @DisplayName("a path climbing out of the directory reaches nothing inside it")
    void keepsTheScriptInsideTheAdapter(@TempDir Path directory) throws Exception {
        Interpreter interpreter = makeAnInterpreterReadingUnder(
                Bounds.standard().grant(HostService.FILES), directory);
        Files.writeString(directory.getParent().resolve("secret.txt"), "not yours");
        assertThat(readTheErrorIdOf(interpreter, "read %../secret.txt")).isEqualTo("cannot-open");
    }

    @Test
    @DisplayName("reading a file that is not there raises")
    void raisesForAMissingFile(@TempDir Path directory) {
        Interpreter interpreter = makeAnInterpreterReadingUnder(
                Bounds.standard().grant(HostService.FILES), directory);
        assertThat(readTheErrorIdOf(interpreter, "read %nothing.txt")).isEqualTo("cannot-open");
    }

    @Test
    @DisplayName("with the grant and no adapter, reading still raises")
    void needsAnAdapterToo() {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().grant(HostService.FILES));
        assertThat(readTheErrorIdOf(interpreter, "read %a.txt")).isNotEqualTo("no-error");
    }
}

package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class APortNeedsAnObjectForItsSpecFromTheSourceTest {

    private static Interpreter reaching(Path directory) throws IOException {
        Files.writeString(directory.resolve("held.txt"), "carried");
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.FILES));
        interpreter.useFileSystem(FileSystemPort.rootedAt(directory));
        return interpreter;
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String failureOf(Interpreter interpreter, String source) {
        return answerTo(interpreter, "e: try [" + source + """
                ]
                either error? e [reduce [e/type e/id]] [reduce ['ok]]""");
    }

    @Test
    @DisplayName("closing a port whose spec was overwritten is an invalid port")
    void closingAPortWhoseSpecWasOverwrittenIsInvalid(@TempDir Path directory)
            throws IOException {
        assertThat(failureOf(reaching(directory), """
                held: open %held.txt
                held/spec: 5
                close held""")).isEqualTo("[Access invalid-port]");
    }

    @Test
    @DisplayName("and so is reading it or writing to it, because the port is what is wrong")
    void everyVerbAnswersTheSameWay(@TempDir Path directory) throws IOException {
        Interpreter interpreter = reaching(directory);
        assertThat(answerTo(interpreter, """
                broken: does [held: open %held.txt held/spec: 5 held]
                idOf: func [e] [either error? e [e/id] ['ok]]
                reduce [
                    idOf try [read broken]
                    idOf try [write broken {x}]
                    idOf try [close broken]
                ]""")).isEqualTo("[invalid-port invalid-port invalid-port]");
    }

    @Test
    @DisplayName("a spec that is none rather than an object is refused the same way")
    void aSpecThatIsNoneIsRefusedTheSameWay(@TempDir Path directory) throws IOException {
        assertThat(failureOf(reaching(directory), """
                held: open %held.txt
                held/spec: none
                read held""")).isEqualTo("[Access invalid-port]");
    }

    @Test
    @DisplayName("an untouched port still reads what it was opened on")
    void anUntouchedPortStillReads(@TempDir Path directory) throws IOException {
        assertThat(answerTo(reaching(directory), """
                held: open %held.txt
                text: to string! read held
                close held
                text = {carried}""")).isEqualTo("#(true)");
    }
}

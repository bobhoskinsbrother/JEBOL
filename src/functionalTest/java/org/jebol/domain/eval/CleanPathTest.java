package org.jebol.domain.eval;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CleanPathTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a single dot names the directory it is in, thus it goes")
    void dropsOneDot() {
        assertThat(answerTo("(clean-path/only %a/./b) = %a/b")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a double dot takes the part above it with it")
    void removesThePartBeforeTwoDots() {
        assertThat(answerTo("(clean-path/only %a/b/../c) = %a/c")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a double dot with nothing above it is dropped")
    void stopsAPathClimbingAboveItsStart() {
        assertThat(answerTo("(clean-path/only %../a) = %a")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("two slashes together are one slash")
    void dropsTheEmptyPart() {
        assertThat(answerTo("(clean-path/only %a//b) = %a/b")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/DIR puts a slash at the end")
    void addsTheDirectorySlash() {
        assertThat(answerTo("(clean-path/only/dir %a/b) = %a/b/")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a path with nothing to work out is unchanged")
    void leavesTheOrdinaryPathAlone() {
        assertThat(answerTo("(clean-path/only %a/b) = %a/b")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a path that starts at the root keeps its first slash")
    void keepsTheRoot() {
        assertThat(answerTo("(clean-path/only %/a/./b) = %/a/b")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("without /ONLY the current directory is needed")
    void needsTheWorkingDirectoryGrant() {
        String source = "e: try [clean-path %a/b] either error? e [e/id] ['no-error]";
        Interpreter interpreter = Interpreter.withBounds(Bounds.standard());
        interpreter.defineNewWordsIn(source);
        assertThat(interpreter.display(interpreter.run(source))).isEqualTo("no-service");
    }

    @Test
    @DisplayName("with the grant, a relative path is made whole")
    void putsTheCurrentDirectoryInFront() {
        String source = "#\"/\" = first clean-path %a/b";
        Interpreter interpreter = Interpreter.createOn(
                org.jebol.application.Interpreter.standardPorts().withFiles(
                        org.jebol.application.FileSystemPort.rootFileSystem(
                                java.nio.file.Path.of("."))),
                org.jebol.domain.host.OutputPort.discard(),
                Bounds.standard().grant(HostService.WORKING_DIRECTORY));
        interpreter.defineNewWordsIn(source);
        assertThat(interpreter.display(interpreter.run(source))).isEqualTo("#(true)");
    }
}

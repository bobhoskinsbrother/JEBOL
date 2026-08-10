package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CLEAN-PATH works out the dots and the double slashes in a path.
 *
 * <p>Specified in {@code spec/natives.allium} and measured against a real
 * R3 3.22.1.
 *
 * <p>/ONLY leaves a relative path relative. Without it the path is put
 * after the current directory, which needs the working directory grant.
 */
class CleanPathTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a single dot names the directory it is in, thus it goes")
    void oneDotIsDropped() {
        assertThat(answerTo("(clean-path/only %a/./b) = %a/b")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a double dot takes the part above it with it")
    void twoDotsRemoveThePartBefore() {
        assertThat(answerTo("(clean-path/only %a/b/../c) = %a/c")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a double dot with nothing above it is dropped")
    void aPathCannotClimbAboveItsStart() {
        assertThat(answerTo("(clean-path/only %../a) = %a")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("two slashes together are one slash")
    void theEmptyPartGoes() {
        assertThat(answerTo("(clean-path/only %a//b) = %a/b")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/DIR puts a slash at the end")
    void theDirectorySlashIsAdded() {
        assertThat(answerTo("(clean-path/only/dir %a/b) = %a/b/")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a path with nothing to work out is unchanged")
    void theOrdinaryPathIsLeftAlone() {
        assertThat(answerTo("(clean-path/only %a/b) = %a/b")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a path that starts at the root keeps its first slash")
    void theRootIsKept() {
        assertThat(answerTo("(clean-path/only %/a/./b) = %/a/b")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("without /ONLY the current directory is needed")
    void theWorkingDirectoryGrantIsNeeded() {
        // A relative path is put after the current directory, thus a host
        // that did not grant the working directory gets a refusal.
        String source = "e: try [clean-path %a/b] either error? e [e/id] ['no-error]";
        Interpreter interpreter = Interpreter.withBounds(Bounds.standard());
        interpreter.defineFreshWordsIn(source);
        assertThat(interpreter.display(interpreter.run(source))).isEqualTo("no-service");
    }

    @Test
    @DisplayName("with the grant, a relative path is made whole")
    void theCurrentDirectoryGoesInFront() {
        String source = "#\"/\" = first clean-path %a/b";
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.WORKING_DIRECTORY));
        interpreter.useFileSystem(org.jebol.application.FileSystemPort.rootedAt(
                java.nio.file.Path.of(".")));
        interpreter.defineFreshWordsIn(source);
        assertThat(interpreter.display(interpreter.run(source))).isEqualTo("#(true)");
    }
}

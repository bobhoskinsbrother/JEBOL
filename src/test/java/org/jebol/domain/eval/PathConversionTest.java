package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SPLIT-PATH, TO-LOCAL-FILE and TO-REBOL-FILE, ported off the backlog.
 *
 * <p>Specified in {@code spec/natives.allium} and measured against a real
 * R3 3.22.1.
 *
 * <p>A REBOL path uses a slash between the parts on every machine. A local
 * path uses whatever the machine uses.
 */
class PathConversionTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("SPLIT-PATH gives the directory and the name")
    void aPathSplitsAtItsLastSlash() {
        assertThat(answerTo("(split-path %a/b/c.txt) = [%a/b/ %c.txt]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a name with no slash sits in the current directory")
    void theDirectoryIsNeverEmpty() {
        assertThat(answerTo("(split-path %c.txt) = [%./ %c.txt]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a path that names a directory keeps its slash on the name")
    void theNameKeepsItsSlash() {
        assertThat(answerTo("(split-path %a/b/) = [%a/ %b/]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a directory with no parent above it")
    void theDegenerateDirectory() {
        assertThat(answerTo("(split-path %b/) = [%./ %b/]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("SPLIT-PATH does not change the path it was given")
    void theAnswerIsACopy() {
        assertThat(answerTo("p: %a/b.txt split-path p p = %a/b.txt")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("TO-REBOL-FILE answers a file and TO-LOCAL-FILE answers a string")
    void theTwoAnswerDifferentTypes() {
        assertThat(answerTo("file? to-rebol-file \"a/b.txt\"")).isEqualTo("#(true)");
        assertThat(answerTo("string? to-local-file %a/b.txt")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the two undo each other")
    void theRoundTripHolds() {
        assertThat(answerTo("(to-rebol-file to-local-file %a/b.txt) = %a/b.txt"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a path that starts at the root keeps its first slash")
    void theRootIsKept() {
        assertThat(answerTo("(to-rebol-file \"/a/b.txt\") = %/a/b.txt")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a path of only slashes and dots is a directory with no name")
    void theWholePathCanBeTheDirectory() {
        assertThat(answerTo("(split-path %/) = [%/ %\"\"]")).isEqualTo("#(true)");
        assertThat(answerTo("(split-path %./) = [%./ %\"\"]")).isEqualTo("#(true)");
        assertThat(answerTo("(split-path %../) = [%../ %\"\"]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a bare name has no directory above it")
    void aBareNameSitsInThisDirectory() {
        assertThat(answerTo("(split-path %dir) = [%./ %dir]")).isEqualTo("#(true)");
        assertThat(answerTo("(split-path %dir/) = [%./ %dir/]")).isEqualTo("#(true)");
    }
}

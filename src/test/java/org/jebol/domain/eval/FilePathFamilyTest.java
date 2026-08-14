package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DIRIZE, UNDIRIZE, SUFFIX? and DIR?, ported off the porting backlog.
 *
 * <p>Specified in {@code spec/natives.allium} and measured against a real
 * R3 3.22.1, whose own definitions were read out of the binary.
 *
 * <p>Each of the first two answers a copy. None of them changes the path
 * that the caller holds.
 */
class FilePathFamilyTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("DIRIZE puts a slash at the end")
    void dirizeAddsTheSlash() {
        assertThat(answerTo("(dirize %a/b) = %a/b/")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("DIRIZE leaves a path that already has one")
    void dirizeAddsNoSecondSlash() {
        assertThat(answerTo("(dirize %a/b/) = %a/b/")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("DIRIZE of an empty path gives a slash")
    void theDegenerateDirize() {
        assertThat(answerTo("(dirize %\"\") = %/")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("UNDIRIZE takes the slash off")
    void undirizeRemovesTheSlash() {
        assertThat(answerTo("(undirize %a/b/) = %a/b")).isEqualTo("#(true)");
        assertThat(answerTo("(undirize %a/b) = %a/b")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("neither one changes the path it was given")
    void bothAnswerACopy() {
        assertThat(answerTo("p: %a/b dirize p p = %a/b")).isEqualTo("#(true)");
        assertThat(answerTo("p: %a/b/ undirize p p = %a/b/")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("SUFFIX? gives the suffix with its dot")
    void theSuffixKeepsItsDot() {
        assertThat(answerTo("(suffix? %a/b.txt) = %.txt")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a dot in a directory name is not a suffix")
    void theDotMustFollowTheLastSlash() {
        assertThat(answerTo("none? suffix? %a.b/c")).isEqualTo("#(true)");
        assertThat(answerTo("none? suffix? %a/b")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("DIR? asks whether the path ends with a slash")
    void aDirectoryEndsWithASlash() {
        assertThat(answerTo("dir? %a/b/")).isEqualTo("#(true)");
        assertThat(answerTo("dir? %a/b")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("DIR? of none is false, and not none")
    void theAnswerIsAlwaysALogic() {
        assertThat(answerTo("dir? none")).isEqualTo("#(false)");
        assertThat(answerTo("logic? dir? none")).isEqualTo("#(true)");
    }
}

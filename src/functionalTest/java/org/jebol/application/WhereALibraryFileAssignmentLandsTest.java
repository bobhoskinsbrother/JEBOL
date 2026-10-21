package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhereALibraryFileAssignmentLandsTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("A module's own EXP does not replace the library's native of that name")
    void keepsTheLibraryWordBesideAModulesOwn() {
        assertThat(answerTo("type? :exp")).isEqualTo("#(native!)");
        assertThat(answerTo("type? :stack")).isEqualTo("#(native!)");
    }

    @Test
    @DisplayName("The module that shadows EXP still works through its own copy")
    void stillRunsTheModuleThroughItsOwnWord() {
        assertThat(answerTo("type? :to-json")).isEqualTo("#(function!)");
    }

    @Test
    @DisplayName("A system file's words are defined in sys and not in lib")
    void definesASystemFilesWordsInSysNotLib() {
        assertThat(answerTo("""
                not none? find words-of system/contexts/sys 'make-scheme"""))
                .isEqualTo("#(true)");
        assertThat(answerTo("""
                none? find words-of system/contexts/lib 'make-scheme"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("Every borrowed file still loads to the end")
    void stillLoadsEveryBorrowedFileToTheEnd() {
        assertThat(AnInterpreter.create().borrowedLoadFailures()).isEmpty();
    }
}

package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FIND-SCRIPT and SCRIPT?, ported off the porting backlog.
 *
 * <p>Specified in {@code spec/load.allium} and measured against a real R3
 * 3.22.1.
 *
 * <p>A header must begin a line. Only spaces can come before it, so text
 * that holds the word REBOL in the middle of a line has no header.
 */
class FindScriptTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a header at the start is found")
    void aHeaderAtTheStart() {
        assertThat(answerTo("(find-script to binary! \"rebol [] 1\") = to binary! \"rebol [] 1\""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("spaces and lines before it are permitted")
    void spacesBeforeAreAllowed() {
        assertThat(answerTo("true? find-script to binary! \"   rebol [] 1\""))
                .isEqualTo("#(true)");
        assertThat(answerTo("true? find-script to binary! \"^/rebol [] 1\""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a byte order mark counts as a space")
    void theMarkIsNotPartOfTheText() {
        assertThat(answerTo("true? find-script to binary! \"^(FEFF)rebol [] 1\""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("other text on the same line means there is no header")
    void theHeaderMustBeginALine() {
        assertThat(answerTo("none? find-script to binary! \"xx rebol [] 1\""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the case of the word does not matter")
    void anyCaseIsAHeader() {
        assertThat(answerTo("true? find-script to binary! \"REBOL [] 1\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a bracket must follow, after any number of spaces")
    void aBracketMustFollow() {
        assertThat(answerTo("none? find-script to binary! \"rebol 1\"")).isEqualTo("#(true)");
        assertThat(answerTo("true? find-script to binary! \"rebol  [] 1\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("text with no header, and no text at all, both give none")
    void theDegenerateSources() {
        assertThat(answerTo("none? find-script to binary! \"1 + 1\"")).isEqualTo("#(true)");
        assertThat(answerTo("none? find-script to binary! \"\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("SCRIPT? takes a string as readily as a binary")
    void scriptTakesEither() {
        assertThat(answerTo("true? script? \"rebol [] 1\"")).isEqualTo("#(true)");
        assertThat(answerTo("none? script? \"1 + 1\"")).isEqualTo("#(true)");
    }
}

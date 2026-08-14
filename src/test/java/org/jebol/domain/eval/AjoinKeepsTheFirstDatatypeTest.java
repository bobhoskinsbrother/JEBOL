package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AJOIN takes its datatype from the first value.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>Answering a string for everything is right for most inputs and wrong
 * for the three that matter, and nothing about the text gives it away --
 * {@code %ab} and {@code "ab"} differ only in what they are.
 */
class AjoinKeepsTheFirstDatatypeTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a file first gives a file")
    void aFileGivesAFile() {
        assertThat(answerTo("mold ajoin [%a \"b\" 3]")).isEqualTo("\"%ab3\"");
    }

    @Test
    @DisplayName("a url and an email keep theirs too")
    void urlsAndEmailsKeepTheirs() {
        assertThat(answerTo("mold type? ajoin [http://x \"b\"]")).isEqualTo("\"#(url!)\"");
        assertThat(answerTo("mold type? ajoin [a@b \"c\"]")).isEqualTo("\"#(email!)\"");
    }

    @Test
    @DisplayName("a tag gives a string, brackets and all")
    void aTagGivesAString() {
        assertThat(answerTo("(ajoin [<a> \"b\" 3]) = \"<a>b3\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a word, a character or a number first gives a string")
    void everythingElseGivesAString() {
        assertThat(answerTo("mold type? ajoin ['a 'b 3]")).isEqualTo("\"#(string!)\"");
        assertThat(answerTo("mold type? ajoin [#\"a\" %b]")).isEqualTo("\"#(string!)\"");
        assertThat(answerTo("mold type? ajoin [3 %b]")).isEqualTo("\"#(string!)\"");
    }

    @Test
    @DisplayName("the first value decides even when it contributes nothing")
    void aLeadingNoneStillDecides() {
        assertThat(answerTo("mold type? ajoin [none %a 3]")).isEqualTo("\"#(string!)\"");
        assertThat(answerTo("mold ajoin [%a none 3]")).isEqualTo("\"%a3\"");
    }

    @Test
    @DisplayName("an empty block gives an empty string")
    void anEmptyBlockIsTheDegenerateCase() {
        assertThat(answerTo("(ajoin []) = \"\"")).isEqualTo("#(true)");
    }
}

package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AjoinKeepsTheFirstDatatypeTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a file first gives a file")
    void givesAFileForAFile() {
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
    void givesAStringForATag() {
        assertThat(answerTo("(ajoin [<a> \"b\" 3]) = \"<a>b3\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a word, a character or a number first gives a string")
    void givesAStringForEverythingElse() {
        assertThat(answerTo("mold type? ajoin ['a 'b 3]")).isEqualTo("\"#(string!)\"");
        assertThat(answerTo("mold type? ajoin [#\"a\" %b]")).isEqualTo("\"#(string!)\"");
        assertThat(answerTo("mold type? ajoin [3 %b]")).isEqualTo("\"#(string!)\"");
    }

    @Test
    @DisplayName("the first value decides even when it contributes nothing")
    void stillLetsALeadingNoneDecide() {
        assertThat(answerTo("mold type? ajoin [none %a 3]")).isEqualTo("\"#(string!)\"");
        assertThat(answerTo("mold ajoin [%a none 3]")).isEqualTo("\"%a3\"");
    }

    @Test
    @DisplayName("an empty block gives an empty string")
    void handlesAnEmptyBlock() {
        assertThat(answerTo("(ajoin []) = \"\"")).isEqualTo("#(true)");
    }
}

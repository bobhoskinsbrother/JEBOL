package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WildcardSearchTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a question mark stands for one character")
    void oneCharacterEach() {
        assertThat(answerTo("(select/any \"abcde\" \"b?d\") = #\"e\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a star stands for any run, including none")
    void aStarTakesWhateverIsThere() {
        assertThat(answerTo("(select/any \"abcde\" \"*d\") = #\"e\"")).isEqualTo("#(true)");
        assertThat(answerTo("(select/any \"abcde\" \"*?d\") = #\"e\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the star is not greedy")
    void theStarStopsAtTheFirstMatch() {
        assertThat(answerTo("(select/any \"abcde\" \"*d\") = #\"e\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/LAST with a shape takes the last place it fits")
    void theLastMatchWins() {
        assertThat(answerTo("(select/last/any \"ab1ab2\" \"?b\") = #\"2\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a needle full of punctuation is not a pattern of its own")
    void everythingElseIsLiteral() {
        assertThat(answerTo("none? select/any \"abc\" \"a.c\"")).isEqualTo("#(true)");
        assertThat(answerTo("(select/any \"a.cd\" \"a.c\") = #\"d\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("without /ANY the needle is literal")
    void theOrdinaryNeedleIsUnaffected() {
        assertThat(answerTo("none? select \"abcde\" \"b?d\"")).isEqualTo("#(true)");
        assertThat(answerTo("(select \"abcde\" \"bcd\") = #\"e\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("FIND/SAME on a string minds case")
    void findSameIsCaseSensitive() {
        assertThat(answerTo("(find/same \"aAbcdAe\" \"A\") = \"AbcdAe\"")).isEqualTo("#(true)");
        assertThat(answerTo("(find \"aAbcdAe\" \"A\") = \"aAbcdAe\""))
                .as("without it the small letter is found first")
                .isEqualTo("#(true)");
    }
}

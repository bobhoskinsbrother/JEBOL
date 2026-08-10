package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FIND/ANY, where {@code *} stands for any run and {@code ?} for one.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>How much the star takes depends on where it sits: a trailing star
 * takes the rest of the series, and a star with anything after it takes as
 * little as it can. Neither a wholly greedy star nor a wholly lazy one
 * fits, and the difference only shows in where the match ENDS, which is
 * what /TAIL stands after.
 */
class FindWithWildcardsTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a star matches a run and the search finds where it starts")
    void aStarFindsItsPosition() {
        assertThat(answerTo("find/any \"abcd\" \"c*\"")).isEqualTo("\"cd\"");
    }

    @Test
    @DisplayName("a trailing star takes the rest of the series")
    void aTrailingStarTakesEverythingLeft() {
        assertThat(answerTo("find/any/tail \"abcd\" \"c*\"")).isEqualTo("\"\"");
        assertThat(answerTo("find/any/tail \"abcd\" \"a*\"")).isEqualTo("\"\"");
    }

    @Test
    @DisplayName("a star with something after it takes as little as it can")
    void anInnerStarTakesTheLeast() {
        // Not the sixth character: the star gives back everything the
        // rest of the pattern does not need.
        assertThat(answerTo("find/any/tail \"abcabc\" \"*bc\"")).isEqualTo("\"abc\"");
        assertThat(answerTo("find/any/tail \"abcabc\" \"a*c\"")).isEqualTo("\"abc\"");
        assertThat(answerTo("find/any/tail \"abcdabcd\" \"b*d\"")).isEqualTo("\"abcd\"");
    }

    @Test
    @DisplayName("a question mark matches exactly one character")
    void aQuestionMarkMatchesOne() {
        assertThat(answerTo("find/any \"abcd\" \"a?c\"")).isEqualTo("\"abcd\"");
    }

    @Test
    @DisplayName("a star with something after it still matches")
    void aStarInTheMiddle() {
        assertThat(answerTo("find/any \"abcd\" \"a*d\"")).isEqualTo("\"abcd\"");
    }

    @Test
    @DisplayName("a star on its own matches from the head")
    void aLoneStarMatchesEverything() {
        assertThat(answerTo("find/any \"abcd\" \"*\"")).isEqualTo("\"abcd\"");
    }

    @Test
    @DisplayName("a pattern with no wildcard behaves as an ordinary search")
    void noWildcardIsOrdinary() {
        assertThat(answerTo("find/any \"abcd\" \"bc\"")).isEqualTo("\"bcd\"");
        assertThat(answerTo("find/tail \"abcd\" \"bc\"")).isEqualTo("\"d\"");
    }

    @Test
    @DisplayName("/reverse looks behind the position for a pattern")
    void reverseSearchesBehind() {
        // A series at its tail has the whole string behind it and nothing
        // ahead, so searching forwards answers none for all of these.
        assertThat(answerTo("find/any/reverse tail \"abcdabcd\" \"?c\""))
                .isEqualTo("\"bcd\"");
        assertThat(answerTo("find/any/reverse tail \"abcdabcd\" \"b*\""))
                .isEqualTo("\"bcd\"");
    }

    @Test
    @DisplayName("/reverse and /tail together measure the match from the head")
    void reverseWithTail() {
        assertThat(answerTo("find/any/reverse/tail tail \"abcdabcd\" \"?c\""))
                .isEqualTo("\"d\"");
        assertThat(answerTo("find/any/reverse/tail tail \"abcdabcd\" \"bc\""))
                .isEqualTo("\"d\"");
    }

    @Test
    @DisplayName("/reverse without wildcards is unaffected")
    void plainReverseIsUnaffected() {
        assertThat(answerTo("find/reverse tail \"abcdabcd\" \"bc\"")).isEqualTo("\"bcd\"");
        assertThat(answerTo("mold find/reverse tail \"abcd\" \"z\"")).isEqualTo("\"_\"");
    }

    @Test
    @DisplayName("/same asks a run to be the same values, not equal ones")
    void sameComparesARunStrictly() {
        String blk = "blk: [1.0 3 1 3 1.0 2.0 1 2] ";
        assertThat(answerTo(blk + "index? find blk [1 2]"))
                .as("loosely, the decimals at five match")
                .isEqualTo("5");
        assertThat(answerTo(blk + "index? find/same blk [1 2]"))
                .as("strictly, only the integers at seven do")
                .isEqualTo("7");
    }

    @Test
    @DisplayName("/same on a single value was already strict")
    void sameOnOneValueIsUnchanged() {
        assertThat(answerTo("index? find/same [1 1.0] 1.0")).isEqualTo("2");
        assertThat(answerTo("index? find/same [1.0 1] 1")).isEqualTo("2");
    }

    @Test
    @DisplayName("a pattern that matches nothing answers none")
    void aMissAnswersNone() {
        assertThat(answerTo("mold find/any \"abcd\" \"z*\"")).isEqualTo("\"_\"");
    }
}

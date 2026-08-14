package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What a word answers to, read out of {@code REBTYPE(Word)} in
 * {@code src/core/t-word.c}.
 *
 * <p>Only two actions, and both are surprising. A word has a LENGTH?,
 * which is the count of code points in its spelling and not anything to
 * do with a series. And TO WORD! of a string runs the scanner over that
 * string and refuses it unless the whole thing reads as one word.
 *
 * <p>The refusal is the part that matters. Without it {@code to word! "a
 * b"} builds a word that no reader can ever load back, and the mistake
 * only shows up later, in a saved file that will not read.
 */
class WordActionsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Nested
    @DisplayName("A_LENGTHQ: how long a word is")
    class Length {

        @Test
        @DisplayName("a word is as long as its spelling")
        void theSpellingIsCounted() {
            assertThat(answerTo("length? 'a")).isEqualTo("1");
            assertThat(answerTo("length? 'abc")).isEqualTo("3");
        }

        @Test
        @DisplayName("the decoration is not counted")
        void theSigilIsNotPartOfIt() {
            assertThat(answerTo("length? quote 'a")).isEqualTo("1");
            assertThat(answerTo("length? #a")).isEqualTo("1");
            assertThat(answerTo("length? first [a:]")).isEqualTo("1");
            assertThat(answerTo("length? first [:a]")).isEqualTo("1");
        }

        @Test
        @DisplayName("a letter outside ASCII counts as one")
        void codePointsRatherThanBytes() {
            assertThat(answerTo("length? #ša")).isEqualTo("2");
            assertThat(answerTo("length? quote 'ša")).isEqualTo("2");
        }

        @Test
        @DisplayName("a word made from a character is one long either side of ASCII")
        void theBoundaryOfTheAsciiRange() {
            assertThat(answerTo("length? to-word to-string to-char 126")).isEqualTo("1");
            assertThat(answerTo("length? to-word to-string to-char 128")).isEqualTo("1");
        }
    }

    @Nested
    @DisplayName("A_TO: making a word out of something")
    class Conversion {

        @Test
        @DisplayName("a word of one kind becomes a word of another, spelling and all")
        void betweenTheWordKinds() {
            assertThat(answerTo("(to word! quote 'a) = 'a")).isEqualTo("#(true)");
            assertThat(answerTo("(to word! #a) = 'a")).isEqualTo("#(true)");
            assertThat(answerTo("(to lit-word! 'a) = quote 'a")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a tag becomes the word inside it")
        void fromATag() {
            assertThat(answerTo("(to word! <a>) = 'a")).isEqualTo("#(true)");
            assertThat(answerTo("word? to word! <a>"))
                    .as("a word, not a tag wearing a new name")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a string becomes a word when the whole of it reads as one")
        void fromAString() {
            assertThat(answerTo("(to word! \"ab\") = 'ab")).isEqualTo("#(true)");
            assertThat(answerTo("(to word! \"a-b\") = 'a-b")).isEqualTo("#(true)");
            assertThat(answerTo("(to word! \"?\") = '?")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a string the scanner cannot read as one word is refused")
        void theRefusal() {
            assertThat(errorIdOf("to word! \"a,\"")).isEqualTo("invalid-chars");
            assertThat(errorIdOf("to word! \"a;\"")).isEqualTo("invalid-chars");
            assertThat(errorIdOf("to word! \"a[\"")).isEqualTo("invalid-chars");
            assertThat(errorIdOf("to word! \"a[]\"")).isEqualTo("invalid-chars");
            assertThat(errorIdOf("to word! \"a b\"")).isEqualTo("invalid-chars");
            assertThat(errorIdOf("to word! <a b>")).isEqualTo("invalid-chars");
        }

        @Test
        @DisplayName("two words in a row are two things, so they are refused too")
        void theWholeStringMustBeConsumed() {
            assertThat(errorIdOf("to word! \"a b c\"")).isEqualTo("invalid-chars");
        }

        @Test
        @DisplayName("a string that reads as something other than a word is refused")
        void aNonWordIsRefused() {
            assertThat(errorIdOf("to word! \"1\"")).isEqualTo("invalid-chars");
            assertThat(errorIdOf("to word! \"a:\"")).isEqualTo("invalid-chars");
            assertThat(errorIdOf("to word! {\"a\"}")).isEqualTo("invalid-chars");
        }

        @Test
        @DisplayName("an empty string is refused")
        void theDegenerateString() {
            assertThat(errorIdOf("to word! \"\"")).isEqualTo("invalid-chars");
        }

        @Test
        @DisplayName("spaces at the end are dropped rather than refused")
        void trailingSpaceIsForgiven() {
            assertThat(answerTo("(to word! \"x \") = 'x")).isEqualTo("#(true)");
            assertThat(answerTo("(to word! \"x^-\") = 'x")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a character becomes a word of one letter")
        void fromACharacter() {
            assertThat(answerTo("(to word! #\"a\") = 'a")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a character that cannot start a word is refused")
        void aBadCharacter() {
            assertThat(errorIdOf("to word! #\" \"")).isNotEqualTo("no-error");
            assertThat(errorIdOf("to word! #\"[\"")).isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("a logic becomes TRUE or FALSE")
        void fromALogic() {
            assertThat(answerTo("(to word! true) = 'true")).isEqualTo("#(true)");
            assertThat(answerTo("(to word! false) = 'false")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an issue takes the laxer rule and keeps its punctuation")
        void fromAStringToAnIssue() {
            assertThat(answerTo("(to issue! \"a.b\") = #a.b")).isEqualTo("#(true)");
            assertThat(answerTo("(to issue! \"1+2\") = #1+2")).isEqualTo("#(true)");
            assertThat(errorIdOf("to issue! \"a b\"")).isEqualTo("invalid-chars");
        }

        @Test
        @DisplayName("anything else is refused")
        void thereAreNoOtherSources() {
            assertThat(errorIdOf("to word! 1")).isNotEqualTo("no-error");
            assertThat(errorIdOf("to word! [a]")).isNotEqualTo("no-error");
            assertThat(errorIdOf("to word! none")).isNotEqualTo("no-error");
        }
    }

    @Nested
    @DisplayName("what a converted word is good for")
    class ItLoadsBack {

        @Test
        @DisplayName("every word TO WORD! makes reads back as the same word")
        void theWholePointOfTheCheck() {
            assertThat(answerTo("""
                    bad: copy []
                    for n 0 255 1 [
                        foreach c reduce [
                            rejoin ["" to char! n "x"]
                            rejoin ["x" to char! n]
                            rejoin ["x" to char! n "x"]
                        ][
                            if not error? try [w: to word! c] [
                                back-again: try [load mold w]
                                if any [
                                    error? back-again
                                    not word? :back-again
                                    not strict-equal? :back-again :w
                                ] [append bad c]
                            ]
                        ]
                    ]
                    empty? bad
                    """)).isEqualTo("#(true)");
        }
    }
}

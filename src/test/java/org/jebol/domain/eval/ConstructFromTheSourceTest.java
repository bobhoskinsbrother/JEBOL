package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * CONSTRUCT, read out of {@code Do_Construct} in {@code src/core/c-do.c}
 * and {@code Scan_Net_Header} in {@code l-types.c}.
 *
 * <p>CONSTRUCT builds an object without evaluating anything, which is what
 * makes it safe for a script header that arrived from somewhere else. A
 * word standing where a value goes is not looked up, so nothing in the
 * block can run.
 *
 * <p>Seven words are the exception. NONE, TRUE, ON, YES, FALSE, OFF and NO
 * become the values they name, because a header full of {@code yes} and
 * {@code no} would otherwise be a header full of words. Every other word
 * stays a word, and {@code /only} takes even those seven literally.
 *
 * <p>Given a string or a binary it does something else entirely: it reads
 * an internet-style header, one field per line, and every value is text.
 */
class ConstructFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("Do_Construct: the seven words that become values")
    class NamedConstants {

        @Test
        @DisplayName("the three spellings of true all become true")
        void theTruthfulWords() {
            assertThat(answerTo("(get in construct [a: true] 'a) = true")).isEqualTo("#(true)");
            assertThat(answerTo("(get in construct [a: on] 'a) = true")).isEqualTo("#(true)");
            assertThat(answerTo("(get in construct [a: yes] 'a) = true")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("the three spellings of false all become false")
        void theFalseWords() {
            assertThat(answerTo("(get in construct [a: false] 'a) = false"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("(get in construct [a: off] 'a) = false")).isEqualTo("#(true)");
            assertThat(answerTo("(get in construct [a: no] 'a) = false")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("all six answer a logic rather than a word")
        void theyAreLogics() {
            assertThat(answerTo("logic? get in construct [a: true] 'a")).isEqualTo("#(true)");
            assertThat(answerTo("logic? get in construct [a: on] 'a")).isEqualTo("#(true)");
            assertThat(answerTo("logic? get in construct [a: yes] 'a")).isEqualTo("#(true)");
            assertThat(answerTo("logic? get in construct [a: false] 'a")).isEqualTo("#(true)");
            assertThat(answerTo("logic? get in construct [a: off] 'a")).isEqualTo("#(true)");
            assertThat(answerTo("logic? get in construct [a: no] 'a")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("NONE becomes none")
        void theEmptyWord() {
            assertThat(answerTo("none? get in construct [a: none] 'a")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("any other word stays a word")
        void everythingElseIsLeftAlone() {
            assertThat(answerTo("word? get in construct [a: b] 'a")).isEqualTo("#(true)");
            assertThat(answerTo("(get in construct [a: b] 'a) = 'b")).isEqualTo("#(true)");
            assertThat(answerTo("word? get in construct [a: print] 'a"))
                    .as("even a word naming a function is not run")
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("Do_Construct: what keeps the shape it was written in")
    class LiteralShapes {

        @Test
        @DisplayName("a lit-word stays a lit-word")
        void litWords() {
            assertThat(answerTo("lit-word? get in construct [a: 'b] 'a")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a get-word stays a get-word")
        void getWords() {
            assertThat(answerTo("get-word? get in construct [a: :b] 'a")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("the three kinds of path each keep their own kind")
        void paths() {
            assertThat(answerTo("path? get in construct [a: b/c] 'a")).isEqualTo("#(true)");
            assertThat(answerTo("lit-path? get in construct [a: 'b/c] 'a"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("get-path? get in construct [a: :b/c] 'a"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an ordinary value is itself")
        void plainValues() {
            assertThat(answerTo("(get in construct [a: 1] 'a) = 1")).isEqualTo("#(true)");
            assertThat(answerTo("(get in construct [a: \"x\"] 'a) = \"x\""))
                    .isEqualTo("#(true)");
            assertThat(answerTo("(get in construct [a: [1 2]] 'a) = [1 2]"))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("Do_Construct: set-words")
    class SetWords {

        @Test
        @DisplayName("several set-words in a row all take the value that follows")
        void cascadingSetWords() {
            assertThat(answerTo("""
                    o: construct [a: b: 1]
                    all [1 = get in o 'a  1 = get in o 'b]
                    """)).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a set-word with nothing after it is left holding nothing")
        void aDanglingSetWord() {
            assertThat(answerTo("not none? find words-of construct [a:] 'a"))
                    .as("the field exists")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a value with no set-word before it is dropped")
        void aValueWithNowhereToGo() {
            assertThat(answerTo("(length? words-of construct [1 2 a: 3]) = 1"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an empty block gives an empty object")
        void theDegenerateBlock() {
            assertThat(answerTo("empty? words-of construct []")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("/only takes every word literally")
    class OnlyRefinement {

        @Test
        @DisplayName("the seven words stay words")
        void noSubstitution() {
            assertThat(answerTo("word? get in construct/only [a: true] 'a"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("word? get in construct/only [a: false] 'a"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("word? get in construct/only [a: on] 'a")).isEqualTo("#(true)");
            assertThat(answerTo("word? get in construct/only [a: off] 'a"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("word? get in construct/only [a: yes] 'a"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("word? get in construct/only [a: no] 'a")).isEqualTo("#(true)");
            assertThat(answerTo("word? get in construct/only [a: none] 'a"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("everything else is unaffected")
        void theShapesAreStillKept() {
            assertThat(answerTo("word? get in construct/only [a: b] 'a")).isEqualTo("#(true)");
            assertThat(answerTo("lit-word? get in construct/only [a: 'b] 'a"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("path? get in construct/only [a: b/c] 'a"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("lit-path? get in construct/only [a: 'b/c] 'a"))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("Scan_Net_Header: CONSTRUCT of a string")
    class FromAHeader {

        @Test
        @DisplayName("one field per line, and the value is text")
        void oneField() {
            assertThat(answerTo("[\"1\"] = values-of construct \"a: 1\"")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a binary is read the same way")
        void fromABinary() {
            assertThat(answerTo("[\"1\"] = values-of construct to-binary \"a: 1\""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a newline starts the next field")
        void severalFields() {
            assertThat(answerTo("[\"1\" \"yes\"] = values-of construct \"a: 1^/b: yes\""))
                    .as("and YES is text here, not a logic")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("without a newline it is all one value")
        void theOffPointForAField() {
            assertThat(answerTo("[\"1 b: yes\"] = values-of construct \"a: 1 b: yes\""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a line that begins with whitespace continues the one before")
        void continuationLines() {
            assertThat(answerTo("[\"a b c\"] = values-of construct \"f: a b^M^/ c\""))
                    .isEqualTo("#(true)");
            assertThat(answerTo("[\"a b c\"] = values-of construct \"f: a b^M^/    c\""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an empty string gives an empty object")
        void theDegenerateHeader() {
            assertThat(answerTo("empty? words-of construct \"\"")).isEqualTo("#(true)");
        }
    }
}

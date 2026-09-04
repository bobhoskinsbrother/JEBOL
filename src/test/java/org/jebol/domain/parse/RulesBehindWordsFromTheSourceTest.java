package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Three things a PARSE rule may be that JEBOL only accepted written out in
 * full.
 *
 * <p>The C resolves a word to its value before it decides what kind of rule it
 * is holding -- {@code Get_Parse_Value} -- so a word standing where a number,
 * a paren or a none could stand behaves as that thing does. JEBOL looked at
 * what was written instead, so a rule assembled at run time did not work while
 * the same rule typed out by hand did.
 *
 * <p>That is the shape of every mezzanine function that builds its own rule,
 * which is most of the interesting ones. SPLIT counts {@code 1 size skip} with
 * the size in a word; REWORD holds each half of its output rule in a word and
 * writes {@code [escape | none]} for the case where no keyword follows the
 * delimiter. Between them they account for forty-six of Rebol's own
 * assertions.
 */
class RulesBehindWordsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /**
     * A molded answer without the delimiters that molding a string adds.
     *
     * <p>Which pair those are depends on the text: one holding a quote is
     * molded inside braces instead, and a molded block of strings holds
     * plenty of quotes.
     */
    private static String moldOf(String source) {
        String molded = answerTo("mold " + source);
        return molded.startsWith("{") && molded.endsWith("}")
                ? molded.substring(1, molded.length() - 1).replace("\"", "")
                : molded.replace("\"", "");
    }

    @Nested
    @DisplayName("a repeat count held in a word")
    class Acount {

        @Test
        @DisplayName("counts as the number it holds, in a range and on its own")
        void awordCounts() {
            assertThat(answerTo("n: 4 s: none parse {12345678} [copy s 1 n skip to end] s")
                    .replace("\"", "")).isEqualTo("1234");
            assertThat(answerTo("n: 3 c: 0 parse {123456} [any [n skip (c: c + 1)]] c"))
                    .isEqualTo("2");
        }

        @Test
        @DisplayName("and the rule after it is found where it really starts")
        void therestOfTheRuleIsFound() {
            assertThat(moldOf(
                    "n: 4 collect [parse {12345678} [any [copy s 1 n skip (keep/only s)]]]"))
                    .as("the count is two rule items when it is a range, however "
                            + "each of the two is written, and everything after it "
                            + "is at the wrong place if that is miscounted")
                    .isEqualTo("[1234 5678]");
        }

        @Test
        @DisplayName("which is what SPLIT is built on")
        void splitWorks() {
            assertThat(moldOf("split {1234567812345678} 4"))
                    .isEqualTo("[1234 5678 1234 5678]");
            assertThat(moldOf("split {1234567812345678} 3"))
                    .isEqualTo("[123 456 781 234 567 8]");
            assertThat(moldOf("split/parts {1234567812345678} 2"))
                    .isEqualTo("[12345678 12345678]");
            assertThat(moldOf("split/parts [1 2 3 4 5 6] 2")).isEqualTo("[[1 2 3] [4 5 6]]");
        }
    }

    @Nested
    @DisplayName("a paren held in a word")
    class Aparen {

        @Test
        @DisplayName("is run rather than read as a sequence of rules")
        void aparenBehindAWordIsRun() {
            assertThat(answerTo("n: 0 x: first [(n: 2)] parse {ab} [skip x skip] n"))
                    .isEqualTo("2");
        }

        @Test
        @DisplayName("but a block held in a word is still a sequence to match")
        void ablockBehindAWordIsASequence() {
            assertThat(answerTo("pair: [{a} {b}] parse {ab} [pair]")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("what TO and THRU are looking for, held in a word")
    class Asearch {

        @Test
        @DisplayName("is what the word holds, not the word")
        void theywhatTheWordHolds() {
            assertThat(answerTo("c: {$} b: none parse {x$a} [to c b: to end] index? b"))
                    .as("a word is never in a string, so looking for the word itself "
                            + "matched nothing and said nothing about it")
                    .isEqualTo("2");
            assertThat(answerTo("c: {$} b: none parse {x$a} [thru c b: to end] index? b"))
                    .isEqualTo("3");
        }

        @Test
        @DisplayName("and a character in a word works the same way")
        void acharacterInAWord() {
            assertThat(answerTo("c: first {$} b: none parse {x$a} [to c b: to end] index? b"))
                    .isEqualTo("2");
        }

        @Test
        @DisplayName("but TO END is still the end and not a word to look up")
        void toendIsStillTheEnd() {
            assertThat(answerTo("end: 1 parse {ab} [to end]"))
                    .as("END is a parse command, and a script that happens to have a "
                            + "word of that name does not change what it means")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("which is what REWORD looks for its delimiter with")
        void rewordWorks() {
            assertThat(answerTo("reword {$a} [a 1]").replace("\"", "")).isEqualTo("1");
            assertThat(answerTo("reword {$a$b} [a 1 b 2]").replace("\"", ""))
                    .isEqualTo("12");
            assertThat(answerTo("reword/case {$a$A$a} [a 1 A 2]").replace("\"", ""))
                    .isEqualTo("121");
            assertThat(answerTo("reword/escape {<bang>} [bang {!}] [{<} {>}]")
                    .replace("\"", "")).isEqualTo("!");
        }
    }

    @Nested
    @DisplayName("NONE as a rule, which means one thing in a string and another in a block")
    class Anone {

        @Test
        @DisplayName("in a string it matches nothing at all and succeeds")
        void nonematchesNothingInAString() {
            assertThat(answerTo("parse {ab} [skip none skip]"))
                    .as("`if (IS_NONE(item)) return index;` is the first line of "
                            + "Parse_Next_String, so the position is handed back "
                            + "unmoved and unfailed")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("but in a block it is a value to match, because the block parser has no such line")
        void noneisAvalueInAblock() {
            assertThat(answerTo("parse reduce [none] [none]"))
                    .as("Parse_Next_String has that line and the block loop does "
                            + "not, so the same word is a no-op in one and a value "
                            + "in the other")
                    .isEqualTo("#(true)");
            assertThat(answerTo("parse [1] [none]"))
                    .as("and it does not match something that is not a none")
                    .isEqualTo("#(false)");
        }

        @Test
        @DisplayName("which makes it the do-nothing arm of an alternation")
        void nonefillsAnAlternation() {
            assertThat(answerTo("parse {ab} [skip [{z} | none] skip]")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a word holding none is the same rule")
        void awordHoldingNone() {
            assertThat(answerTo("nothing: none parse {ab} [skip nothing skip]"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("it consumes nothing, so it does not stand in for SKIP")
        void noneisNotSkip() {
            assertThat(answerTo("parse {ab} [none none]"))
                    .as("two rules that match nothing leave two characters unmatched")
                    .isEqualTo("#(false)");
        }
    }
}

package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Nine small differences from Rebol, each with its own cause.
 *
 * <p>They have nothing in common except where they were found: Rebol's own
 * {@code series-test.r3}, which could not be read at all until {@code vector!}
 * existed and which turned out to be holding a hundred and seventy-eight
 * failures behind that.
 */
class SmallDivergencesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String moldOf(String source) {
        String molded = answerTo("mold " + source);
        return molded.startsWith("{") && molded.endsWith("}")
                ? molded.substring(1, molded.length() - 1).replace("\"", "")
                : molded.replace("\"", "");
    }

    private static String whatHappensTo(String source) {
        return answerTo("either error? e: try [" + source + "] [e/id] ['worked]");
    }

    @Nested
    @DisplayName("NEW-LINE/SKIP counts records, and a record has at least one item in it")
    class NewLineSkip {

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -2})
        @DisplayName("a width of nothing or less is out of range")
        void awidthOfNothing(int width) {
            assertThat(whatHappensTo("new-line/skip [1 2] true " + width))
                    .as("clamping it to one quietly marked every item instead, "
                            + "which is what /ALL means and not what was asked")
                    .isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("and one or more is a width")
        void awidthOfOneOrMore() {
            assertThat(whatHappensTo("new-line/skip [1 2 3 4] true 2")).isEqualTo("worked");
        }
    }

    @Nested
    @DisplayName("a record width and a length, when they are not one")
    class NumbersThatAreNotSizes {

        @Test
        @DisplayName("a /SKIP width below one is out of range rather than clamped")
        void arecordWidthBelowOne() {
            assertThat(whatHappensTo("union/skip [2 1] [2 1] -2"))
                    .as("clamped to one, the call ran over single items and looked "
                            + "as though the caller had meant that")
                    .isEqualTo("out-of-range");
            assertThat(whatHappensTo("union/skip [2 1] [2 1] 0")).isEqualTo("out-of-range");
            assertThat(whatHappensTo("union/skip [2 1 2 1] [2 1] 2")).isEqualTo("worked");
        }

        @Test
        @DisplayName("and a /PART length past what a whole number holds is too")
        void alengthPastTheRange() {
            assertThat(whatHappensTo("copy/part tail [1] -2147483649"))
                    .as("the C counts in a 32-bit integer, so a number outside that "
                            + "is not a length it could ever mean")
                    .isEqualTo("out-of-range");
            assertThat(whatHappensTo("copy/part tail [1] -1")).isEqualTo("worked");
        }
    }

    @Nested
    @DisplayName("a block of names to walk with, when there are none")
    class NoNamesAtAll {

        @Test
        @DisplayName("is an invalid argument, not the wrong datatype")
        void anemptyNameList() {
            assertThat(whatHappensTo("foreach [] [] []"))
                    .as("the block is the right datatype and the wrong value, and "
                            + "the two errors send a reader to different places")
                    .isEqualTo("invalid-arg");
        }
    }

    @Nested
    @DisplayName("RANDOM of a block-like value that is not a block")
    class RandomOfSomethingBlockLike {

        @ParameterizedTest
        @ValueSource(strings = {"'a/b/c", "first [(1 2 3)]", "make hash! [1 2 3]"})
        @DisplayName("is refused, the way the C's own arm refuses it")
        void refused(String written) {
            assertThat(whatHappensTo("random " + written))
                    .as("`if (!IS_BLOCK(value)) Trap_Action(VAL_TYPE(value), "
                            + "action);` is the second line of REBTYPE(Block)'s "
                            + "RANDOM, so reaching the arm is not the same as "
                            + "being served by it")
                    .isEqualTo("cannot-use");
        }

        @Test
        @DisplayName("but a plain block is shuffled")
        void aplainBlockIsShuffled() {
            assertThat(answerTo("b: [1 2 3] same? b random b")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("DELINE/LINES, and what a trailing line ending means")
    class Delining {

        @Test
        @DisplayName("a line ending ends a line rather than starting an empty one")
        void atrailingEndingIsNotALine() {
            assertThat(answerTo("(deline/lines {a^M^/b^M^/}) = [{a} {b}]"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("(deline/lines {^M^/^M^/}) = [{} {}]"))
                    .as("two endings are two empty lines, not three and not none")
                    .isEqualTo("#(true)");
            assertThat(answerTo("(deline/lines {a^M^/b}) = [{a} {b}]"))
                    .as("and a last line with no ending is still a line")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and nothing at all is no lines")
        void nothingIsNoLines() {
            assertThat(moldOf("deline/lines {}")).isEqualTo("[]");
        }

        @Test
        @DisplayName("ENLINE of a block is declared and not written")
        void enliningAblock() {
            assertThat(whatHappensTo("enline [{a}]"))
                    .as("a block is in the declared spec, so refusing it as the "
                            + "wrong datatype would be a lie about the spec")
                    .isEqualTo("not-done");
        }
    }

    @Nested
    @DisplayName("molding a path whose head is not a word")
    class MoldingApath {

        @Test
        @DisplayName("uses the construct form, because the slashes would not read back")
        void aheadThatIsNotAWord() {
            assertThat(moldOf("to-path [1 2 3]"))
                    .as("the reader sees a number first and stops, so 1/2/3 is not "
                            + "a way of writing this path down")
                    .isEqualTo("#(path! [1 2 3])");
            assertThat(moldOf("to-path [1 none 3]")).isEqualTo("#(path! [1 none 3])");
        }

        @Test
        @DisplayName("and only the head decides, not what follows it")
        void onlyTheHeadDecides() {
            assertThat(moldOf("to-path [a b c]")).isEqualTo("a/b/c");
            assertThat(moldOf("to-path [a 2 3]"))
                    .as("a path is a word and then whatever selects through it, so "
                            + "the items after the first may be anything")
                    .isEqualTo("a/2/3");
        }
    }

    @Nested
    @DisplayName("MAKE URL! from a block")
    class MakingAurl {

        @Test
        @DisplayName("is a scheme and then the path it names")
        void aschemeAndApath() {
            assertThat(moldOf("make url! [http]")).isEqualTo("http://");
            assertThat(moldOf("make url! [http www.rebol.com %reboldoc.html]"))
                    .isEqualTo("http://www.rebol.com/reboldoc.html");
        }
    }

    @Nested
    @DisplayName("TO-STRING of bytes that carry a byte order mark")
    class ReadingMarkedText {

        @ParameterizedTest
        @ValueSource(strings = {
                "#{FFFE0000E4000000F6000000FC0000000A000000}",
                "#{0000FEFF000000E4000000F6000000FC0000000A}",
                "#{FFFEE400F600FC000A00}",
                "#{FEFF00E400F600FC000A}",
        })
        @DisplayName("reads them the way the mark says, and drops the mark")
        void themarkSaysHow(String octets) {
            assertThat(answerTo("(to-string " + octets + ") = {äöü^/}"))
                    .as("the four-byte marks have to be tested before the two-byte "
                            + "ones they start with, or a UTF-32 file reads as "
                            + "UTF-16 with a null after every character")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("with no mark at all it is UTF-8, as it always was")
        void withoutAmark() {
            assertThat(answerTo("to-string #{616263}").replace("\"", "")).isEqualTo("abc");
        }
    }
}

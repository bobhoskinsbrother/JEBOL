package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Three primitives that Rebol's own library stands on and JEBOL got wrong.
 *
 * <p>None of these was found by reading the C. They were found by reading
 * which of Rebol's mezzanine functions gave the wrong answer and then asking
 * what each one was standing on: PAD is four lines of REBOL and one of them is
 * {@code insert/dup}, SUM is three and one of them is {@code make}, and the
 * whole bitwise-on-binary group is one arm that never accepted two binaries.
 *
 * <p>That is why they are worth pinning here rather than only through the
 * functions above them. A borrowed file that loads is not a borrowed file that
 * works, and the failure surfaces a long way from its cause.
 */
class PrimitivesUnderTheMezzanineFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String theTextOf(String source) {
        return answerTo(source).replace("\"", "");
    }

    @Nested
    @DisplayName("INSERT/DUP into a string, which repeats what it inserts")
    class InsertingSeveralTimes {

        @ParameterizedTest
        @CsvSource({
                "0,  'ab'",
                "1,  'ab '",
                "2,  'ab  '",
                "3,  'ab   '",
        })
        @DisplayName("a count of nothing, one, and more")
        void thecount(int times, String expected) {
            assertThat(theTextOf(
                    "head insert/dup tail {ab} SPACE " + times)).isEqualTo(expected);
        }

        @Test
        @DisplayName("a negative count inserts nothing at all")
        void anegativeCount() {
            assertThat(theTextOf("head insert/dup tail {ab} SPACE -1")).isEqualTo("ab");
        }

        @Test
        @DisplayName("and a string repeats whole rather than a character at a time")
        void astringRepeatsWhole() {
            assertThat(theTextOf("head insert/dup tail {x} {ab} 3")).isEqualTo("xababab");
        }

        @Test
        @DisplayName("/PART limits what is taken before /DUP repeats it")
        void partLimitsTheSource() {
            assertThat(theTextOf("head insert/part tail {x} {abc} 2")).isEqualTo("xab");
        }

        @Test
        @DisplayName("and PAD, which is four lines of REBOL over this one call, works")
        void padWorks() {
            assertThat(theTextOf("pad {ab} 4")).isEqualTo("ab  ");
            assertThat(theTextOf("pad {ab} -4")).isEqualTo("  ab");
            assertThat(theTextOf("pad 12 4")).isEqualTo("12  ");
            assertThat(theTextOf("pad 12 -4")).isEqualTo("  12");
            assertThat(theTextOf("pad/with 12 4 first {0}")).isEqualTo("1200");
            assertThat(theTextOf("pad/with 12 -4 first {0}")).isEqualTo("0012");
        }
    }

    @Nested
    @DisplayName("MAKE from a value rather than a datatype")
    class MakingFromAPrototype {

        @Test
        @DisplayName("a number stands for its own datatype")
        void anumberStandsForItsDatatype() {
            assertThat(answerTo("make 0 0"))
                    .as("MAKE takes the type from the first argument whatever it is, "
                            + "so a number there means a number of that kind")
                    .isEqualTo("0");
            assertThat(answerTo("make 1 5")).isEqualTo("5");
            assertThat(answerTo("make 1.5 0")).isEqualTo("0.0");
            assertThat(answerTo("make 1% 0")).isEqualTo("0%");
        }

        @Test
        @DisplayName("and so does a string, a block and a binary")
        void theseriesStandForTheirs() {
            assertThat(theTextOf("make {a} {bc}")).isEqualTo("bc");
            assertThat(answerTo("mold make [] [1 2]").replace("\"", "")).isEqualTo("[1 2]");
            assertThat(answerTo("make #{} #{0102}")).isEqualTo("#{0102}");
        }

        @Test
        @DisplayName("which is what SUM and AVERAGE are built on")
        void sumAndAverageWork() {
            assertThat(answerTo("sum [1 2 3]")).isEqualTo("6");
            assertThat(answerTo("sum [1.0 2 3]")).isEqualTo("6.0");
            assertThat(answerTo("sum []")).isEqualTo("0");
            assertThat(answerTo("average [1 2 3]")).isEqualTo("2");
            assertThat(answerTo("sum #(u8! [10 25])")).isEqualTo("35");
            assertThat(answerTo("average #(u8! [10 25])")).isEqualTo("17.5");
        }
    }

    @Nested
    @DisplayName("a PARSE rule named by a word, when the word holds a paren")
    class AparenBehindAWord {

        @Test
        @DisplayName("is run, the way a paren written in place is")
        void aparenBehindAWordIsRun() {
            assertThat(answerTo("n: 0 parse {ab} [skip (n: 1) skip] n")).isEqualTo("1");
            assertThat(answerTo("n: 0 x: quote (n: 2) parse {ab} [skip x skip] n"))
                    .as("a word in a rule is looked up and its value used as a rule, "
                            + "and a paren as a rule is something to run rather than "
                            + "a sequence to match")
                    .isEqualTo("2");
        }

        @Test
        @DisplayName("and a word holding a block is still a sequence to match")
        void awordHoldingABlockIsStillASequence() {
            assertThat(answerTo("digits: [{1} {2}] parse {12} [digits]"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("which is what REWORD builds its whole rule out of")
        void rewordWorks() {
            assertThat(theTextOf("reword {$a} [a 1]")).isEqualTo("1");
            assertThat(theTextOf("reword {$a$b} [a 1 b 2]")).isEqualTo("12");
            assertThat(theTextOf("reword/escape {<bang>} [bang {!}] [{<} {>}]"))
                    .isEqualTo("!");
        }
    }

    @Nested
    @DisplayName("POKE into a map, whose key is a key and not a position")
    class PokingIntoAMap {

        @Test
        @DisplayName("takes a key of any datatype")
        void anyKind() {
            assertThat(answerTo("m: make map! 4 poke m {k} 5 select m {k}")).isEqualTo("5");
            assertThat(answerTo("m: make map! 4 poke m 'k 5 select m 'k")).isEqualTo("5");
            assertThat(answerTo("m: make map! 4 poke m 2 5 select m 2"))
                    .as("a number is a key here too, not the second slot")
                    .isEqualTo("5");
        }
    }

    @Nested
    @DisplayName("AND, OR and XOR between two binaries")
    class CombiningTwoBinaries {

        @ParameterizedTest
        @CsvSource({
                "'#{0102} and #{00FF}',  '#{0002}'",
                "'#{0102} and #{0300}',  '#{0100}'",
                "'#{0102}  or #{00FF}',  '#{01FF}'",
                "'#{0102}  or #{0300}',  '#{0302}'",
                "'#{0102} xor #{00FF}',  '#{01FD}'",
        })
        @DisplayName("combine octet by octet")
        void octetByOctet(String written, String expected) {
            assertThat(answerTo(written)).isEqualTo(expected);
        }

        @Test
        @DisplayName("and the shorter one is used again from its start, not padded")
        void theshorterOneCycles() {
            assertThat(answerTo("#{0101} and #{03}"))
                    .as("Xandor_Binary walks the longer of the two and wraps its "
                            + "index into the shorter -- `if (i == mt) i = 0` -- so "
                            + "one octet against two is that octet twice, and the "
                            + "answer is as long as the longer")
                    .isEqualTo("#{0101}");
            assertThat(answerTo("#{0101} or #{03}")).isEqualTo("#{0303}");
            assertThat(answerTo("#{03} or #{0101}"))
                    .as("and which side is longer decides, not which side is written "
                            + "first, so the three are all commutative")
                    .isEqualTo("#{0303}");
        }
    }
}

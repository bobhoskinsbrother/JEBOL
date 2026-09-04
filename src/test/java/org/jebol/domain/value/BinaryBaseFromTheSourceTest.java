package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code system/options/binary-base}, and the three notations a binary molds
 * in.
 *
 * <p>{@code Mold_Binary} reads the option at the moment it writes, so the
 * notation is a property of the interpreter's state rather than of the call.
 * JEBOL wrote hex whatever the option said, which meant a script that set the
 * option got no error and no change.
 *
 * <p>Each base also has its own rule for when a binary is long enough to
 * break into lines, and the three do not agree. Base sixty-four is the one
 * that catches people out: its runs are forty-eight bytes long but it writes
 * on one line up to sixty-four, because the two numbers come from different
 * places in the C.
 */
class BinaryBaseFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("which notation the option asks for")
    class TheNotation {

        @Test
        @DisplayName("two, sixteen and sixty-four each name themselves but sixteen")
        void eachBaseNamesItself() {
            assertThat(answerTo("""
                    collect [
                        foreach base [2 16 64][
                            system/options/binary-base: base
                            keep mold #{FFAA}
                        ]
                        system/options/binary-base: 16
                    ]""")).isEqualTo("""
                    ["2#{1111111110101010}" "#{FFAA}" "64#{/6o=}"]""");
        }

        @Test
        @DisplayName("FORM ignores it, because forming asks for the digits")
        void formIgnoresIt() {
            assertThat(answerTo("""
                    system/options/binary-base: 2
                    answer: form #{FFAA}
                    system/options/binary-base: 16
                    answer""")).isEqualTo("\"FFAA\"");
        }

        @Test
        @DisplayName("and a bitset follows it too, being molded by the same code")
        void aBitsetFollowsIt() {
            assertThat(answerTo("""
                    system/options/binary-base: 2
                    answer: mold #(bitset! #{FF})
                    system/options/binary-base: 16
                    answer""")).isEqualTo("\"#(bitset! 2#{11111111})\"");
        }
    }

    @Nested
    @DisplayName("where each base breaks its lines")
    class TheLineBreaks {

        private String moldedAtBase(int base, int bytes) {
            return answerTo("""
                    system/options/binary-base: %d
                    bin: copy #{}
                    repeat i %d [append bin 255]
                    answer: mold bin
                    system/options/binary-base: 16
                    answer""".formatted(base, bytes));
        }

        @Test
        @DisplayName("sixteen stays on one line up to thirty-two bytes")
        void sixteenBreaksAtThirtyTwo() {
            assertThat(moldedAtBase(16, 32)).doesNotContain("\n");
            assertThat(moldedAtBase(16, 33)).contains("\n");
        }

        @Test
        @DisplayName("two stays on one line up to eight")
        void twoBreaksAtEight() {
            assertThat(moldedAtBase(2, 8)).doesNotContain("\n");
            assertThat(moldedAtBase(2, 9)).contains("\n");
        }

        @Test
        @DisplayName("and sixty-four up to sixty-four, though its runs are forty-eight")
        void sixtyFourBreaksAtSixtyFour() {
            assertThat(moldedAtBase(64, 48)).doesNotContain("\n");
            assertThat(moldedAtBase(64, 64)).doesNotContain("\n");
            assertThat(moldedAtBase(64, 65)).contains("\n");
        }

        @Test
        @DisplayName("MOLD/FLAT breaks nothing, whatever the base")
        void flatBreaksNothing() {
            assertThat(answerTo("""
                    system/options/binary-base: 2
                    bin: copy #{}
                    repeat i 40 [append bin 255]
                    answer: mold/flat bin
                    system/options/binary-base: 16
                    answer""")).doesNotContain("\n");
        }

        @Test
        @DisplayName("exactly eight bytes in base two come back a digit short")
        void eightBytesInBaseTwoLoseADigit() {
            assertThat(moldedAtBase(2, 8))
                    .isEqualTo("{2#{" + "1".repeat(63) + "}}");
            assertThat(moldedAtBase(2, 7))
                    .isEqualTo("{2#{" + "1".repeat(56) + "}}");
        }
    }

    @Nested
    @DisplayName("MOLD/PART, which cuts the bytes before encoding them")
    class TheLimit {

        @Test
        @DisplayName("eight characters of each base")
        void eightCharactersOfEachBase() {
            assertThat(answerTo("""
                    collect [
                        foreach base [2 16 64][
                            system/options/binary-base: base
                            keep mold/part #{FFFFFFFFFFFFFFFFFFFF} 8
                        ]
                        system/options/binary-base: 16
                    ]""")).isEqualTo("""
                    ["2#{11111" "#{FFFFFF" "64#{////"]""");
        }

        @Test
        @DisplayName("and a limit past the end is the whole thing")
        void aLimitPastTheEnd() {
            assertThat(answerTo("""
                    mold/part #{FFAA} 100""")).isEqualTo("\"#{FFAA}\"");
        }

        @Test
        @DisplayName("a limit of nothing is nothing")
        void aLimitOfNothing() {
            assertThat(answerTo("""
                    mold/part #{FFAA} 0""")).isEqualTo("\"\"");
        }
    }
}

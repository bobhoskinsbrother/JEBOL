package org.jebol.domain.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PARSE walking a binary one byte at a time.
 *
 * <p>Specified in {@code spec/parse.allium} and measured against a real R3
 * 3.22.1.
 *
 * <p>A binary went to the block walker before this, which wrapped it as a
 * single item in a list of one. So {@code parse #{0102} [2 skip]} saw one
 * thing rather than two bytes, and no rule written as a binary could match
 * anything. It now goes to the same walker a string does, each byte
 * standing in for a character.
 *
 * <p>What differs is only what comes back out: a span is a binary, a
 * single item is the byte's number, and a rule written as a binary is
 * compared byte for byte rather than as the text MOLD would give.
 */
class BinaryParseTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("SKIP advances one byte")
    void skipStepsAByteAtATime() {
        assertThat(answerTo("parse #{0102} [2 skip]")).isEqualTo("#(true)");
        assertThat(answerTo("parse #{0102} [skip skip]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a count that does not match the length fails")
    void theLengthStillHasToMatch() {
        assertThat(answerTo("parse #{0102} [3 skip]")).isEqualTo("#(false)");
        assertThat(answerTo("parse #{0102} [1 skip]")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("a rule written as a binary matches those bytes")
    void aBinaryRuleMatchesBytes() {
        assertThat(answerTo("parse #{0102} [#{01} #{02}]")).isEqualTo("#(true)");
        assertThat(answerTo("parse #{0102} [#{0102}]")).isEqualTo("#(true)");
        assertThat(answerTo("parse #{0102} [#{02} #{01}]")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("COPY answers a binary, however many bytes it took")
    void copyAlwaysAnswersABinary() {
        assertThat(answerTo("parse #{010203} [copy v 2 skip skip] v = #{0102}"))
                .isEqualTo("#(true)");
        assertThat(answerTo("parse #{0102} [copy v skip skip] v = #{01}"))
                .as("one byte is still a binary to COPY")
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("SET of one byte answers its number")
    void setAnswersTheByte() {
        assertThat(answerTo("parse #{0102} [set v skip skip] v = 1")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("KEEP gives a binary for a span and a number for one byte")
    void keepFollowsTheSameRule() {
        assertThat(answerTo(
                "(parse #{010203} [collect any [keep 1 2 skip]]) = reduce [#{0102} 3]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("TO and THRU work over bytes")
    void seekingWorksOverBytes() {
        assertThat(answerTo("parse #{010203} [thru #{02} #{03}]")).isEqualTo("#(true)");
        assertThat(answerTo("parse #{010203} [to #{03} #{03}]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an empty binary is at its end")
    void theDegenerateInputIsEmpty() {
        assertThat(answerTo("parse #{} [end]")).isEqualTo("#(true)");
        assertThat(answerTo("parse #{} [skip]")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("a byte of zero is an ordinary byte")
    void zeroIsNotSpecial() {
        assertThat(answerTo("parse #{000100} [3 skip]")).isEqualTo("#(true)");
        assertThat(answerTo("parse #{000100} [#{00} #{01} #{00}]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a high byte is an ordinary byte too")
    void theTopOfTheRangeIsOrdinary() {
        assertThat(answerTo("parse #{FF} [#{FF}]")).isEqualTo("#(true)");
        assertThat(answerTo("parse #{FF} [copy v skip] v = #{FF}")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("parsing a string is unaffected")
    void theStringCaseStillWorks() {
        assertThat(answerTo("parse \"ab\" [\"a\" \"b\"]")).isEqualTo("#(true)");
        assertThat(answerTo("parse \"ab\" [copy v skip skip] v = \"a\""))
                .as("a string span is still a string")
                .isEqualTo("#(true)");
        assertThat(answerTo("parse \"ab\" [set v skip skip] v = #\"a\""))
                .as("and a single character is still a character")
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("parsing a binary minds case from the start")
    void bytesAreBytes() {
        assertThat(answerTo("parse to binary! \"aB\" [#{61} #{42}]")).isEqualTo("#(true)");
        assertThat(answerTo("parse to binary! \"aB\" [#{41} #{62}]")).isEqualTo("#(false)");
        assertThat(answerTo("parse \"aB\" [\"A\" \"b\"]"))
                .as("a string still folds")
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("NO-CASE still turns the folding on inside a binary rule")
    void foldingCanBeAskedFor() {
        assertThat(answerTo("parse to binary! \"aaaAB\" [thru #\"A\" no-case #\"b\"]"))
                .isEqualTo("#(true)");
        assertThat(answerTo("parse to binary! \"aaaAB\" [thru #\"A\" #\"b\"]"))
                .isEqualTo("#(false)");
    }
}

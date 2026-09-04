package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A batch from {@code series-test.r3}: a charset built from a block spreads
 * a string member's characters (issue-88), TRIM with both /head and /tail
 * trims both ends, and TRIM/WITH an integer removes that one code point.
 */
class CharsetAndTrimFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a charset from a block spreads a string member's characters")
    void charsetSpreadsAStringMember() {
        assertThat(answerTo("""
                reduce [
                    true? find charset [{abc}] #"b"
                    true? find charset [{abc}] #"z"
                ]""")).isEqualTo("[#(true) #(false)]");
    }

    @Test
    @DisplayName("FIND on a string with such a charset lands on the member")
    void findWithACharsetLands() {
        assertThat(answerTo("""
                {c} = find {abc} charset [{c}]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("TRIM with both /head and /tail trims both ends of a binary")
    void trimHeadAndTailBinary() {
        assertThat(answerTo("""
                (trim/head/tail #{000102030000}) = #{010203}""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("TRIM/WITH an integer removes that one code point, not its digits")
    void trimWithAnIntegerRemovesOneCodePoint() {
        assertThat(answerTo("""
                {1b2c3} = trim/with {a1b2c3} 97""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("TRIM/WITH a string removes each of its characters")
    void trimWithAStringRemovesEachCharacter() {
        assertThat(answerTo("""
                {he wrd} = trim/with {hello world} {lo}""")).isEqualTo("#(true)");
    }
}

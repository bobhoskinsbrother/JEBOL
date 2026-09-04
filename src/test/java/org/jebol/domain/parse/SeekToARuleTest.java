package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PARSE's TO and THRU take a whole rule, not only a value.
 *
 * <p>Specified in {@code spec/parse.allium}, confirmed against a real R3.
 *
 * <p>Reading the target as text makes every such rule fail, because a
 * block has no text form that appears in the input -- and it fails the way
 * a rule that simply did not match fails, which is why it can sit
 * unnoticed.
 */
class SeekToARuleTest {

    private static final String SETS = "x: charset \"x\" y: charset \"y\" ";

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("THRU takes an alternation and matches either branch")
    void thruTakesAnAlternation() {
        assertThat(answerTo(SETS + "parse \"x\" [thru [x | y]]")).isEqualTo("#(true)");
        assertThat(answerTo(SETS + "parse \"y\" [thru [x | y]]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("THRU walks forward until the rule matches")
    void thruWalksForward() {
        assertThat(answerTo(SETS + "parse \"zx\" [thru [x | y]]")).isEqualTo("#(true)");
        assertThat(answerTo(SETS + "parse \"zy\" [thru [x | y]]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("THRU leaves the position after what matched")
    void thruLandsAfterTheMatch() {
        assertThat(answerTo(SETS + "parse \"xz\" [thru [x | y] #\"z\"]"))
                .isEqualTo("#(true)");
        assertThat(answerTo("parse \"abc\" [thru [\"b\" | \"q\"] \"c\"]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("TO leaves the position before what matched")
    void toLandsBeforeTheMatch() {
        assertThat(answerTo("parse \"abc\" [to [\"b\" | \"q\"] \"bc\"]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("what is left over still has to be consumed")
    void theRestOfTheInputStillCounts() {
        assertThat(answerTo("parse \"abc\" [thru [\"b\" | \"q\"]]")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("a rule that matches nothing anywhere fails")
    void aRuleThatNeverMatchesFails() {
        assertThat(answerTo(SETS + "parse \"abc\" [thru [x | y]]")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("THRU with a plain value is unaffected")
    void aPlainValueStillWorks() {
        assertThat(answerTo("parse \"abc\" [thru \"b\" \"c\"]")).isEqualTo("#(true)");
    }
}

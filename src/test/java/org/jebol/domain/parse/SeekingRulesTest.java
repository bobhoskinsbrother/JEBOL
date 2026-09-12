package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeekingRulesTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("TO a number goes to that item")
    void toANumberIsAPlace() {
        assertThat(answerTo("parse \"abcd\" [to 1 \"abcd\"]")).isEqualTo("#(true)");
        assertThat(answerTo("parse \"abcd\" [to 2 \"bcd\"]")).isEqualTo("#(true)");
        assertThat(answerTo("parse \"abcd\" [to 4 \"d\"]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("THRU a number goes past that item")
    void thruANumberGoesOneFurther() {
        assertThat(answerTo("parse \"abcd\" [thru 0 \"abcd\"]")).isEqualTo("#(true)");
        assertThat(answerTo("parse \"abcd\" [thru 1 \"bcd\"]")).isEqualTo("#(true)");
        assertThat(answerTo("parse \"abcd\" [thru 4 end]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a number is counted from the head, so it can go backwards")
    void aPlaceIsAbsolute() {
        assertThat(answerTo("parse \"abcd\" [\"ab\" to 1 \"abcd\"]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a place outside the input fails rather than raising")
    void aPlaceBeyondTheInputIsNoMatch() {
        assertThat(answerTo("parse \"ab\" [to 9 end]")).isEqualTo("#(false)");
        assertThat(answerTo("parse \"ab\" [to -1 end]")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("a tag is sought with its brackets")
    void aTagStandsForItsBrackets() {
        assertThat(answerTo("parse \"<a>\" [thru <a>]")).isEqualTo("#(true)");
        assertThat(answerTo("parse \"a<a>\" [to <a> 3 skip]")).isEqualTo("#(true)");
        assertThat(answerTo("parse \"a<a>\" [thru [<b> | <a>]]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a rule that repeats cannot be sought")
    void aRepeatingRuleIsRefused() {
        assertThat(errorIdOf("parse \"foo\" [thru some \"0\"]")).isEqualTo("parse-rule");
        assertThat(errorIdOf("parse \"foo\" [thru any \"0\"]")).isEqualTo("parse-rule");
    }

    @Test
    @DisplayName("a fraction cannot be sought either")
    void aDecimalIsRefused() {
        assertThat(errorIdOf("parse \"foo\" [thru 1.2]")).isEqualTo("parse-rule");
    }

    @Test
    @DisplayName("seeking a string is unaffected")
    void theOrdinaryTargetsStillWork() {
        assertThat(answerTo("parse \"abcd\" [thru \"bc\" \"d\"]")).isEqualTo("#(true)");
        assertThat(answerTo("parse \"abcd\" [to \"bc\" \"bcd\"]")).isEqualTo("#(true)");
        assertThat(answerTo("parse \"abcd\" [to end]")).isEqualTo("#(true)");
    }
}

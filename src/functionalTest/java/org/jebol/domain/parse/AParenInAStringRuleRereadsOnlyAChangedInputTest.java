package org.jebol.domain.parse;

import java.util.concurrent.TimeUnit;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

class AParenInAStringRuleRereadsOnlyAChangedInputTest {

    private static final String TRUE = "#(true)";

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("a paren at every character of a long string costs a step each, not a copy of the string each")
    void walksALongStringQuickly() {
        assertThat(answerTo("""
                n: 0 s: append/dup copy "" "x" 100000
                parse s [any [skip (n: n + 1)]] n""")).isEqualTo("100000");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("the same over a long binary")
    void walksALongBinaryQuickly() {
        assertThat(answerTo("""
                n: 0 b: append/dup copy #{} #{01} 100000
                parse b [any [skip (n: n + 1)]] n""")).isEqualTo("100000");
    }

    @Test
    @DisplayName("a paren that changes a character in place is seen by the rule after it")
    void seesAChangeOfTheSameLength() {
        assertThat(answerTo("""
                s: {ab} parse s [(s/1: #"x") {xb}]""")).isEqualTo(TRUE);
        assertThat(answerTo("""
                b: #{0102} parse b [(b/1: 9) #{0902}]""")).isEqualTo(TRUE);
    }

    @Test
    @DisplayName("a paren that lengthens or shortens the input is seen by the rule after it")
    void seesAChangeOfLength() {
        assertThat(answerTo("""
                s: {ab} parse s [skip (append s {c}) {bc}]""")).isEqualTo(TRUE);
        assertThat(answerTo("""
                s: {abc} parse s [skip (remove back tail s) {b}]""")).isEqualTo(TRUE);
    }
}

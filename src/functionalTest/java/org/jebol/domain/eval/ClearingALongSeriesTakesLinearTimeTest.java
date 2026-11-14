package org.jebol.domain.eval;

import java.util.concurrent.TimeUnit;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

class ClearingALongSeriesTakesLinearTimeTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("CLEAR of a string of a million characters takes moments, not minutes")
    void clearsALongString() {
        assertThat(answerTo("""
                s: append/dup copy "" "x" 1000000 clear s length? s""")).isEqualTo("0");
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("CLEAR of a binary of a million bytes takes moments, not minutes")
    void clearsALongBinary() {
        assertThat(answerTo("""
                b: append/dup copy #{} #{01} 1000000 clear b length? b""")).isEqualTo("0");
    }

    @Test
    @DisplayName("CLEAR from a position keeps what is before it")
    void keepsTheHead() {
        assertThat(answerTo("""
                s: {abcdef} clear skip s 2 s == {ab}""")).isEqualTo("#(true)");
        assertThat(answerTo("""
                b: #{010203} clear next b b""")).isEqualTo("#{01}");
    }
}

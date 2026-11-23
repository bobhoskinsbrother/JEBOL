package org.jebol.application;

import org.jebol.domain.value.AnyStringValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnlineEndsLinesAsItsPlatformDoesFromTheSourceTest {

    private static String textOf(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        assertThat(outcome.succeeded()).as(source + " ran").isTrue();
        return ((AnyStringValue) outcome.value()).text();
    }

    private static String onWindows(String source) {
        return textOf(AnInterpreter.writingLocalFilesForWindows(), source);
    }

    private static String onPosix(String source) {
        return textOf(AnInterpreter.writingLocalFilesForPosix(), source);
    }

    @Test
    @DisplayName("on Windows a line feed gains the carriage return in front of it, as Rebol's suite asserts")
    void addsCarriageReturnOnWindows() {
        assertThat(onWindows("enline {a^/b}")).isEqualTo("a\r\nb");
    }

    @Test
    @DisplayName("on Windows a line feed that has its carriage return already is left alone")
    void leavesWholeEndingsOnWindows() {
        assertThat(onWindows("enline {a^M^/b^/c^M^/}")).isEqualTo("a\r\nb\r\nc\r\n");
    }

    @Test
    @DisplayName("on Windows only the part from the string's position is changed")
    void changesFromPositionOnWindows() {
        assertThat(onWindows("""
                s: {a^/b^/c}
                enline next next s
                head s""")).isEqualTo("a\nb\r\nc");
    }

    @Test
    @DisplayName("elsewhere a carriage return and line feed become a line feed, as Rebol's suite asserts")
    void leavesLineFeedElsewhere() {
        assertThat(onPosix("enline {a^/b^M^/c}")).isEqualTo("a\nb\nc");
    }
}

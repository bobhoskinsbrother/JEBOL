package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ISO 8601 as a date literal, which is a thing the lexer reads rather than a
 * string a codec parses.
 *
 * <p>{@code 2000-01-01T10:00+02:00} and {@code 1-Jan-2000/10:00+2:00} are the
 * same value written two ways: a T stands where the slash does, and the offset
 * may run its hour and minute together with no colon between them.
 *
 * <p>Which is why it belongs here. A date written that way can sit in source,
 * in a block, and in a path, and be equal to the same date written the REBOL
 * way -- {@code b/2013-11-08T17:01} selects from a block keyed by a date.
 */
class IsoDateLiteralFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a T stands where the slash does")
    void aTStandsForTheSlash() {
        assertThat(answerTo("""
                8-Nov-2013/17:01 = load "2013-11-08T17:01\"""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a trailing Z is UTC, which is an offset of nothing")
    void zuluIsNoOffset() {
        assertThat(answerTo("""
                8-Nov-2013/17:01 = load "2013-11-08T17:01Z\"""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an offset with no colon counts, both ways round")
    void anOffsetWithoutAColonCounts() {
        assertThat(answerTo("""
                reduce [
                    8-Nov-2013/17:01+1:00 = load "2013-11-08T17:01+0100"
                    8-Nov-2013/17:01-1:00 = load "2013-11-08T17:01-0100"
                ]""")).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("and an offset with one reads the same")
    void anOffsetWithAColonReadsTheSame() {
        assertThat(answerTo("""
                8-Nov-2013/17:01+1:00 = load "2013-11-08T17:01+01:00\""""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("slashes work where hyphens do, which is not standard but is read")
    void slashesReadAsWell() {
        assertThat(answerTo("""
                reduce [
                    8-Nov-2013/17:01 = load "2013/11/08T17:01"
                    8-Nov-2013/17:01 = load "2013/11/08T17:01Z"
                    8-Nov-2013/17:01+1:00 = load "2013/11/08T17:01+0100"
                    8-Nov-2013/17:01+1:00 = load "2013/11/08T17:01+01:00"
                ]""")).isEqualTo("[#(true) #(true) #(true) #(true)]");
    }

    @Test
    @DisplayName("a Z with digits after it is neither, and is refused")
    void aZuluWithDigitsAfterItIsRefused() {
        assertThat(answerTo("""
                reduce [
                    error? try [load "2013-11-08T17:01Z0100"]
                    error? try [load "2013/11/08T17:01Z0100"]
                ]""")).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("one written with slashes is a date and not a path of three numbers")
    void theSlashedFormIsNotAPath() {
        assertThat(answerTo("""
                date? load "2013/11/08T17:01\"""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and it selects from a block through a path")
    void itSelectsThroughAPath() {
        assertThat(answerTo("""
                b: [8-Nov-2013/17:01 "foo"]
                "foo" = b/2013-11-08T17:01""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("MOLD/ALL writes a date back out the ISO way")
    void moldAllWritesIso() {
        assertThat(answerTo("""
                reduce [
                    mold/all 1-1-2000/1:2:3
                    mold/all 1-1-2000/10:20:3
                    mold/all 1-1-200/1:2:3
                ]""")).isEqualTo(
                "[\"2000-01-01T01:02:03\" \"2000-01-01T10:20:03\""
                        + " \"0200-01-01T01:02:03\"]");
    }

    @Test
    @DisplayName("with the offset padded to two digits each side")
    void moldAllPadsTheOffset() {
        assertThat(answerTo("""
                reduce [
                    mold/all 1-1-200/1:2:3+2:0
                    mold/all 1-1-200/1:2:3+10:0
                ]""")).isEqualTo(
                "[\"0200-01-01T01:02:03+02:00\" \"0200-01-01T01:02:03+10:00\"]");
    }

    @Test
    @DisplayName("a path segment that reads as nothing and cannot be a word is refused")
    void anUnreadableNumericSegmentIsRefused() {
        assertThat(answerTo("""
                error? try [load "a/08T17:01Z0100"]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("but a segment that reads as several values is still a path and a word")
    void aSegmentThatReadsAsSeveralIsKept() {
        assertThat(answerTo("""
                mold load "a/3<\"""")).isEqualTo("\"[a/3 <]\"");
    }
}

package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Writing a date's offset, which two names do in opposite ways.
 *
 * <p>{@code PD_Date} in {@code t-date.c}. ZONE keeps the clock and changes
 * what it is an offset from; TIMEZONE keeps the instant and moves the clock by
 * the difference between the offsets. They agree only where that difference is
 * nothing, which means setting an offset a date already has -- and not, as a
 * reader might expect, on a date that has no offset at all.
 *
 * <p>A date is a value rather than a series, so writing a field replaces what
 * the word holds instead of changing something in place.
 */
class DateZoneWrittenFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("raised: try [" + source + "] raised/id");
    }

    @Test
    @DisplayName("ZONE keeps the clock, so a bare date gains midnight and an offset")
    void zoneKeepsTheClock() {
        assertThat(answerTo("""
                d: 1-Jan-2000
                d/zone: 2
                d""")).isEqualTo("1-Jan-2000/0:00+2:00");
    }

    @Test
    @DisplayName("and it replaces an offset that was already there")
    void zoneReplacesAnOffset() {
        assertThat(answerTo("""
                d: 28-Oct-2009/10:09:38-7:00
                d/zone: 2
                d""")).isEqualTo("28-Oct-2009/10:09:38+2:00");
    }

    @Test
    @DisplayName("TIMEZONE keeps the instant, so the clock moves instead")
    void timezoneKeepsTheInstant() {
        assertThat(answerTo("""
                d: 1-Jan-2000
                d/zone: 2
                d/timezone: 4
                d""")).isEqualTo("1-Jan-2000/2:00+4:00");
    }

    @Test
    @DisplayName("and it carries the date over when the clock passes midnight")
    void timezoneCarriesTheDate() {
        assertThat(answerTo("""
                d: 1-Jan-2000
                d/zone: 2
                d/timezone: -7
                d""")).isEqualTo("31-Dec-1999/15:00-7:00");
    }

    @Test
    @DisplayName("moving to no offset at all keeps the instant just the same")
    void timezoneToNothingKeepsTheInstant() {
        assertThat(answerTo("""
                d: 1-Jan-2000
                d/zone: 2
                d/timezone: 0
                d""")).isEqualTo("31-Dec-1999/22:00");
    }

    @Test
    @DisplayName("the two disagree wherever the offset actually changes")
    void theTwoDisagreeWhenTheOffsetChanges() {
        assertThat(answerTo("""
                byClock: 1-Jan-2000
                byClock/zone: 2
                byInstant: 1-Jan-2000
                byInstant/timezone: 2
                reduce [byClock byInstant]"""))
                .isEqualTo("[1-Jan-2000/0:00+2:00 1-Jan-2000/2:00+2:00]");
    }

    @Test
    @DisplayName("and agree only where it does not")
    void theTwoAgreeWhenTheOffsetStays() {
        assertThat(answerTo("""
                d: 1-Jan-2000
                d/zone: 2
                before: d
                d/timezone: 2
                before = d""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an offset may be given as a time as well as a number of hours")
    void anOffsetMayBeATime() {
        assertThat(answerTo("""
                d: 1-Jan-2000
                d/zone: 2:30
                d""")).isEqualTo("1-Jan-2000/0:00+2:30");
    }

    @Test
    @DisplayName("fifteen hours and three quarters is as far as either goes")
    void theOffsetHasAReach() {
        assertThat(answerTo("""
                d: 1-Jan-2000
                d/zone: 15
                d""")).isEqualTo("1-Jan-2000/0:00+15:00");
    }

    @Test
    @DisplayName("and past it is out of range rather than wrapping round")
    void pastTheReachIsRefused() {
        assertThat(errorIdFrom("""
                d: 1-Jan-2000 d/zone: 16""")).isEqualTo("out-of-range");
        assertThat(errorIdFrom("""
                d: 1-Jan-2000 d/timezone: -70""")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("a part that cannot be written says so, and a word that is no part says something else")
    void theTwoRefusalsAreDifferent() {
        assertThat(errorIdFrom("""
                d: 1-1-2000 d/date: 1""")).isEqualTo("bad-field-set");
        assertThat(errorIdFrom("""
                d: 1-1-2000 d/utc: 1""")).isEqualTo("bad-field-set");
        assertThat(errorIdFrom("""
                d: 1-1-2000 d/foo: 1""")).isEqualTo("invalid-path");
    }
}

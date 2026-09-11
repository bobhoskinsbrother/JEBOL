package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The date and clock MS-DOS packed into sixteen bits each, and what an offset
 * does to them.
 *
 * <p>{@code u-bincode.c}. Neither field has room for an offset -- five bits of
 * hour, six of minute, five of half-seconds, seven of year, four of month,
 * five of day -- so a date carrying one has to be resolved before it is
 * written, and what gets written is the instant rather than the wall time
 * somebody read off a clock beside it.
 *
 * <p>REBOL does that resolving without a line of code in the dialect, because
 * it stores a date already in UTC and remembers the offset only to put it back
 * on for display: {@code Adjust_Date_Zone}, whose own comment says "the result
 * should be used for output, not stored". JEBOL keeps the time as it was
 * written, so the conversion is explicit here instead.
 *
 * <p>Which makes a ZIP written in Berlin and a ZIP written in London at the
 * same moment carry the same two bytes, and that is the only reading under
 * which the format's times can be compared at all.
 *
 * <p>Every byte string here was read off a real 3.22.5 before it was written.
 */
class BincodeMsDosMomentsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String bytesWritten(String dialect) {
        return answerTo("""
                b: binary 32
                binary/write b [%s]
                enbase/flat b/buffer 16""".formatted(dialect));
    }

    private static String errorIdFrom(String dialect) {
        return answerTo("""
                b: binary 32
                e: try [binary/write b [%s]]
                either error? e [e/id] ['no-error]""".formatted(dialect));
    }

    @Test
    @DisplayName("the clock written is the instant, not the wall time")
    void theClockIsTheInstantAndNotTheWallTime() {
        assertThat(bytesWritten("msdos-time 14-Mar-2019/15:33:18+1:00"))
                .isEqualTo("\"2974\"");
        assertThat(bytesWritten("msdos-time 14-Mar-2019/15:33:18-1:00"))
                .isEqualTo("\"2984\"");
    }

    /**
     * The two degenerate cases either side of the rule: an offset of nothing,
     * and no offset at all. Both leave the clock as it was written, and both
     * write the same two bytes as the bare time does.
     */
    @Test
    @DisplayName("an offset of nothing leaves the clock alone")
    void anOffsetOfNothingLeavesTheClockAlone() {
        assertThat(bytesWritten("msdos-time 14-Mar-2019/15:33:18+0:00"))
                .isEqualTo("\"297C\"");
        assertThat(bytesWritten("msdos-time 14-Mar-2019/15:33:18"))
                .isEqualTo("\"297C\"");
        assertThat(bytesWritten("msdos-time 15:33:18")).isEqualTo("\"297C\"");
    }

    /** Fifteen hours is as far ahead as REBOL will read an offset. */
    @Test
    @DisplayName("the widest offset still moves the clock")
    void theWidestOffsetStillMovesTheClock() {
        assertThat(bytesWritten("msdos-time 14-Mar-2019/15:33:18+15:00"))
                .isEqualTo("\"2904\"");
    }

    /**
     * Half past midnight an hour ahead is half past eleven the evening before,
     * so the day the date field carries is the previous one. The other
     * direction crosses the same boundary the other way.
     */
    @Test
    @DisplayName("the day moves when the offset carries the clock past midnight")
    void theDayMovesWhenTheOffsetCrossesMidnight() {
        assertThat(bytesWritten("msdos-date 14-Mar-2019/00:33:18+1:00"))
                .isEqualTo("\"6D4E\"");
        assertThat(bytesWritten("msdos-date 14-Mar-2019/23:33:18-1:00"))
                .isEqualTo("\"6F4E\"");
    }

    /**
     * The smallest offset REBOL can express is a quarter of an hour, and at
     * midnight exactly that is still enough to land on the day before.
     */
    @Test
    @DisplayName("a quarter of an hour at midnight exactly is enough")
    void aQuarterOfAnHourAtMidnightIsEnough() {
        assertThat(bytesWritten("msdos-date 14-Mar-2019/00:00:00+0:15"))
                .isEqualTo("\"6D4E\"");
    }

    @Test
    @DisplayName("an offset that stays inside the day leaves the day alone")
    void anOffsetInsideTheDayLeavesTheDayAlone() {
        assertThat(bytesWritten("msdos-date 14-Mar-2019/23:00:00+1:00"))
                .isEqualTo("\"6E4E\"");
    }

    /**
     * A date with no clock has nothing for the offset to act on, which is what
     * {@code Adjust_Date_Zone} means by returning early when the time is
     * absent. So the day written is the day given.
     */
    @Test
    @DisplayName("a date carrying no clock keeps its day")
    void aDateWithNoClockKeepsItsDay() {
        assertThat(bytesWritten("msdos-date 14-Mar-2019")).isEqualTo("\"6E4E\"");
    }

    @Test
    @DisplayName("both halves of MSDOS-DATETIME come from the one instant")
    void bothHalvesComeFromTheOneInstant() {
        assertThat(bytesWritten("msdos-datetime 14-Mar-2019/00:33:18+1:00"))
                .isEqualTo("\"29BC6D4E\"");
        assertThat(bytesWritten("msdos-datetime 14-Mar-2019/23:33:18-1:00"))
                .isEqualTo("\"29046F4E\"");
    }

    @Test
    @DisplayName("and it reads back as that instant, offset gone")
    void andItReadsBackAsThatInstant() {
        assertThat(answerTo("""
                b: binary 32
                binary/write b [msdos-datetime 14-Mar-2019/00:33:18+1:00]
                binary/read b 'MSDOS-DATETIME""")).isEqualTo("13-Mar-2019/23:33:18");
    }

    /** The turn of a year is the same boundary one step larger. */
    @Test
    @DisplayName("an offset can carry the date into the year before")
    void anOffsetCanCarryTheDateIntoTheYearBefore() {
        assertThat(bytesWritten("msdos-datetime 1-Jan-2020/00:30:00+1:00"))
                .isEqualTo("\"C0BB9F4F\"");
    }

    /**
     * The year field counts from 1980 in seven bits, which reaches 2107 and no
     * further. Both ends wrap rather than raising, because the field is seven
     * bits and that is all there is to it: 1979 is 127 and 2108 is nought.
     *
     * <p>Which the offset can reach on its own -- half past midnight on the
     * first day of 1980, an hour ahead, is the last evening of 1979.
     */
    @Test
    @DisplayName("the seven-bit year wraps at both ends rather than raising")
    void theSevenBitYearWrapsAtBothEnds() {
        assertThat(bytesWritten("msdos-date 31-Dec-1979")).isEqualTo("\"9FFF\"");
        assertThat(bytesWritten("msdos-date 1-Jan-1970")).isEqualTo("\"21EC\"");
        assertThat(bytesWritten("msdos-date 1-Jan-2108")).isEqualTo("\"2100\"");
        assertThat(bytesWritten("msdos-date 1-Jan-2200")).isEqualTo("\"21B8\"");
    }

    @Test
    @DisplayName("and an offset alone can carry a date below the epoch")
    void anOffsetAloneCanCarryADateBelowTheEpoch() {
        assertThat(bytesWritten("msdos-datetime 1-Jan-1980/00:30:00+1:00"))
                .isEqualTo("\"C0BB9FFF\"");
    }

    /**
     * MSDOS-DATE needs a date, since a time has no day in it to write. The
     * wrong value is a fault in the dialect rather than in an argument, which
     * is where the C puts it: {@code if (!IS_DATE(next)) goto error}.
     */
    @Test
    @DisplayName("MSDOS-DATE refuses a time, and every code refuses a number or text")
    void theCodesRefuseWhatHasNoMomentInIt() {
        assertThat(errorIdFrom("msdos-date 15:33:18")).isEqualTo("dialect");
        assertThat(errorIdFrom("msdos-time 5")).isEqualTo("dialect");
        assertThat(errorIdFrom("""
                msdos-time {x}""")).isEqualTo("dialect");
        assertThat(errorIdFrom("msdos-date 5")).isEqualTo("dialect");
        assertThat(errorIdFrom("msdos-datetime 5")).isEqualTo("dialect");
    }

    /**
     * MSDOS-DATETIME given a bare time is where JEBOL parts company with the C
     * on purpose. The C lets it through -- {@code if (IS_DATE(next) ||
     * IS_TIME(next))} -- and then reads the year, month and day out of a
     * struct that holds a time, so what it writes is whatever those bits
     * happened to be. There is nothing there to copy, and nothing in REBOL's
     * own suite that asks for it.
     */
    @Test
    @DisplayName("and a bare time has no date half, so the whole code is refused")
    void aBareTimeHasNoDateHalf() {
        assertThat(errorIdFrom("msdos-datetime 15:33:18")).isEqualTo("dialect");
    }
}

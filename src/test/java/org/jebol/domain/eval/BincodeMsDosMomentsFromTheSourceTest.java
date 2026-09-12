package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    @DisplayName("an offset of nothing leaves the clock alone")
    void anOffsetOfNothingLeavesTheClockAlone() {
        assertThat(bytesWritten("msdos-time 14-Mar-2019/15:33:18+0:00"))
                .isEqualTo("\"297C\"");
        assertThat(bytesWritten("msdos-time 14-Mar-2019/15:33:18"))
                .isEqualTo("\"297C\"");
        assertThat(bytesWritten("msdos-time 15:33:18")).isEqualTo("\"297C\"");
    }

    @Test
    @DisplayName("the widest offset still moves the clock")
    void theWidestOffsetStillMovesTheClock() {
        assertThat(bytesWritten("msdos-time 14-Mar-2019/15:33:18+15:00"))
                .isEqualTo("\"2904\"");
    }

    @Test
    @DisplayName("the day moves when the offset carries the clock past midnight")
    void theDayMovesWhenTheOffsetCrossesMidnight() {
        assertThat(bytesWritten("msdos-date 14-Mar-2019/00:33:18+1:00"))
                .isEqualTo("\"6D4E\"");
        assertThat(bytesWritten("msdos-date 14-Mar-2019/23:33:18-1:00"))
                .isEqualTo("\"6F4E\"");
    }

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

    @Test
    @DisplayName("an offset can carry the date into the year before")
    void anOffsetCanCarryTheDateIntoTheYearBefore() {
        assertThat(bytesWritten("msdos-datetime 1-Jan-2020/00:30:00+1:00"))
                .isEqualTo("\"C0BB9F4F\"");
    }

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

    @Test
    @DisplayName("and a bare time has no date half, so the whole code is refused")
    void aBareTimeHasNoDateHalf() {
        assertThat(errorIdFrom("msdos-datetime 15:33:18")).isEqualTo("dialect");
    }
}

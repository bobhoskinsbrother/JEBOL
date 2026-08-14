package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A raw string, {@code %{...}%}, read out of {@code Scan_Raw_String} in
 * {@code l-scan.c}.
 *
 * <p>The C's own summary says what it is for: "Scan a raw string (without any
 * modifications). Eliminates need of double escaping and allowes unmatched
 * braces." So a caret is a caret and a brace need not be matched -- everything a
 * braced string reads as an instruction is content here.
 *
 * <p>The part worth knowing is how it closes: the run of percent signs that
 * opened it is the run that closes it. That is what lets a raw string hold the
 * closing sequence of a shorter one, and it means the way to write the
 * terminator is a longer run rather than an escape.
 *
 * <p>Specified in {@code spec/natives.allium} under "A raw string, and what it
 * does not read".
 */
class RawStringFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("an empty one is an empty string, whatever the run length")
    void theEmptyRawString() {
        assertThat(answerTo("\"\" == transcode/one \"%{}%\"")).isEqualTo("#(true)");
        assertThat(answerTo("\"\" == transcode/one \"%%{}%%\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a caret is a caret, not an escape")
    void aCaretIsACaret() {
        assertThat(answerTo("2 = length? transcode/one \"%{a^^b}%\""))
                .isEqualTo("#(false)");
        assertThat(answerTo("3 = length? transcode/one \"%{a^^b}%\""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an unmatched brace is content")
    void anUnmatchedBraceIsContent() {
        assertThat(answerTo("\"}\" == transcode/one \"%{}}%\"")).isEqualTo("#(true)");
        assertThat(answerTo("\"{\" == transcode/one \"%{{}%\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and a longer run can hold a shorter one whole")
    void aLongerRunHoldsAShorterOne() {
        assertThat(answerTo("\" %{^^}% \" == transcode/one \"%%{ %{^^}% }%%\""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a line ending is whatever the source had")
    void lineEndingsSurvive() {
        assertThat(answerTo("\"^/\" == transcode/one rejoin [\"%{\" LF \"}%\"]"))
                .isEqualTo("#(true)");
        assertThat(answerTo("\"^M^/\" == transcode/one rejoin [\"%{\" CR LF \"}%\"]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a closing run longer than the opening one is refused")
    void aLongerClosingRunIsRefused() {
        assertThat(answerTo(
                "e: try [transcode/one \"%{a}%%\"] error? e")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and a percent sign still reads as a file and as a word")
    void thePercentSignStillDoesItsOtherJobs() {
        assertThat(answerTo("%a = first [%a]")).isEqualTo("#(true)");
        assertThat(answerTo("file? first [%/tmp/x]")).isEqualTo("#(true)");
        assertThat(answerTo("-7 %% 3")).isEqualTo("2");
    }
}

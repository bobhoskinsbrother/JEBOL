package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RawStringFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("an empty one is an empty string, whatever the run length")
    void readsTheEmptyRawString() {
        assertThat(answerTo("\"\" == transcode/one \"%{}%\"")).isEqualTo("#(true)");
        assertThat(answerTo("\"\" == transcode/one \"%%{}%%\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a caret is a caret, not an escape")
    void readsACaretAsACaret() {
        assertThat(answerTo("2 = length? transcode/one \"%{a^^b}%\""))
                .isEqualTo("#(false)");
        assertThat(answerTo("3 = length? transcode/one \"%{a^^b}%\""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an unmatched brace is content")
    void readsAnUnmatchedBraceAsContent() {
        assertThat(answerTo("\"}\" == transcode/one \"%{}}%\"")).isEqualTo("#(true)");
        assertThat(answerTo("\"{\" == transcode/one \"%{{}%\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and a longer run can hold a shorter one whole")
    void holdsAShorterRunInsideALongerOne() {
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
    void refusesALongerClosingRun() {
        assertThat(answerTo(
                "e: try [transcode/one \"%{a}%%\"] error? e")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and a percent sign still reads as a file and as a word")
    void leavesThePercentSignItsOtherJobs() {
        assertThat(answerTo("%a = first [%a]")).isEqualTo("#(true)");
        assertThat(answerTo("file? first [%/tmp/x]")).isEqualTo("#(true)");
        assertThat(answerTo("-7 %% 3")).isEqualTo("2");
    }
}

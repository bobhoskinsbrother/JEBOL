package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckWalksTheWholeSeriesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String failureOf(String source) {
        return answerTo("e: try [" + source + """
                ]
                either error? e [reduce [e/type e/id]] [reduce ['ok]]""");
    }

    @Test
    @DisplayName("a text carrying a zero breaks the invariant CHECK is for")
    void aTextCarryingAZeroIsRefused() {
        assertThat(failureOf("""
                check {a^(00)b}""")).isEqualTo("[Internal bad-series]");
    }

    @Test
    @DisplayName("and it does not matter where in the text the zero sits")
    void whereTheZeroSitsDoesNotMatter() {
        assertThat(failureOf("""
                check {^(00)ab}""")).isEqualTo("[Internal bad-series]");
        assertThat(failureOf("""
                check {ab^(00)}""")).isEqualTo("[Internal bad-series]");
    }

    @Test
    @DisplayName("a binary carrying a zero byte is refused the same way")
    void aBinaryCarryingAZeroByteIsRefused() {
        assertThat(failureOf("check #{610062}")).isEqualTo("[Internal bad-series]");
        assertThat(failureOf("check #{616200}")).isEqualTo("[Internal bad-series]");
    }

    @Test
    @DisplayName("the whole series is walked, not the part after the position")
    void theWholeSeriesIsWalkedNotThePartAfterThePosition() {
        assertThat(failureOf("""
                held: {a^(00)b}
                check skip held 2""")).isEqualTo("[Internal bad-series]");
    }

    @Test
    @DisplayName("an ordinary series answers the very series it was given")
    void anOrdinarySeriesAnswersItself() {
        assertThat(answerTo("""
                held: {ab}
                same? held check held""")).isEqualTo("#(true)");
        assertThat(answerTo("""
                held: {aéb}
                same? held check held""")).isEqualTo("#(true)");
        assertThat(answerTo("""
                held: {}
                same? held check held""")).isEqualTo("#(true)");
        assertThat(answerTo("check #{}")).isEqualTo("#{}");
        assertThat(answerTo("check #{616263}")).isEqualTo("#{616263}");
    }

    @Test
    @DisplayName("a block never raises, whatever it holds")
    void aBlockNeverRaises() {
        assertThat(answerTo("check [1 2]")).isEqualTo("[1 2]");
        assertThat(answerTo("check []")).isEqualTo("[]");
        assertThat(answerTo("""
                check reduce [1 {a^(00)b}]""")).isEqualTo("""
                [1 "a^@b"]""");
    }

    @Test
    @DisplayName("and what is not a series at all is refused where arguments are gathered")
    void whatIsNotASeriesIsRefusedAsAnArgument() {
        assertThat(failureOf("check 5")).isEqualTo("[Script expect-arg]");
    }
}

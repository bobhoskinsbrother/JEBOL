package org.jebol.domain.read;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhatIsReadKeepsItsLineBreaksFromTheSourceTest {

    private static final String TRUE = "#(true)";

    private static final String FALSE = "#(false)";

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String marksOf(String reading) {
        return answerTo("collect [walked: " + reading
                + " forall walked [keep new-line? walked]]");
    }

    @Test
    @DisplayName("the outermost block LOAD builds carries the line breaks the source wrote")
    void marksTheOutermostBlockLoadBuilds() {
        assertThat(answerTo("new-line? next load {a^/b}")).isEqualTo(TRUE);
        assertThat(answerTo("equal? mold load {a^/b} {[a^/b]}")).isEqualTo(TRUE);
    }

    @Test
    @DisplayName("the outermost block TRANSCODE builds carries them too")
    void marksTheOutermostBlockTranscodeBuilds() {
        assertThat(answerTo("new-line? next transcode {a^/b}")).isEqualTo(TRUE);
        assertThat(answerTo("equal? mold transcode {a^/b} {[a^/b]}")).isEqualTo(TRUE);
    }

    @Test
    @DisplayName("the outermost block and every block inside it are marked alike")
    void marksTheBlocksInsideAsWellAsTheOneAround() {
        assertThat(marksOf("load {a^/[c^/d]^/b}")).isEqualTo("[#(false) #(true) #(true)]");
        assertThat(marksOf("second load {a^/[c^/d]^/b}")).isEqualTo("[#(false) #(true)]");
    }

    @Test
    @DisplayName("a paren keeps its line breaks as a block does")
    void marksAParenTheSameWay() {
        assertThat(answerTo("equal? mold load {(a^/b)} {(a^/b)}")).isEqualTo(TRUE);
    }

    @Test
    @DisplayName("nothing on a single line is marked")
    void marksNothingWhereTheSourceBrokeNoLine() {
        assertThat(marksOf("load {a b c}")).isEqualTo("[#(false) #(false) #(false)]");
    }

    @Test
    @DisplayName("several blank lines mark the value that follows them once")
    void marksTheValueAfterBlankLinesOnce() {
        assertThat(marksOf("load {a^/^/^/b}")).isEqualTo("[#(false) #(true)]");
        assertThat(answerTo("equal? mold load {a^/^/^/b} {[a^/b]}")).isEqualTo(TRUE);
    }

    @Test
    @DisplayName("a comment ends the line it stands on")
    void marksTheValueAfterACommentedLine() {
        assertThat(marksOf("load {a ; what follows is on its own line^/b}"))
                .isEqualTo("[#(false) #(true)]");
    }

    @Test
    @DisplayName("a carriage return and a line feed together are one break")
    void readsCarriageReturnAndLineFeedAsOneBreak() {
        assertThat(marksOf("load {a^M^/b}")).isEqualTo("[#(false) #(true)]");
    }

    @Test
    @DisplayName("a break before the first value marks the first value")
    void marksTheFirstValueWhenTheSourceOpensWithABreak() {
        assertThat(answerTo("new-line? load {^/a b}")).isEqualTo(TRUE);
        assertThat(marksOf("load {^/a b}")).isEqualTo("[#(true) #(false)]");
    }

    @Test
    @DisplayName("a break after the last value marks nothing, the tail included")
    void marksNothingForABreakAfterTheLastValue() {
        assertThat(marksOf("load {a b^/}")).isEqualTo("[#(false) #(false)]");
        assertThat(answerTo("new-line? tail load {a b^/}")).isEqualTo(FALSE);
        assertThat(answerTo("equal? mold load {a b^/} {[a b]}")).isEqualTo(TRUE);
    }

    @Test
    @DisplayName("stepping through the source marks the value the step read")
    void marksTheValueAStepRead() {
        assertThat(answerTo("new-line? transcode/next {^/a b}")).isEqualTo(TRUE);
        assertThat(marksOf("transcode/next {^/a b}")).isEqualTo("[#(true) #(false)]");
    }

    @Test
    @DisplayName("a failure handed back keeps the marks of what was read before it")
    void keepsTheMarksOfWhatWasReadBeforeAFailure() {
        assertThat(marksOf("transcode/error {a^/b}")).isEqualTo("[#(false) #(true) #(false)]");
    }

    @Test
    @DisplayName("a script keeps the breaks of the body under its header")
    void marksTheBodyOfAScriptUnderItsHeader() {
        assertThat(answerTo("equal? mold load {Rebol [] a^/b} {[a^/b]}")).isEqualTo(TRUE);
    }

    @Test
    @DisplayName("a value asked for alone comes back as itself, with no block to mark")
    void answersTheValueAloneUnmarked() {
        assertThat(answerTo("transcode/one {a^/b}")).isEqualTo("a");
    }
}

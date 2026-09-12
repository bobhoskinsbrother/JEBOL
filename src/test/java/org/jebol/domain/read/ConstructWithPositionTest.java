package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConstructWithPositionTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a string construct stands where it was told to")
    void aStringStandsAtItsPosition() {
        assertThat(answerTo("load {#(string! \"ab\" 2)}")).isEqualTo("\"b\"");
    }

    @Test
    @DisplayName("position one is the head, which is the same as no position")
    void positionOneIsTheHead() {
        assertThat(answerTo("load {#(string! \"ab\" 1)}")).isEqualTo("\"ab\"");
        assertThat(answerTo("load {#(string! \"ab\")}")).isEqualTo("\"ab\"");
    }

    @Test
    @DisplayName("a file construct takes a position too")
    void aFileTakesAPosition() {
        assertThat(answerTo("mold load {#(file! \"ab\" 2)}")).isEqualTo("\"%b\"");
    }

    @Test
    @DisplayName("a block construct stands where it was told to")
    void aBlockStandsAtItsPosition() {
        assertThat(answerTo("mold load {#(block! [1 2 3] 2)}")).isEqualTo("\"[2 3]\"");
    }

    @Test
    @DisplayName("the block family converts within itself")
    void theBlockFamilyConverts() {
        assertThat(answerTo("mold load {#(paren! [1 2])}")).isEqualTo("\"(1 2)\"");
    }

    @Test
    @DisplayName("a construct naming a block and holding something else is refused")
    void aWrongContentIsRefused() {
        assertThat(answerTo("e: try [load {#(block! 1)}] either error? e [e/id] ['no-error]"))
                .isEqualTo("malconstruct");
    }

    @Test
    @DisplayName("a position past the tail clamps rather than failing")
    void aPositionPastTheEndClamps() {
        assertThat(answerTo("load {#(string! \"ab\" 9)}")).isEqualTo("\"\"");
        assertThat(answerTo("load {#(string! \"ab\" 3)}")).isEqualTo("\"\"");
        assertThat(answerTo("load {#(string! \"ab\" 2147483647)}")).isEqualTo("\"\"");
    }

    @Test
    @DisplayName("and a position below the head clamps to the tail, not to the head")
    void apositionBelowTheHeadClampsToTheTail() {
        assertThat(answerTo("load {#(string! \"ab\" 0)}")).isEqualTo("\"\"");
        assertThat(answerTo("load {#(string! \"ab\" -1)}")).isEqualTo("\"\"");
        assertThat(answerTo("mold load {#(block! [1 2] 0)}")).isEqualTo("\"[]\"");
    }

    @Test
    @DisplayName("a text construct takes the value and at most a position, nothing else")
    void atextConstructTakesNothingElse() {
        for (String refused : new String[] {
            "#(string! {ab} x)",
            "#(string! {ab} 2 x)",
            "#(string! {ab} 2 3)",
            "#(string! {ab} 1.5)",
            "#(string! {ab} {c})",
            "#(string! 2 {ab})",
            "#(file! {ab} x)",
            "#(file! {ab} 2 x)",
            "#(tag! {ab} 2 x)",
            "#(email! {ab} 2 x)",
            "#(url! {ab} 2 x)",
            "#(ref! {ab} 2 x)",
            "#(binary! #{0102} 2 x)",
        }) {
            assertThat(errorIdFrom(refused)).as(refused).isEqualTo("malconstruct");
        }
    }

    @Test
    @DisplayName("but a block construct ignores what follows the position")
    void ablockConstructIgnoresTheRest() {
        assertThat(answerTo("mold load {#(block! [1 2] 2 x)}")).isEqualTo("\"[2]\"");
        assertThat(answerTo("mold load {#(block! [1 2] 2 3)}")).isEqualTo("\"[2]\"");
        assertThat(answerTo("mold load {#(block! [1 2] x)}")).isEqualTo("\"[1 2]\"");
    }

    @Test
    @DisplayName("and the text family still converts within itself")
    void thetextFamilyConverts() {
        assertThat(answerTo("mold load {#(binary! {ab})}")).isEqualTo("\"#{6162}\"");
        assertThat(answerTo("load {#(string! #{6162})}")).isEqualTo("\"ab\"");
        assertThat(answerTo("mold load {#(file! #{6162})}")).isEqualTo("\"%ab\"");
        assertThat(errorIdFrom("#(string! [1 2])")).isEqualTo("malconstruct");
    }

    private static String errorIdFrom(String literal) {
        return answerTo("e: try [load {" + literal + "}] "
                + "either error? e [e/id] ['no-error]");
    }
}

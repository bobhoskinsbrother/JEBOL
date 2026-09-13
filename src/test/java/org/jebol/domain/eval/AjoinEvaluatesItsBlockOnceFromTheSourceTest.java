package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AjoinEvaluatesItsBlockOnceFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        String whole = """
                counted: 0
                bump: does [counted: counted + 1 ajoin ["#" counted]]
                """ + source;
        interpreter.defineFreshWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    @Test
    @DisplayName("a function in the block is called once per place it is written")
    void aFunctionInTheBlockIsCalledOncePerPlaceItIsWritten() {
        assertThat(answerTo("""
                joined: ajoin ["<" bump ">" bump "!"]
                reduce [joined counted]""")).isEqualTo("""
                        ["<#1>#2!" 2]""");
    }

    @Test
    @DisplayName("and that holds with a separator, with /ALL, and for an empty block")
    void thatHoldsWithASeparatorWithAllAndForAnEmptyBlock() {
        assertThat(answerTo("""
                reduce [
                    ajoin/with ["a" bump "b"] #"/"
                    ajoin/all [bump none bump]
                    ajoin []
                    counted
                ]""")).isEqualTo("""
                        ["a/#1/b" "#2none#3" "" 3]""");
    }

    @Test
    @DisplayName("the datatype still comes from the first value, dropped or not")
    void theDatatypeStillComesFromTheFirstValue() {
        assertThat(answerTo("""
                reduce [
                    ajoin [%a "b"]
                    ajoin [none %a 3]
                    ajoin [<a> "b"]
                    ajoin [http://x "y"]
                ]""")).isEqualTo("""
                        [%ab "a3" "<a>b" http://xy]""");
    }

    @Test
    @DisplayName("a counter walked by the block lands where one pass leaves it")
    void aCounterWalkedByTheBlockLandsWhereOnePassLeavesIt() {
        assertThat(answerTo("""
                widths: [100 20 *]
                column: 0
                width-of-the-next-column: does [
                    column: column + 1
                    either integer? found: pick widths column [
                        ajoin [" width=" found]
                    ] [""]
                ]
                collect [
                    loop 3 [
                        keep ajoin ["<th" width-of-the-next-column ">"]
                    ]
                ]""")).isEqualTo("""
                        ["<th width=100>" "<th width=20>" "<th>"]""");
    }
}

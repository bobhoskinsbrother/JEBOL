package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AGobsPositionWrapsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        String whole = """
                three-children: does [
                    holder: make gob! []
                    append holder make gob! 1x1
                    append holder make gob! 2x2
                    append holder make gob! 3x3
                    holder
                ]
                """ + source;
        interpreter.defineFreshWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    @Test
    @DisplayName("stepping back past the head wraps rather than stopping there")
    void steppingBackPastTheHeadWraps() {
        assertThat(answerTo("""
                collect [
                    foreach step [-3 -2 -1 0 1 2 3 4 5] [
                        keep index? skip three-children step
                    ]
                ]""")).isEqualTo("[4294967294 4294967295 0 1 2 3 4 5 6]");
    }

    @Test
    @DisplayName("and inserting at a wrapped position reaches the tail, not the head")
    void insertingAtAWrappedPositionReachesTheTail() {
        assertThat(answerTo("""
                collect [
                    foreach step [-2 -1 0 1 2 3] [
                        holder: three-children
                        taken: take/part holder 1
                        insert lib/skip holder step taken
                        keep/only collect [
                            foreach child holder/pane [keep child/size]
                        ]
                    ]
                ]""")).isEqualTo("""
                        [[2x2 3x3 1x1] [2x2 3x3 1x1] [1x1 2x2 3x3] \
                        [2x2 1x1 3x3] [2x2 3x3 1x1] [2x2 3x3 1x1]]""");
    }

    @Test
    @DisplayName("which is what makes MOVE with a negative offset work at all")
    void whichIsWhatMakesMoveWithANegativeOffsetWork() {
        assertThat(answerTo("""
                holder: three-children
                sizes: does [collect [foreach child holder/pane [keep child/size]]]
                collect [
                    keep/only sizes
                    move holder 1   keep/only sizes
                    move holder -1  keep/only sizes
                    move holder -1  keep/only sizes
                ]""")).isEqualTo("""
                        [[1x1 2x2 3x3] [2x2 1x1 3x3] [1x1 3x3 2x2] [3x3 2x2 1x1]]""");
    }
}

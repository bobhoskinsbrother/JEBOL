package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A batch of evaluation behaviors from {@code evaluation-test.r3}, each read
 * from the C: UNBIND loosens a word, COMPOSE evaluates its parens in the
 * caller's context, and CATCH/WITH with a function handler leaves the caught
 * value in last-result.
 */
class EvaluationClusterFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("UNBIND loosens a word so it no longer reads its old slot")
    void unbindLoosensAWord() {
        assertThat(answerTo("""
                x: 10 error? try [unset unbind 'x]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("UNBIND loosens every word in a block")
    void unbindLoosensABlock() {
        assertThat(answerTo("""
                a: 1 b: 2 blk: unbind [a b]
                error? try [do reduce [{get} first blk]]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("COMPOSE evaluates a paren in the caller's context")
    void composeSeesTheCallersContext() {
        assertThat(answerTo("""
                f: func [a] [compose [got (a)]]
                (f 42) = [got 42]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("CATCH/WITH a block handler reads and rewrites last-result")
    void catchWithABlockHandler() {
        assertThat(answerTo("""
                catch/with [throw 1] [2 * system/state/last-result]"""))
                .isEqualTo("2");
    }

    @Test
    @DisplayName("CATCH/WITH a function handler takes the caught value as its argument")
    void catchWithAFunctionHandler() {
        assertThat(answerTo("""
                catch/with [throw 21] func [v n] [2 * v]""")).isEqualTo("42");
    }
}

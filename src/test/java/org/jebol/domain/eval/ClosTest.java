package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CLOS makes a closure, ported off the porting backlog.
 *
 * <p>Specified in {@code spec/natives.allium} and measured against a real
 * R3 3.22.1.
 *
 * <p>R3 writes this as {@code make closure!}. JEBOL has no closure
 * datatype to make, and its FUNC already keeps a frame that outlives the
 * call, so FUNC is what a closure means here. That is FUNC being wrong
 * rather than CLOS being right, and the note beside CLOSURE in the prelude
 * says so.
 */
class ClosTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("CLOS makes a function that can be called")
    void closMakesAFunction() {
        assertThat(answerTo("f: clos [x] [x + 1] f 2")).isEqualTo("3");
    }

    @Test
    @DisplayName("the names of a call outlive the call")
    void theFrameOutlivesTheCall() {
        assertThat(answerTo("f: clos [x] [does [x]] g: f 1 g")).isEqualTo("1");
    }

    @Test
    @DisplayName("two calls keep two separate frames")
    void eachCallHasItsOwn() {
        assertThat(answerTo(
                "f: clos [x] [does [x]] a: f 1 b: f 2 (a) + (b)")).isEqualTo("3");
    }

    @Test
    @DisplayName("a closure with no arguments works")
    void theDegenerateSpec() {
        assertThat(answerTo("f: clos [] [5] f")).isEqualTo("5");
    }

    @Test
    @DisplayName("the spec and the body are copied")
    void neitherIsShared() {
        assertThat(answerTo("b: [1] f: clos [] b append b 2 f")).isEqualTo("1");
    }
}

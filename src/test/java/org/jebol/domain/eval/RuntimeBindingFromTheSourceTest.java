package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where words that arrive at run time get their slots, read out of
 * {@code Do_String} in {@code src/core/c-do.c}.
 *
 * <p>Source that reaches the interpreter while a script is running -- from
 * a string, a file, or anything LOAD produced -- is bound with
 * {@code BIND_ALL}, which gives every word in it a slot whether or not
 * anything knew that word before.
 *
 * <p>Without it nothing loaded at run time can name anything new, and
 * {@code do "total: 1"} fails on TOTAL. It is the difference between a
 * language that can read its own source and one that can only run the text
 * it was started with.
 */
class RuntimeBindingFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("source read at run time may name something new")
    void aNewWordGetsASlot() {
        assertThat(answerTo("do \"ax: 999\"")).isEqualTo("999");
    }

    @Test
    @DisplayName("the name it made survives the DO that made it")
    void theSlotOutlivesTheCall() {
        assertThat(answerTo("do \"ax: 999\" ax")).isEqualTo("999");
    }

    @Test
    @DisplayName("a word the source did not assign to holds nothing rather than being unknown")
    void anUnassignedWordIsUnset() {
        assertThat(errorIdOf("do \"never-set-anywhere\"")).isEqualTo("no-value");
    }

    @Test
    @DisplayName("a word the library already knows keeps its meaning")
    void theLibraryStillWins() {
        assertThat(answerTo("do \"length? [1 2 3]\"")).isEqualTo("3");
    }

    @Test
    @DisplayName("a name made at run time is reachable from ordinary source")
    void theTwoSidesShareOneContext() {
        assertThat(answerTo("do \"made-later: 7\" made-later + 1")).isEqualTo("8");
    }

    @Test
    @DisplayName("words nested inside blocks are bound too")
    void bindingGoesDeep() {
        assertThat(answerTo("do \"if true [deep-one: 5] deep-one\"")).isEqualTo("5");
    }

    @Test
    @DisplayName("a function defined at run time can be called afterwards")
    void aWholeDefinitionWorks() {
        assertThat(answerTo("do \"doubler: func [n] [n * 2]\" doubler 21")).isEqualTo("42");
    }

    @Test
    @DisplayName("an empty source does nothing rather than failing")
    void theDegenerateSource() {
        assertThat(answerTo("unset? do \"\"")).isEqualTo("#(true)");
    }
}

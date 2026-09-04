package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REMOVE/KEY on a block read as keys and values.
 *
 * <p>Specified in {@code spec/natives.allium} and measured against a real
 * R3 3.22.1.
 *
 * <p>Only an odd place holds a key. JEBOL ignored the refinement and took
 * the first item out instead, whatever the caller asked for, so the block
 * always changed and never in the way that was wanted.
 */
class RemoveKeyTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the pair whose key matches is taken out")
    void aMatchingKeyTakesItsValueWithIt() {
        assertThat(answerTo("b: copy [a b b c] remove/key b 'b b = [a b]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a value is not a key, even when it looks like one")
    void onlyAnOddPlaceHoldsAKey() {
        assertThat(answerTo("b: copy [a b b c] remove/key b 'c b = [a b b c]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the key is matched exactly")
    void theKeyMindsCase() {
        assertThat(answerTo("b: copy [a b b c] remove/key b 'B b = [a b b c]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a paren is read the same way")
    void aParenIsAlsoPairs() {
        assertThat(answerTo("b: quote (a b b c) remove/key b 'c b = quote (a b b c)"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a key that is not there changes nothing")
    void aMissingKeyIsHarmless() {
        assertThat(answerTo("b: copy [a 1] remove/key b 'z b = [a 1]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an empty block is unharmed")
    void theDegenerateBlockIsLeftAlone() {
        assertThat(answerTo("b: copy [] remove/key b 'a empty? b")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("REMOVE/KEY on a map still works")
    void theMapCaseIsUnaffected() {
        assertThat(answerTo("m: make map! [a 1 b 2] remove/key m 'a none? select m 'a"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a plain REMOVE is unaffected")
    void theOrdinaryRemoveStillWorks() {
        assertThat(answerTo("a: copy [1 2 3] remove a a = [2 3]")).isEqualTo("#(true)");
        assertThat(answerTo("a: copy [1 2 3] remove/part a 2 a = [3]")).isEqualTo("#(true)");
    }
}

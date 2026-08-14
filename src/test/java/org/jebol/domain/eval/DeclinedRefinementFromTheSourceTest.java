package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A refinement written as a get-word and turned down, read out of
 * {@code Do_Args} in {@code src/core/c-do.c}.
 *
 * <p>{@code f/:flag} applies the refinement when FLAG is true and leaves
 * it off otherwise, which is how a function passes its own refinements on
 * without writing the call twice.
 *
 * <p>The part that is not guessable: a refinement turned down still takes
 * its arguments out of the block, and drops them. The C says so in one
 * line -- {@code if (useArgs) DS_Base[ds] = *DS_POP; else DS_DROP} -- and
 * it has to be that way, because the values after the call are already
 * written down and something has to consume them. What follows the call in
 * the block would otherwise be read as more expressions.
 */
class DeclinedRefinementFromTheSourceTest {

    /** The suite's own function: one required argument and two refinements. */
    private static final String FUNCTION =
            "fce: func [a [string!] /ref1 b [integer!] /ref2 :c 'd] "
                    + "[reduce [a ref1 b ref2 c d]] ";

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        String whole = FUNCTION + source;
        interpreter.defineFreshWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("a refinement that is true behaves as though it were written plainly")
    void grantedIsOrdinary() {
        assertThat(answerTo("ref1: yes (fce/:ref1 \"a\" 1) = fce/ref1 \"a\" 1"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a refinement that is false leaves the function without it")
    void declinedIsAbsent() {
        assertThat(answerTo("ref1: off (fce/:ref1 \"a\" 1) = fce \"a\""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a declined refinement still eats its argument")
    void theArgumentIsConsumed() {
        assertThat(answerTo("ref1: off (reduce [fce/:ref1 \"a\" 1]) = reduce [fce \"a\"]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the argument it eats is evaluated all the same")
    void theArgumentIsStillEvaluated() {
        assertThat(answerTo("ref1: off fce/:ref1 \"a\" x: 1 + 1 x = 2"))
                .isEqualTo("#(true)");
        assertThat(answerTo("ref1: yes fce/:ref1 \"a\" x: 1 + 1 x = 2"))
                .as("and the same when it is granted")
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a declined refinement takes a wrong-typed argument without complaint")
    void theTypeIsNotChecked() {
        assertThat(errorIdOf("ref1: off fce/:ref1 \"a\" \"\"")).isEqualTo("no-error");
    }

    @Test
    @DisplayName("a declined refinement still needs its argument to be there")
    void theArgumentIsStillRequired() {
        assertThat(errorIdOf("ref1: off fce/:ref1 \"a\"")).isEqualTo("no-arg");
        assertThat(errorIdOf("ref1: yes fce/:ref1 \"a\""))
                .as("granted or not makes no difference to this")
                .isEqualTo("no-arg");
    }

    @Test
    @DisplayName("a quoted argument of a declined refinement is taken as written")
    void quotingStillApplies() {
        assertThat(answerTo("ref2: off (fce/:ref2 \"a\" never-set also-never) = fce \"a\""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("two refinements, either of which may be declined")
    void allFourCombinations() {
        assertThat(answerTo("""
                ref1: yes ref2: yes
                (fce/:ref1/:ref2 "a" 1 + 1 x y) = reduce ["a" true 2 true 'x 'y]
                """)).isEqualTo("#(true)");
        assertThat(answerTo("""
                ref1: yes ref2: off
                (fce/:ref1/:ref2 "a" 1 + 1 x y) = reduce ["a" true 2 none none none]
                """)).isEqualTo("#(true)");
        assertThat(answerTo("""
                ref1: off ref2: yes
                (fce/:ref1/:ref2 "a" 1 + 1 x y) = reduce ["a" none none true 'x 'y]
                """)).isEqualTo("#(true)");
        assertThat(answerTo("""
                ref1: off ref2: off
                (fce/:ref1/:ref2 "a" 1 + 1 x y) = reduce ["a" none none none none none]
                """)).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the same four written the other way round")
    void theCombinationsInPathOrder() {
        assertThat(answerTo("""
                ref1: yes ref2: yes
                (fce/:ref2/:ref1 "a" x y 1 + 1) = reduce ["a" true 2 true 'x 'y]
                """)).isEqualTo("#(true)");
        assertThat(answerTo("""
                ref1: yes ref2: off
                (fce/:ref2/:ref1 "a" x y 1 + 1) = reduce ["a" true 2 none none none]
                """)).isEqualTo("#(true)");
        assertThat(answerTo("""
                ref1: off ref2: yes
                (fce/:ref2/:ref1 "a" x y 1 + 1) = reduce ["a" none none true 'x 'y]
                """)).isEqualTo("#(true)");
        assertThat(answerTo("""
                ref1: off ref2: off
                (fce/:ref2/:ref1 "a" x y 1 + 1) = reduce ["a" none none none none none]
                """)).isEqualTo("#(true)");
    }
}

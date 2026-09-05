package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A refinement a function does not have raises, whoever wrote the function.
 *
 * <p>Natives and actions refused one already. A function written in REBOL did
 * not: the check sat behind {@code callee instanceof NativeValue} and a
 * user-defined function fell straight past it, so {@code f/nope 1} ran as
 * though the refinement had been left off. That is every function in the
 * borrowed library as well as every function a script writes, which makes it
 * the widest silent-wrong-answer in the port -- and the sharpest answer to
 * whether a borrowed {@code .reb} passing its own tests says the port is right.
 *
 * <p>The note that used to sit on the check said a user function "needs none of
 * this: its refinements are parameters and its arity already accounts for
 * them". Both halves are true and neither makes the refusal unnecessary: a
 * refinement that is not a parameter at all still has to be refused rather than
 * ignored.
 *
 * <p>{@code no-refine} appears zero times in all sixty-seven vendored suite
 * files, so nothing in Rebol's own tests could have caught this. Every
 * expectation was read off `./r3-head`.
 */
class UnknownRefinementIsRefusedFromTheSourceTest {

    private static String errorIdFrom(String source) {
        String asking = "e: try [" + source + "] either error? e [e/id] ['no-error]";
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(asking);
        return interpreter.display(interpreter.run(asking));
    }

    @Test
    @DisplayName("on a function a script wrote")
    void onAfunctionAscriptWrote() {
        assertThat(errorIdFrom("f: func [x][x] f/nope 1")).isEqualTo("no-refine");
    }

    @Test
    @DisplayName("on a function the borrowed library wrote")
    void onAborrowedFunction() {
        assertThat(errorIdFrom("split/nope \"a,b\" \",\"")).isEqualTo("no-refine");
        assertThat(errorIdFrom("pad/left \"ab\" 5"))
                .as("PAD has no /left, which is the case that found this")
                .isEqualTo("no-refine");
    }

    @Test
    @DisplayName("and on a native and an action, which always refused")
    void onAnativeAndAnAction() {
        assertThat(errorIdFrom("reduce/nope [1]")).isEqualTo("no-refine");
        assertThat(errorIdFrom("append/nope [] 1")).isEqualTo("no-refine");
    }

    @Test
    @DisplayName("a refinement the function does have still works")
    void arefinementItHasStillWorks() {
        assertThat(errorIdFrom("split/into \"a,b\" \",\""))
                .as("SPLIT has no /into either, so this must still raise")
                .isEqualTo("no-refine");
        Interpreter interpreter = Interpreter.create();
        String source = "f: func [x /twice][either twice [x * 2][x]] f/twice 4";
        interpreter.defineFreshWordsIn(source);
        assertThat(interpreter.display(interpreter.run(source))).isEqualTo("8");
    }

    @Test
    @DisplayName("and a function with no refinements at all refuses any")
    void afunctionWithNoRefinementsRefusesAny() {
        assertThat(errorIdFrom("g: func [][1] g/anything")).isEqualTo("no-refine");
    }
}

package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

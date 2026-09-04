package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A refinement's argument is type-checked like any other, read from
 * {@code func-test.r3}: {@code fce/ref1 "a" ""} refuses the string where
 * {@code /ref1} declared an integer, with expect-arg.
 */
class RefinementArgumentTypesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String A_FUNCTION_WITH_A_TYPED_REFINEMENT = """
            fce: func [a [string!] /ref1 b [integer!]] [reduce [a b]]
            """;

    @Test
    @DisplayName("a refinement argument of the wrong type is refused")
    void aWrongTypeRefinementArgumentIsRefused() {
        assertThat(errorIdOf(A_FUNCTION_WITH_A_TYPED_REFINEMENT
                + "fce/ref1 \"a\" \"\"")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("a refinement argument of the right type is taken")
    void aRightTypeRefinementArgumentIsTaken() {
        assertThat(answerTo(A_FUNCTION_WITH_A_TYPED_REFINEMENT
                + "fce/ref1 \"a\" 1")).isEqualTo("[\"a\" 1]");
    }

    @Test
    @DisplayName("the base argument is still type-checked when a refinement is asked")
    void theBaseArgumentIsStillChecked() {
        assertThat(errorIdOf(A_FUNCTION_WITH_A_TYPED_REFINEMENT
                + "fce/ref1 1 2")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("an absent refinement's argument is not checked against the next value")
    void anAbsentRefinementIsNotCheckedAgainstTheNextValue() {
        assertThat(answerTo(A_FUNCTION_WITH_A_TYPED_REFINEMENT
                + "fce \"a\"")).isEqualTo("[\"a\" _]");
    }
}

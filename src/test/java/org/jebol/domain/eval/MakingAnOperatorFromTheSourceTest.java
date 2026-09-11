package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MAKE OP!, which builds an infix operator at runtime.
 *
 * <p>{@code Make_Function} with {@code type == REB_OP} in {@code c-function.c}.
 * Two ways in and they meet at once: a block is read exactly as MAKE FUNCTION!
 * reads one, and a function or an action is taken as it stands -- sharing its
 * specification, its body and its arguments rather than being copied.
 *
 * <p>What the operator adds is where the first argument comes from: the value
 * already produced to its left rather than the position after it. Nothing else
 * about applying one is different, which is why every operator this build
 * starts with has a prefix twin doing the same work.
 *
 * <p>JEBOL had none of it. {@code make op!} answered something that was not an
 * operator, so the whole of REBOL's own OP! group failed -- fourteen
 * assertions, and the {@code .} operator its own tests define is a fair
 * example of what the word is for.
 *
 * <p>Every expectation was read off a real 3.22.5 first.
 */
class MakingAnOperatorFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a specification and a body make one, and it reads infix")
    void aSpecificationAndABodyMakeOne() {
        assertThat(answerTo("""
                +*: make op! [[a b][a + (a * b)]]
                reduce [op? :+*  1 +* 2  2 +* 2]""")).isEqualTo("[#(true) 3 6]");
    }

    @Test
    @DisplayName("and so does a function that already exists")
    void andSoDoesAFunctionThatAlreadyExists() {
        assertThat(answerTo("""
                times: func [a b][a * b]
                by: make op! :times
                reduce [op? :by  2 by 3]""")).isEqualTo("[#(true) 6]");
    }

    @Test
    @DisplayName("and an action, which is a function by another name")
    void andAnAction() {
        assertThat(answerTo("""
                mod: make op! :remainder
                reduce [op? :mod  6 mod 3  7 mod 3]""")).isEqualTo("[#(true) 0 1]");
    }

    /**
     * Counted up to the first refinement rather than over the whole list.
     * What follows a refinement is only ever supplied by a call that named
     * one, and an operator has no way to name anything -- so a function of two
     * arguments with refinements after them makes a perfectly good operator,
     * and its refinements are simply never asked for.
     */
    @Test
    @DisplayName("a function whose refinements come after its two arguments is fine")
    void aFunctionWithRefinementsAfterItsTwoArguments() {
        assertThat(answerTo("""
                near: func [a b /p][(abs a - b) <= (abs a * 0.01)]
                aeq: make op! :near
                reduce [op? :aeq  1.011 aeq 1.01  2.0 aeq 1.0]"""))
                .isEqualTo("[#(true) #(true) #(false)]");
    }

    @Test
    @DisplayName("and a local after them is not an argument either")
    void andALocalAfterThemIsNotAnArgument() {
        assertThat(answerTo("""
                .: make op! [[a "val1" b "val2" /local c][c: none join a b]]
                reduce [op? :.  "a"."b"  "a".["b" "c"]]"""))
                .isEqualTo("""
                        [#(true) "ab" "abc"]""");
    }

    /**
     * An operator takes the value on its left and the value on its right, and
     * there is nowhere for a third to come from. One is refused from the other
     * side for the same reason.
     */
    @Test
    @DisplayName("three arguments cannot be an operator, and neither can one")
    void threeArgumentsCannotBeAnOperator() {
        assertThat(answerTo("""
                three: func [a b c][a + b + c]
                e: try [make op! :three] reduce [error? e  e/id]"""))
                .isEqualTo("[#(true) bad-make-arg]");
        assertThat(answerTo("""
                one: func [a][a]
                e: try [make op! :one] reduce [error? e  e/id]"""))
                .isEqualTo("[#(true) bad-make-arg]");
        assertThat(answerTo("""
                e: try [make op! [[a b c][a]]] e/id""")).isEqualTo("bad-make-arg");
    }

    @Test
    @DisplayName("and what is not a function at all is refused too")
    void whatIsNotAFunctionAtAllIsRefused() {
        assertThat(answerTo("e: try [make op! 42] e/id")).isEqualTo("bad-make-arg");
        assertThat(answerTo("""
                e: try [make op! "ab"] e/id""")).isEqualTo("bad-make-arg");
    }

    /**
     * Every reflector asks the function the operator dispatches to rather than
     * the operator. The C reads the datatype the operator was made from and
     * starts the same switch again, so an operator made from an action answers
     * none for its body where one made from a function answers the block it
     * was written with.
     */
    @Test
    @DisplayName("SPEC-OF and BODY-OF ask what is behind it")
    void specOfAndBodyOfAskWhatIsBehindIt() {
        assertThat(answerTo("""
                +*: make op! [[a b][a + (a * b)]]
                reduce [spec-of :+*  body-of :+*]"""))
                .isEqualTo("[[a b] [a + (a * b)]]");
        assertThat(answerTo("""
                .: make op! [[a "val1" b "val2" /local c][c: none join a b]]
                reduce [spec-of :.  body-of :.]"""))
                .isEqualTo("""
                        [[a "val1" b "val2" /local c] [c: none join a b]]""");
    }

    @Test
    @DisplayName("and one made from an action has no body to show")
    void oneMadeFromAnActionHasNoBodyToShow() {
        assertThat(answerTo("""
                mod: make op! :remainder
                none? body-of :mod""")).isEqualTo("#(true)");
    }

    /**
     * Taken as it stands rather than copied, so the operator and the function
     * are one behaviour reached two ways -- which is what every operator this
     * build starts with already is.
     */
    @Test
    @DisplayName("the operator and the function it was made from are the same behaviour")
    void theOperatorAndTheFunctionAreTheSameBehaviour() {
        assertThat(answerTo("""
                times: func [a b][a * b]
                by: make op! :times
                reduce [(2 by 3) = times 2 3  (body-of :by) = body-of :times]"""))
                .isEqualTo("[#(true) #(true)]");
    }
}

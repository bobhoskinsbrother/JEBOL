package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a function specification may hold, and what it may not.
 *
 * <p>{@code Check_Func_Spec} in {@code c-function.c}, which allows seven kinds
 * of thing and refuses everything else with the whole specification as the
 * argument -- "Report full invalid function spec block in the error", says the
 * comment above the line, and it is the more useful of the two because a stray
 * set-word means little without the specification around it.
 *
 * <p>The duplicate check is separate and comes first. {@code Collect_Frame}
 * walks the block before anything is validated, so a word repeated anywhere is
 * a duplicate -- between two arguments, between two locals, or between an
 * argument and a local. JEBOL skipped everything after {@code /local}, which
 * let two names for one slot through.
 *
 * <p>Every expectation was read off a real 3.22.5 first.
 */
class AFunctionSpecFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorFrom(String source) {
        return answerTo("""
                e: try [%s]
                either error? e [reduce [e/id e/arg1]] ['no-error]"""
                .formatted(source));
    }

    @Test
    @DisplayName("a name used twice is a duplicate, wherever the second one is")
    void aNameUsedTwiceIsADuplicate() {
        assertThat(errorFrom("func [a a][]")).isEqualTo("[dup-vars a]");
        assertThat(errorFrom("func [a /local b b][]")).isEqualTo("[dup-vars b]");
        assertThat(errorFrom("func [a /local a][]")).isEqualTo("[dup-vars a]");
        assertThat(errorFrom("func [/b /local b][]")).isEqualTo("[dup-vars b]");
    }

    /**
     * Named as it was written rather than as it was spelled, so a repeated
     * refinement reports the slash with it. {@code Trap1(RE_DUP_VARS, value)}
     * hands over the value from the block itself.
     */
    @Test
    @DisplayName("and a repeated refinement is named with its slash")
    void aRepeatedRefinementIsNamedWithItsSlash() {
        assertThat(errorFrom("func [a /x /x][]")).isEqualTo("[dup-vars /x]");
    }

    @Test
    @DisplayName("a set-word cannot be a parameter, and the error shows the whole spec")
    void aSetWordCannotBeAParameter() {
        assertThat(errorFrom("func [a:][]")).isEqualTo("[bad-func-def [a:]]");
        assertThat(errorFrom("func [a b:][]")).isEqualTo("[bad-func-def [a b:]]");
    }

    /**
     * Red writes a function that way and the C allows it so the same
     * definition reads in both -- "It will be ignored while evaluating", says
     * the comment, and it is.
     */
    @Test
    @DisplayName("except RETURN: followed by a block, which is allowed and ignored")
    void exceptReturnFollowedByABlock() {
        assertThat(answerTo("""
                f: func [a return: [integer!]][a + 1]  f 1""")).isEqualTo("2");
        assertThat(errorFrom("func [a return:][]"))
                .isEqualTo("[bad-func-def [a return:]]");
    }

    @Test
    @DisplayName("and an issue cannot be one either")
    void anIssueCannotBeOneEither() {
        assertThat(errorFrom("func [#a][]")).isEqualTo("[bad-func-def [#a]]");
    }

    /**
     * A whole number is allowed and means nothing. The C says why beside the
     * case it falls into: "special case used by datatype test actions", which
     * write their own type number into the specification.
     */
    @Test
    @DisplayName("but a whole number is allowed, and contributes no parameter")
    void aWholeNumberIsAllowed() {
        assertThat(answerTo("f: func [a 1][a]  f 9")).isEqualTo("9");
    }

    @Test
    @DisplayName("and the ordinary parts are all still accepted")
    void theOrdinaryPartsAreStillAccepted() {
        assertThat(answerTo("""
                f: func ["a title" a [integer!] "what a is" /b c :d 'e /local g][
                    reduce [a b c]
                ]
                answered: f 1
                answered""")).isEqualTo("[1 _ _]");
    }
}

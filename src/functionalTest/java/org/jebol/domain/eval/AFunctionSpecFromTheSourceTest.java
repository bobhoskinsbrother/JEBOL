package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AFunctionSpecFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorFrom(String source) {
        return answerTo("""
                e: try [%s]
                either error? e [reduce [e/id e/arg1]] ['no-error]"""
                .formatted(source));
    }

    @Test
    @DisplayName("a name used twice is a duplicate, wherever the second one is")
    void refusesANameUsedTwiceAsADuplicate() {
        assertThat(readTheErrorFrom("func [a a][]")).isEqualTo("[dup-vars a]");
        assertThat(readTheErrorFrom("func [a /local b b][]")).isEqualTo("[dup-vars b]");
        assertThat(readTheErrorFrom("func [a /local a][]")).isEqualTo("[dup-vars a]");
        assertThat(readTheErrorFrom("func [/b /local b][]")).isEqualTo("[dup-vars b]");
    }

    @Test
    @DisplayName("and a repeated refinement is named with its slash")
    void namesARepeatedRefinementWithItsSlash() {
        assertThat(readTheErrorFrom("func [a /x /x][]")).isEqualTo("[dup-vars /x]");
    }

    @Test
    @DisplayName("a set-word cannot be a parameter, and the error shows the whole spec")
    void refusesASetWordAsAParameter() {
        assertThat(readTheErrorFrom("func [a:][]")).isEqualTo("[bad-func-def [a:]]");
        assertThat(readTheErrorFrom("func [a b:][]")).isEqualTo("[bad-func-def [a b:]]");
    }

    @Test
    @DisplayName("except RETURN: followed by a block, which is allowed and ignored")
    void exceptReturnFollowedByABlock() {
        assertThat(answerTo("""
                f: func [a return: [integer!]][a + 1]  f 1""")).isEqualTo("2");
        assertThat(readTheErrorFrom("func [a return:][]"))
                .isEqualTo("[bad-func-def [a return:]]");
    }

    @Test
    @DisplayName("and an issue cannot be one either")
    void refusesAnIssueAsAParameter() {
        assertThat(readTheErrorFrom("func [#a][]")).isEqualTo("[bad-func-def [#a]]");
    }

    @Test
    @DisplayName("but a whole number is allowed, and contributes no parameter")
    void allowsAWholeNumber() {
        assertThat(answerTo("f: func [a 1][a]  f 9")).isEqualTo("9");
    }

    @Test
    @DisplayName("and the ordinary parts are all still accepted")
    void stillAcceptsTheOrdinaryParts() {
        assertThat(answerTo("""
                f: func ["a title" a [integer!] "what a is" /b c :d 'e /local g][
                    reduce [a b c]
                ]
                answered: f 1
                answered""")).isEqualTo("[1 _ _]");
    }
}

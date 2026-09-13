package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DepthIsAStackOverflowFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String failureOf(String source) {
        return answerTo("e: try [" + source + """
                ]
                either error? e [reduce [e/type e/id]] [reduce ['ok]]""");
    }

    private static final String A_STRING_OF_OPENING_AND_CLOSING_BRACKETS = """
            nested: func [count /local text] [
                text: copy {}
                loop count [insert text {[}]
                loop count [append text {]}]
                text
            ]
            """;

    @Test
    @DisplayName("a function that calls itself for ever is an internal stack-overflow")
    void aFunctionThatCallsItselfForEverIsAStackOverflow() {
        assertThat(failureOf("""
                forever-down: does [forever-down]
                forever-down""")).isEqualTo("[Internal stack-overflow]");
    }

    @Test
    @DisplayName("and so are two functions that call each other")
    void twoFunctionsThatCallEachOtherAreAStackOverflow() {
        assertThat(failureOf("""
                there: does [back-again]
                back-again: does [there]
                there""")).isEqualTo("[Internal stack-overflow]");
    }

    @Test
    @DisplayName("source nested past what the reader will read is the same failure")
    void sourceNestedPastTheReaderIsTheSameFailure() {
        assertThat(answerTo(A_STRING_OF_OPENING_AND_CLOSING_BRACKETS + """
                e: try [load nested 10000]
                either error? e [reduce [e/type e/id]] [reduce ['ok]]"""))
                .isEqualTo("[Internal stack-overflow]");
    }

    @Test
    @DisplayName("nesting the reader does accept is read as the block it is")
    void nestingTheReaderAcceptsIsReadAsABlock() {
        assertThat(answerTo(A_STRING_OF_OPENING_AND_CLOSING_BRACKETS + """
                block? load nested 100""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the code says internal too, and neither failure carries an argument")
    void theCodeSaysInternalAndNeitherCarriesAnArgument() {
        assertThat(answerTo("""
                forever-down: does [forever-down]
                e: try [forever-down]
                reduce [e/code none? e/arg1]""")).isEqualTo("[903 #(true)]");
        assertThat(answerTo(A_STRING_OF_OPENING_AND_CLOSING_BRACKETS + """
                e: try [load nested 10000]
                reduce [e/code none? e/arg1]""")).isEqualTo("[903 #(true)]");
    }

    @Test
    @DisplayName("the id is one the catalogue names, which too-deep never was")
    void theIdIsOneTheCatalogueNames() {
        assertThat(answerTo("""
                forever-down: does [forever-down]
                e: try [forever-down]
                not none? select system/catalog/errors/internal e/id""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a recursion that ends on its own is not a stack overflow")
    void aRecursionThatEndsOnItsOwnIsNotAStackOverflow() {
        assertThat(answerTo("""
                countdown: func [n] [either n = 0 ['done] [countdown n - 1]]
                countdown 100""")).isEqualTo("done");
    }
}

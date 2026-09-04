package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A function definition is validated, read from {@code func-test.r3}: a
 * {@code return:} annotation must carry a type block, and no variable may
 * be named twice.
 */
class FunctionSpecValidationFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("return: with no block is not a definition")
    void returnWithNoBlockIsRefused() {
        assertThat(errorIdOf("func [return:] []")).isEqualTo("bad-func-def");
    }

    @Test
    @DisplayName("return: followed by a non-block is not a definition")
    void returnFollowedByANonBlockIsRefused() {
        assertThat(errorIdOf("func [return: \"\"] []")).isEqualTo("bad-func-def");
    }

    @Test
    @DisplayName("the invalid spec is carried in arg1")
    void theInvalidSpecIsInArg1() {
        assertThat(answerTo("e: try [func [return:] []] mold e/arg1"))
                .isEqualTo("\"[return:]\"");
    }

    @Test
    @DisplayName("a variable named twice is dup-vars, naming the word")
    void aVariableNamedTwiceIsDupVars() {
        assertThat(answerTo("""
                e: try [func [return: [] a return: []] []]
                reduce [e/id = 'dup-vars  e/arg1 = 'return]"""))
                .isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("a well-formed return: annotation still builds and round-trips")
    void aWellFormedReturnAnnotationBuilds() {
        assertThat(answerTo("""
                f: func [a [integer!] return: [integer!]] [return 2 * a]
                reduce [function? :f  f 3  (spec-of :f) = [a [integer!] return: [integer!]]]"""))
                .isEqualTo("[#(true) 6 #(true)]");
    }

    @Test
    @DisplayName("distinct parameters and a refinement are not duplicates")
    void distinctNamesAreNotDuplicates() {
        assertThat(answerTo("""
                f: func [a b /ref c] [a] function? :f""")).isEqualTo("#(true)");
    }
}

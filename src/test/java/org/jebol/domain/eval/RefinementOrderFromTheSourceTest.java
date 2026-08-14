package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which argument belongs to which refinement, read out of {@code Do_Args}
 * in {@code src/core/c-do.c}.
 *
 * <p>The values after a call are read in the order the path wrote its
 * refinements, not in the order the function declares them. The C says so
 * in as many words: when the path names a refinement that is not the next
 * one in the spec, it restarts the spec walk at that refinement, under a
 * comment reading "refinement out of sequence, resequence arg order".
 *
 * <p>Reading them in declared order agrees with the path whenever the two
 * orders happen to match, which is most calls, so the defect hides until
 * someone writes the refinements the other way round. SORT is where it
 * showed: {@code sort/compare/skip s 1 3} sorted by the wrong column and
 * gave an answer that looked plausible.
 */
class RefinementOrderFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a user function reads its refinement arguments in path order")
    void aFunctionFollowsThePath() {
        String declare = "f: func [/one a /two b] [reduce [a b]] ";
        assertThat(answerTo(declare + "(f/one/two 1 2) = [1 2]"))
                .as("path order and declared order agree here")
                .isEqualTo("#(true)");
        assertThat(answerTo(declare + "(f/two/one 1 2) = [2 1]"))
                .as("written the other way round, the values swap")
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a refinement taking two arguments keeps them together")
    void aChunkStaysWhole() {
        String declare = "f: func [/one a b /two c] [reduce [a b c]] ";
        assertThat(answerTo(declare + "(f/two/one 3 1 2) = [1 2 3]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the required arguments come first whatever the path says")
    void theRequiredOnesAreUnmoved() {
        String declare = "f: func [x /one a /two b] [reduce [x a b]] ";
        assertThat(answerTo(declare + "(f/two/one 0 1 2) = [0 2 1]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("one refinement is the same either way")
    void theDegenerateCase() {
        String declare = "f: func [/one a] [a] ";
        assertThat(answerTo(declare + "(f/one 1) = 1")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a refinement with no argument of its own takes nothing")
    void anArgumentlessRefinementIsSkipped() {
        String declare = "f: func [/flag /one a] [reduce [flag a]] ";
        assertThat(answerTo(declare + "(f/one/flag 1) = [#(true) 1]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("SORT reads its comparator and its record size in path order")
    void sortFollowsThePath() {
        assertThat(answerTo("(sort/compare/skip \"ba ab aa \" 1 3) == \"ab aa ba \""))
                .isEqualTo("#(true)");
        assertThat(answerTo("(sort/compare/skip \"ba ab aa \" 2 3) == \"ba aa ab \""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("SORT written the declared way round means the same thing")
    void sortAgreesWithItself() {
        assertThat(answerTo("(sort/skip/compare \"ba ab aa \" 3 1) == \"ab aa ba \""))
                .isEqualTo("#(true)");
    }
}

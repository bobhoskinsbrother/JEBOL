package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A run of slashes is an ordinary word, and can be assigned to and read from.
 *
 * <p>{@code /} divides and {@code //} divides as whole numbers -- both are rows of
 * {@code boot/ops.reb}, where {@code %} is the one that takes a remainder. So both
 * are words the library binds to functions. Which means a script has to be able to write
 * {@code /: :my-divide} to rebind one, and {@code :/} to pass it around -- and
 * neither spelling works by accident, because a slash is a
 * {@code LEX_DELIMIT_SLASH} and would otherwise end the token before the colon.
 *
 * <p>So the C gives each its own arm, and the get-word arm says why in a comment:
 * "must be modified, because / is delimiter!". Both arms walk the run of slashes
 * and then insist a delimiter follows it.
 *
 * <p>The trap is that a *refinement* may not end in a colon -- {@code /a:} is
 * refused -- and that check lives in a different arm of the same case. Reading the
 * two as one rule refuses {@code /:} along with {@code /a:}, which is what
 * happened here: what sits between the slashes and the colon is the whole
 * difference.
 */
class SlashWordFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFromLoading(String source) {
        return answerTo("e: try [load " + source + "] "
                + "either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("a run of slashes on its own")
    class ThePlainWord {

        @Test
        @DisplayName("is a word, however long the run")
        void aRunOfSlashesIsAWord() {
            // `scan_state->end = cp; return TOKEN_WORD;` -- the last arm of the
            // slash case, reached when neither a name nor a colon follows.
            assertThat(answerTo("""
                    (load {/}) = to word! "/\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {//}) = to word! "//\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {///}) = to word! "///\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the first two are operators the boot table binds")
        void theyAreRealOperators() {
            // Which is the reason all of this matters. `/` and `//` are not
            // punctuation the reader invented a rule for; they are two of the rows
            // of `boot/ops.reb`, which "maps infix operator symbols to function
            // names":
            //
            //     /  divide
            //     // integer-divide
            //     %  remainder
            //
            // So `//` is integer division and *not* remainder, which is `%`. Worth
            // pinning, because the spelling suggests the other one -- it is what
            // this test claimed before the table was read.
            assertThat(answerTo("""
                    9 / 2""")).isEqualTo("4.5");
            assertThat(answerTo("""
                    9 // 2""")).isEqualTo("4");
        }
    }

    @Nested
    @DisplayName("assigning to one")
    class TheSetWord {

        @Test
        @DisplayName("a colon after the run makes a set-word")
        void aRunThenAColon() {
            // `if (*cp == ':' && IS_LEX_DELIMIT(cp[1])) { scan_state->end = cp+1;
            // return TOKEN_SET; }` -- Rebol's own lexer test asserts all three.
            assertThat(answerTo("""
                    set-word? load {/:}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    set-word? load {//:}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    set-word? load {///:}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it names the run without the colon")
        void whatItNames() {
            assertThat(answerTo("""
                    (load {//:}) = to set-word! "//\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but a name between the slash and the colon is refused")
        void aNamedRefinementWithAColon() {
            // The other arm of the same case: `if (*(scan_state->end - 1) == ':')
            // return -type;`, reached only when a word follows the slash run. So
            // `/a:` is a refused refinement and `/:` is a set-word, and what sits
            // between the slashes and the colon is the whole difference.
            //
            // Reading the two as one rule refuses both, which cost the three
            // set-words above.
            assertThat(errorIdFromLoading("""
                    {/a:}""")).isEqualTo("invalid");
        }
    }

    @Nested
    @DisplayName("reading one without calling it")
    class TheGetWord {

        @Test
        @DisplayName("a colon before the run makes a get-word")
        void aColonThenARun() {
            // `if (cp[1] == '/') { do { ++cp; } while (*cp == '/'); if
            // (IS_LEX_DELIMIT(*cp)) { scan_state->end = cp; // must be modified,
            // because / is delimiter! return TOKEN_GET; } }`
            assertThat(answerTo("""
                    get-word? load {:/}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    get-word? load {://}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    get-word? load {:///}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it names the run without the colon")
        void whatItNames() {
            assertThat(answerTo("""
                    (load {://}) = to get-word! "//\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it answers the function rather than calling it")
        void itDoesNotCall() {
            // The point of the spelling. `:/` hands over the divide function, so a
            // script can pass it to SORT or rebind it.
            assertThat(answerTo("""
                    any-function? :/""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but something that is not a delimiter after the run is refused")
        void aNameAfterTheRun() {
            // `else cp = scan_state->begin;` and then the ordinary word scan, which
            // fails: the delimiter test is what parts a slash word from a path.
            assertThat(errorIdFromLoading("""
                    {://x}""")).isNotEqualTo("no-error");
        }
    }
}

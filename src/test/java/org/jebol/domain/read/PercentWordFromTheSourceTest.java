package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A run of percent signs is a word, and what a sigil'd one may be followed by.
 *
 * <p>{@code %} is a word, and so are {@code %%} and {@code %%%}. The C gives them
 * their own arm in three token cases, each with the same comment -- "special words
 * like :%, :%%, :%%% etc..." -- because a lone percent would otherwise begin a file:
 *
 * <pre>
 * if (cp[1] == '%') {
 *     do { ++cp; } while (*cp == '%');
 *     return (IS_LEX_DELIMIT(*cp)) ? TOKEN_GET : -TOKEN_GET;
 * }
 * </pre>
 *
 * <p>A delimiter has to follow the run. A slash is a delimiter, so {@code '%/} gets
 * past that test as a lit-word -- and then the block scanner sees the slash and
 * builds a <em>path</em> out of it, which has no second segment and fails.
 *
 * <p>Which is why the error says {@code path} and not {@code word}: the token kind
 * reported is the one the reader had reached by the time it failed, and a script
 * reads it as ARG1. Rebol's own test asserts all four spellings.
 */
class PercentWordFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /** Whether reading this failed as a refused path, both halves at once. */
    private static String refusedAsAPath(String source) {
        return answerTo("e: try [transcode/one " + source + "] "
                + "all [error? e e/id = 'invalid e/arg1 = \"path\"]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("a run of percent signs is a word")
    class ThePercentWord {

        @Test
        @DisplayName("on its own, however long the run")
        void theRunAlone() {
            // `%` is also what begins a file, so the reader has to choose. A run
            // followed by a delimiter is the word; anything else starts a file.
            assertThat(answerTo("""
                    (transcode/one {%}) = to word! "%\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (transcode/one {%%}) = to word! "%%\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it takes a sigil like any other word")
        void withASigil() {
            // The same arm appears in the tick case and the colon case, which is
            // what makes `'%` and `:%` spellable at all.
            assertThat(answerTo("""
                    (transcode/one {'%}) = to lit-word! "%\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (transcode/one {:%}) = to get-word! "%\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (transcode/one {'%%}) = to lit-word! "%%\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but a percent that begins a name is a file, not a word")
        void aFileInstead() {
            // Which is the reason the run needs a delimiter after it: `%a` has to
            // stay a file, and only a bare run may be a word.
            assertThat(answerTo("""
                    file? transcode/one {%a}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    file? transcode/one {%/}""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("and a slash after a sigil'd one makes a path that cannot finish")
    class TheTrailingSlash {

        @Test
        @DisplayName("all four spellings Rebol's own test asserts")
        void theFourSpellings() {
            // A slash *is* a delimiter, so the percent arm is satisfied and the
            // lit-word is formed. The slash then makes it a path, and the path has
            // nothing after the slash to be a second segment.
            assertThat(refusedAsAPath("""
                    {'%/}""")).isEqualTo(TRUE);
            assertThat(refusedAsAPath("""
                    {:%/}""")).isEqualTo(TRUE);
            assertThat(refusedAsAPath("""
                    {'%%/}""")).isEqualTo(TRUE);
            assertThat(refusedAsAPath("""
                    {:%%/}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the error says path rather than word, because that is how far it got")
        void theKindReported() {
            // The distinction is the point of asserting ARG1 at all. The reader had
            // a perfectly good lit-word and then went on to build a path from it;
            // what failed was the path, and that is what it reports.
            assertThat(answerTo("""
                    e: try [transcode/one {'%/}] e/arg1""")).isEqualTo("\"path\"");
        }

        @Test
        @DisplayName("and every other missing segment reports the same kind")
        void anyMissingSegment() {
            // Nothing about the percent, in the end. A segment that is not there is
            // a path that cannot finish, however it was spelled.
            assertThat(refusedAsAPath("""
                    {a/}""")).isEqualTo(TRUE);
            assertThat(refusedAsAPath("""
                    {a//b}""")).isEqualTo(TRUE);
            assertThat(refusedAsAPath("""
                    {'a/}""")).isEqualTo(TRUE);
        }
    }
}

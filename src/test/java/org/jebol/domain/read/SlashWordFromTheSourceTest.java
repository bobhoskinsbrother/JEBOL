package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
            assertThat(answerTo("""
                    any-function? :/""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but something that is not a delimiter after the run is refused")
        void aNameAfterTheRun() {
            assertThat(errorIdFromLoading("""
                    {://x}""")).isNotEqualTo("no-error");
        }
    }
}

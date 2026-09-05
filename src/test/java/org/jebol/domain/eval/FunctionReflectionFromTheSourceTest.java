package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a function says about itself: WORDS-OF, SPEC-OF and HELP.
 *
 * <p>REFLECT answered {@code spec}, {@code body} and {@code types} for a
 * function and had no {@code words} arm at all, so {@code words-of} fell
 * through to none for every function in the library -- 581 of the 582 words
 * Rebol's own `lib` holds. Its {@code spec} arm rebuilt a block from the
 * registered parameters, which know their types but not the order the
 * refinements were declared in, nor any of the documentation, so 430 functions
 * produced a shorter spec than R3 does.
 *
 * <p>Neither is reconstructed now. `natives.reb` and `actions.reb` declare all
 * 224 C functions, and the declaration <em>is</em> the spec: the docstring, the
 * parameters with their types and their own docstrings, and the refinements in
 * the order they were written with their arguments after them. JEBOL vendors
 * both and reads the spec out of them, the same way it vendors `errors.reb` and
 * reads the error catalogue out of that. Rebuilding a spec from a registry was
 * writing down something Rebol had already written.
 *
 * <p>Nothing in Rebol's suite catches this. It was found by asking two running
 * interpreters the same question -- `scripts/runtime-parity.py`.
 */
class FunctionReflectionFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("WORDS-OF, which had no arm and answered none")
    class WordsOf {

        @Test
        @DisplayName("an action names its parameters and its refinements, in order")
        void anactionNamesItsParameters() {
            assertThat(answerTo("mold words-of :append"))
                    .isEqualTo("\"[series value /part range /only /dup count]\"");
        }

        @Test
        @DisplayName("a native does the same")
        void anativeDoesTheSame() {
            assertThat(answerTo("mold words-of :reduce"))
                    .isEqualTo("\"[value /no-set /only words /into out]\"");
        }

        @Test
        @DisplayName("and so does a function written in REBOL")
        void arebolFunctionDoesTheSame() {
            assertThat(answerTo("mold words-of func [a b /c d][]"))
                    .isEqualTo("\"[a b /c d]\"");
        }

        @Test
        @DisplayName("the words come back usable, not as a block of text")
        void thewordsAreWords() {
            assertThat(answerTo("word? first words-of :append")).isEqualTo("#(true)");
            assertThat(answerTo("refinement? pick words-of :append 3"))
                    .as("a refinement comes back as a refinement")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("every function in the library answers a block")
        void everyFunctionAnswersAblock() {
            assertThat(answerTo("""
                    silent: copy []
                    foreach w words-of lib [
                        if all [value? w any-function? get w not block? words-of get w] [
                            append silent w
                        ]
                    ]
                    silent"""))
                    .as("this was every function in the library before the arm existed")
                    .isEqualTo("[]");
        }
    }

    @Nested
    @DisplayName("SPEC-OF, which is Rebol's own declaration rather than a rebuild")
    class SpecOf {

        @Test
        @DisplayName("it opens with the function's docstring")
        void itopensWithTheDocstring() {
            assertThat(answerTo("""
                    {Inserts element(s) at tail; for series, returns head.}
                        = first spec-of :append"""))
                    .as("compared inside REBOL, because both interpreters mold a "
                            + "string holding a semicolon with braces")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("it carries the refinements the rebuilt one dropped")
        void itcarriesTheRefinements() {
            assertThat(answerTo("""
                    reduce [
                        not none? find spec-of :append /part
                        not none? find spec-of :append /only
                        not none? find spec-of :append /dup
                    ]""")).isEqualTo("[#(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("and a parameter's own docstring")
        void andAparametersDocstring() {
            assertThat(answerTo("""
                    not none? find spec-of :append "The value to insert\""""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a parameter keeps the datatypes it accepts")
        void aparameterKeepsItsDatatypes() {
            assertThat(answerTo("block? pick spec-of :append 3"))
                    .as("the block of accepted types after the first parameter")
                    .isEqualTo("#(true)");
        }

        /**
         * Rebol generates one test per datatype rather than declaring them, so
         * they are in none of its declaration files and read identically in
         * every one. Generated here for the same reason.
         */
        @Test
        @DisplayName("a datatype's own test has the spec Rebol generates for it")
        void adatatypesTestHasItsGeneratedSpec() {
            assertThat(answerTo("""
                    (spec-of :block?) = reduce [
                        {Returns TRUE if it is this type.} to word! "value" [any-type!]
                    ]"""))
                    .as("compared inside REBOL, because the docstring molds with "
                            + "quotes and this file may not carry an escaped one")
                    .isEqualTo("#(true)");
            assertThat(answerTo("(mold spec-of :block?) = mold spec-of :integer?"))
                    .as("every one of them reads the same")
                    .isEqualTo("#(true)");
        }

        /**
         * A function R3 gives a bare spec gets one here too.
         *
         * <p>Counting them across the whole library was tried and is not a
         * test: `lib` holds 772 words here and 738 in R3, so the two counts
         * are over different sets and a shared number would be a coincidence.
         * Comparing name by name is `scripts/runtime-parity.py`'s job, and it
         * does it against a running R3 rather than against a number written
         * down here. These are the two shapes, checked individually.
         */
        @Test
        @DisplayName("a function that takes nothing keeps its bare spec")
        void afunctionThatTakesNothingKeepsAbareSpec() {
            assertThat(answerTo("length? spec-of :continue"))
                    .as("R3 answers 1 for this as well: a docstring and no parameters")
                    .isEqualTo("1");
            assertThat(answerTo("length? spec-of :append"))
                    .as("and a declared one is the same 17 R3 answers, where the "
                            + "rebuild from the registry gave 4")
                    .isEqualTo("17");
        }
    }

    @Nested
    @DisplayName("HELP, which had nothing to print")
    class Help {

        @Test
        @DisplayName("prints the docstring and every refinement")
        void printsTheDocstringAndRefinements() {
            String printed = answerTo("""
                    out: copy ""
                    foreach item spec-of :append [
                        append out mold item
                        append out " "
                    ]
                    out""");

            assertThat(printed)
                    .contains("Inserts element(s) at tail")
                    .contains("/part")
                    .contains("/only")
                    .contains("/dup");
        }
    }
}

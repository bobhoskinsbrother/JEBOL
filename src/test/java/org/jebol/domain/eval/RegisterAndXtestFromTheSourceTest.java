package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REGISTER files a struct's layout in a catalogue; XTEST exercises structures
 * this build has not got.
 *
 * <p>{@code n-system.c}. A struct's layout describes how bytes are arranged,
 * and code laying that description over a binary wants the description rather
 * than an instance of it. {@code system/catalog/structs} is a map from names
 * to layouts and REGISTER is how one gets in -- which is why {@code
 * sysobj.reb} declares that field as {@code make map! []} with the comment
 * "filled using `register` native function". An empty map at boot is the
 * finished state, not a gap.
 *
 * <p>XTEST prints a coloured self-test of building a handle and reading its
 * data, length and identity. Those are the C's own structures rather than the
 * language's, so there is nothing here to exercise -- the position EVOKE's
 * debug chants are in, and it gets the same answer by the same name.
 *
 * <p>Specified in {@code spec/natives.allium} under REGISTER and XTEST.
 */
class RegisterAndXtestFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("REGISTER files a layout under a name")
    class TheCatalogue {

        @Test
        @DisplayName("the catalogue starts empty, which is where REGISTER fills it from")
        void itStartsEmpty() {
            assertThat(answerTo("map? system/catalog/structs")).isEqualTo(TRUE);
            assertThat(answerTo("empty? system/catalog/structs")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("it answers the struct it was given, so a caller can chain")
        void itAnswersTheStruct() {
            assertThat(answerTo("""
                    s: #(struct! [a [uint8!]])
                    struct? register my-struct s""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the layout is in the catalogue under that name afterwards")
        void theLayoutIsFiled() {
            assertThat(answerTo("""
                    s: #(struct! [a [uint8!]])
                    register my-struct s
                    mold select system/catalog/structs 'my-struct"""))
                    .isEqualTo("\"[a [uint8!]]\"");
        }

        @Test
        @DisplayName("the name is taken literally, not evaluated")
        void theNameIsLiteral() {
            assertThat(answerTo("""
                    my-struct: "something else"
                    s: #(struct! [a [uint8!]])
                    register my-struct s
                    true? select system/catalog/structs 'my-struct""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("registering the same layout again is allowed and changes nothing")
        void theSameLayoutAgainIsAllowed() {
            // So a file loaded twice does not fail on its second pass.
            assertThat(answerTo("""
                    s: #(struct! [a [uint8!]])
                    register twice s
                    struct? register twice s""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but a different layout under a taken name is already-used")
        void aDifferentLayoutIsRefused() {
            assertThat(answerTo("""
                    s: #(struct! [a [uint8!]])
                    t: #(struct! [b [uint16!]])
                    register taken s
                    e: try [register taken t] e/id""")).isEqualTo("already-used");
        }

        @Test
        @DisplayName("and the refusal names the word that was taken")
        void theRefusalNamesTheWord() {
            assertThat(answerTo("""
                    s: #(struct! [a [uint8!]])
                    t: #(struct! [b [uint16!]])
                    register taken s
                    e: try [register taken t] e/arg1 = 'taken""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("something that is not a struct is refused by the declaration")
        void aNonStructIsRefused() {
            assertThat(answerTo("""
                    e: try [register anything 5] e/id""")).isEqualTo("expect-arg");
            assertThat(answerTo("""
                    e: try [register anything "text"] e/id""")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("two names may hold the same layout, since neither is taken by the other")
        void twoNamesMayShareALayout() {
            assertThat(answerTo("""
                    s: #(struct! [a [uint8!]])
                    register first-name s
                    struct? register second-name s""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("XTEST has nothing here to test")
    class TheSelfTest {

        @Test
        @DisplayName("it refuses with feature-na, as EVOKE's debug chants do")
        void itRefuses() {
            assertThat(answerTo("e: try [xtest] e/id")).isEqualTo("feature-na");
        }

        @Test
        @DisplayName("and it takes no arguments, so the refusal is the whole of it")
        void itTakesNoArguments() {
            assertThat(answerTo("empty? spec-of :xtest")).isEqualTo(TRUE);
        }
    }
}

package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

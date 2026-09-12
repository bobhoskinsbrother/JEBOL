package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArrowWordFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("an arrow-like run is a word")
    class ThePlainWords {

        @Test
        @DisplayName("the short ones the C names as its common cases")
        void theCommonCases() {
            assertThat(answerTo("""
                    (load {<>}) = to word! "<>\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {<=}) = to word! "<=\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {<<}) = to word! "<<\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a lone angle bracket, which has its own guard")
        void aLoneAngle() {
            assertThat(answerTo("""
                    (load {<}) = to word! "<\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {>}) = to word! ">\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a longer run of arrow characters, however long")
        void theLongerRuns() {
            assertThat(answerTo("""
                    (load {<-->}) = to word! "<-->\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {<~~~>}) = to word! "<~~~>\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {<-==->}) = to word! "<-==->\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but a run that closes with a name inside it is a tag")
        void aTagIsStillATag() {
            assertThat(answerTo("""
                    tag? load {<a>}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    tag? load {</a>}""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("and a colon at the end makes it a set-word")
    class TheSetWords {

        @Test
        @DisplayName("all four spellings Rebol's own test asserts")
        void theFourSpellings() {
            assertThat(answerTo("""
                    set-word? load {<-->:}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    set-word? load {<==>:}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    set-word? load {<-==->:}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    set-word? load {<~~~>:}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it is one value, naming the run without the colon")
        void itIsOneValue() {
            assertThat(answerTo("""
                    length? load {[<-->:]}""")).isEqualTo("1");
            assertThat(answerTo("""
                    (load {<-->:}) = to set-word! "<-->\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the right-pointing ones take one too")
        void theRightArrows() {
            assertThat(answerTo("""
                    set-word? load {-->:}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {==>:}) = to set-word! "==>\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but a colon with a name after it is not the end of a word")
        void aColonInTheMiddle() {
            assertThat(answerTo("""
                    length? load {[<-->:x]}""")).isEqualTo("2");
        }
    }
}

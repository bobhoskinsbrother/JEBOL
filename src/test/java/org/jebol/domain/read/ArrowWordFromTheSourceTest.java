package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The arrow-like words, from {@code Skip_Left_Arrow}, {@code Skip_Right_Arrow} and
 * the two {@code Scan_Token} cases that call them.
 *
 * <p>A run starting with {@code <} that holds nothing but arrow characters is a
 * word, not a tag: {@code <>}, {@code <=}, {@code <-->} and {@code <~~~>} are all
 * words a script can bind. Which characters count is a short list --
 * {@code Skip_Left_Arrow} consumes the run of {@code <} and then any of
 * {@code - = > ~} -- and anything else in the run makes it a tag or a failure.
 *
 * <p>And a colon at the end belongs to the word. {@code Skip_Left_Arrow} consumes
 * it and stops -- {@code if (*cp == ':') { cp++; break; }} -- and the caller reads
 * the last character to decide which token it has:
 * {@code return (np[-1] == ':' ? TOKEN_SET : TOKEN_WORD);}. So {@code <-->:} is one
 * set-word, and without that it comes back as the word and a stray colon, which is
 * a set-word nobody can write.
 */
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

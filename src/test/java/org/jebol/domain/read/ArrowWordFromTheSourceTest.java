package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
            // `if (IS_LEX_DELIMIT(cp[2]) && (cp[1] == '>' || cp[1] == '=' ||
            // cp[1] == '<')) return TOKEN_WORD; // common cases: <> <= <<`
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
            // `if (IS_LEX_ANY_SPACE(cp[1]) || cp[1] == ']' || cp[1] == ')' ||
            // cp[1] == 0) return TOKEN_WORD; // CES.9121 Was LEX_DELIMIT --
            // changed for </tag>` -- the comment records that the guard was
            // narrowed from every delimiter, so that `</tag>` still reads as a tag.
            assertThat(answerTo("""
                    (load {<}) = to word! "<\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {>}) = to word! ">\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a longer run of arrow characters, however long")
        void theLongerRuns() {
            // `while (*cp == '<') cp++;` and then a loop over `- = > ~`. So the
            // arrows a dialect invents are words without the reader knowing about
            // the dialect.
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
            // Which is the whole reason the character list is short: `<a>` has to
            // stay a tag, and only a run of arrow characters may be a word.
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
            // `return (np[-1] == ':' ? TOKEN_SET : TOKEN_WORD);` -- the last
            // character of what the skipper consumed decides it.
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
            // The failure this replaces was two values: the word and a stray
            // colon. Which reads as an assignment and is not one, so a dialect
            // defining `<-->:` got a word it never asked for.
            // Counted inside a block, because `length?` on a word answers the
            // length of its spelling: `length? load {<-->:}` is 4 either way.
            assertThat(answerTo("""
                    length? load {[<-->:]}""")).isEqualTo("1");
            assertThat(answerTo("""
                    (load {<-->:}) = to set-word! "<-->\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the right-pointing ones take one too")
        void theRightArrows() {
            // `case LEX_SPECIAL_GREATER:` calls `Skip_Right_Arrow` and reads the
            // same last character, so the rule is one rule seen from either end.
            assertThat(answerTo("""
                    set-word? load {-->:}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {==>:}) = to set-word! "==>\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but a colon with a name after it is not the end of a word")
        void aColonInTheMiddle() {
            // The skipper stops at the colon, so what follows it is a separate
            // token rather than part of the name.
            assertThat(answerTo("""
                    length? load {[<-->:x]}""")).isEqualTo("2");
        }
    }
}

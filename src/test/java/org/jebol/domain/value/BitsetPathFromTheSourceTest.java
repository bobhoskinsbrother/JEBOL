package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reading and writing one bit of a set through a path, read out of
 * {@code PD_Bitset} in {@code src/core/t-bitset.c}.
 *
 * <p>Nine lines of C and two rules in them. Reading answers a logic and never
 * none. Writing minds the complement flag, and the flag inverts what true
 * means, which changes nothing for an ordinary set and everything for a
 * complemented one.
 *
 * <p>Rebol's own url-parser needs the write. It copies the URI character set
 * and then adds the percent sign, so that a URL which is already encoded is
 * left alone. Without the write, {@code sys-ports.reb} stops on that line and
 * takes MAKE-PORT* and the whole scheme registry with it.
 */
class BitsetPathFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";
    private static final String FALSE = "#(false)";

    @Nested
    @DisplayName("reading a bit")
    class Reading {

        @Test
        @DisplayName("a character the set holds answers true")
        void aHeldCharacterAnswersTrue() {
            assertThat(answerTo("b: charset \"abc\" b/(#\"a\")")).isEqualTo(TRUE);
            assertThat(answerTo("b: charset \"abc\" b/(#\"c\")")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a character it does not hold answers false, never none")
        void anAbsentCharacterAnswersFalse() {
            assertThat(answerTo("b: charset \"abc\" b/(#\"z\")")).isEqualTo(FALSE);
            assertThat(answerTo("b: charset \"abc\" logic? b/(#\"z\")")).isEqualTo(TRUE);
            assertThat(answerTo("b: charset \"abc\" none? b/(#\"z\")")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("a complemented set answers the other way round")
        void aComplementedSetIsInverted() {
            assertThat(answerTo("b: complement charset \"abc\" b/(#\"a\")")).isEqualTo(FALSE);
            assertThat(answerTo("b: complement charset \"abc\" b/(#\"z\")")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("writing a bit")
    class Writing {

        @Test
        @DisplayName("writing true puts a character in the set")
        void writingTrueAddsACharacter() {
            assertThat(answerTo("b: charset \"a\" b/(#\"z\"): true b/(#\"z\")"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("writing false takes one out")
        void writingFalseRemovesACharacter() {
            assertThat(answerTo("b: charset \"az\" b/(#\"z\"): false b/(#\"z\")"))
                    .isEqualTo(FALSE);
            assertThat(answerTo("b: charset \"az\" b/(#\"z\"): false b/(#\"a\")"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the set the word holds changes, so a parse rule sees it")
        void theSetItselfChanges() {
            assertThat(answerTo("b: charset \"a\" b/(#\"z\"): true "
                    + "parse \"z\" [some b]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("writing to a complemented set inverts what true means")
        void writingToAComplementedSetIsInverted() {
            assertThat(answerTo("b: complement charset \"a\" b/(#\"z\"): true b/(#\"z\")"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("b: complement charset \"a\" b/(#\"z\"): false b/(#\"z\")"))
                    .isEqualTo(FALSE);
            assertThat(answerTo("b: complement charset \"a\" b/(#\"z\"): true b/(#\"a\")"))
                    .isEqualTo(FALSE);
        }
    }

    @Nested
    @DisplayName("what this unblocks: Rebol's own url-parser")
    class WhatItUnblocks {

        @Test
        @DisplayName("the URI set can be copied and widened by one character")
        void theUriSetCanBeWidened() {
            assertThat(answerTo("bits: copy system/catalog/bitsets/uri "
                    + "bits/(#\"%\"): true bits/(#\"%\")")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the copy is independent of the catalogue's own set")
        void theCopyIsIndependent() {
            assertThat(answerTo("bits: copy system/catalog/bitsets/uri "
                    + "bits/(#\"%\"): true system/catalog/bitsets/uri/(#\"%\")"))
                    .isEqualTo(FALSE);
        }

        @Test
        @DisplayName("the four bitsets Rebol has in its catalogue and JEBOL had not")
        void theCatalogueIsComplete() {
            assertThat(answerTo("bitset? system/catalog/bitsets/uri")).isEqualTo(TRUE);
            assertThat(answerTo("bitset? system/catalog/bitsets/uri-component"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("bitset? system/catalog/bitsets/not-crlf")).isEqualTo(TRUE);
            assertThat(answerTo("bitset? system/catalog/bitsets/quoted-printable"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("URI holds what needs no encoding and not what does")
        void theUriSetIsRight() {
            assertThat(answerTo("system/catalog/bitsets/uri/(#\"a\")")).isEqualTo(TRUE);
            assertThat(answerTo("system/catalog/bitsets/uri/(#\"9\")")).isEqualTo(TRUE);
            assertThat(answerTo("system/catalog/bitsets/uri/(#\"/\")")).isEqualTo(TRUE);
            assertThat(answerTo("system/catalog/bitsets/uri/(#\" \")")).isEqualTo(FALSE);
            assertThat(answerTo("system/catalog/bitsets/uri/(#\"%\")")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("NOT-CRLF is the complement of CRLF, computed rather than written out")
        void notCrlfIsComputed() {
            assertThat(answerTo("system/catalog/bitsets/not-crlf/(#\"a\")")).isEqualTo(TRUE);
            assertThat(answerTo("system/catalog/bitsets/not-crlf/(#\"^/\")"))
                    .isEqualTo(FALSE);
        }
    }
}

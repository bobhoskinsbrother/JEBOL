package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * BINARY, the entry point of a little language for laying numbers into bytes.
 *
 * <p>{@code u-bincode.c}. A protocol is a sequence of fields of stated widths,
 * and writing one by hand means shifting and masking at every field. The
 * dialect states the widths instead: {@code [UI8 5 UI16 300]} writes one byte
 * then two, and {@code [UI8 UI16]} reads them back. That is what
 * {@code prot-tls.reb} is built on, TLS being nothing but framed fields of
 * stated widths.
 *
 * <p>Every byte pattern below was checked against a real 3.22.1, including
 * that writing answers the <em>context</em> rather than the bytes -- which is
 * what lets a caller write field after field, each call taking the last one's
 * answer.
 *
 * <p>Eighty-one codes exist in the C and this carries the ones for whole
 * numbers, position and raw bytes. A code outside that set raises rather than
 * being skipped, which is the decision here worth defending and is pinned
 * below: a dialect that ignores what it does not understand writes a message
 * of the wrong length and leaves the far end to discover it.
 *
 * <p>Specified in {@code spec/natives.allium} under the binary dialect.
 */
class BinaryDialectFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("the context")
    class TheContext {

        @Test
        @DisplayName("a size makes one, shaped like system/standard/bincode")
        void aSizeMakesAContext() {
            assertThat(answerTo("object? binary 64")).isEqualTo(TRUE);
            assertThat(answerTo("b: binary 64  b/type = 'bincode")).isEqualTo(TRUE);
            assertThat(answerTo("b: binary 64  mold words-of b"))
                    .isEqualTo("\"[type buffer buffer-write r-mask w-mask]\"");
        }

        @Test
        @DisplayName("and it carries a buffer for the bytes to go in")
        void itCarriesABuffer() {
            assertThat(answerTo("b: binary 64  binary? b/buffer")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("writing lays fields of stated widths")
    class TheWriting {

        @Test
        @DisplayName("one byte then two, most significant first")
        void aByteThenTwo() {
            assertThat(answerTo("b: binary/write #{} [UI8 5 UI16 300]  mold b/buffer"))
                    .isEqualTo("\"#{05012C}\"");
        }

        @Test
        @DisplayName("an LE suffix writes the same field the other way round")
        void littleEndianReverses() {
            assertThat(answerTo("b: binary/write #{} [UI16LE 300]  mold b/buffer"))
                    .isEqualTo("\"#{2C01}\"");
        }

        @Test
        @DisplayName("a three-byte field is three bytes, which no other width gives")
        void aThreeByteField() {
            assertThat(answerTo("b: binary/write #{} [UI24 65536]  mold b/buffer"))
                    .isEqualTo("\"#{010000}\"");
        }

        @Test
        @DisplayName("BYTES lays a binary in whole")
        void bytesLaysABinary() {
            assertThat(answerTo("b: binary/write #{} [BYTES #{AABB} UI8 1]  mold b/buffer"))
                    .isEqualTo("\"#{AABB01}\"");
        }

        @Test
        @DisplayName("and it answers the context, so writes can follow one another")
        void writingAnswersTheContext() {
            assertThat(answerTo("object? binary/write #{} [UI8 1]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("which is what lets a caller build a message field by field")
        void writesChain() {
            assertThat(answerTo("""
                    b: binary/write #{} [UI8 1]
                    c: binary/write b/buffer [UI8 2]
                    mold c/buffer""")).isEqualTo("\"#{0102}\"");
        }
    }

    @Nested
    @DisplayName("reading takes them back out")
    class TheReading {

        @Test
        @DisplayName("one value per code that produces one")
        void aValuePerCode() {
            assertThat(answerTo("mold binary/read #{05012C} [UI8 UI16]"))
                    .isEqualTo("\"[5 300]\"");
        }

        @Test
        @DisplayName("and it round trips whatever was written")
        void itRoundTrips() {
            assertThat(answerTo("""
                    b: binary/write #{} [UI8 5 UI16 300 UI24 65536]
                    (mold binary/read b/buffer [UI8 UI16 UI24]) = {[5 300 65536]}"""))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a little-endian field reads back the way it was written")
        void littleEndianRoundTrips() {
            assertThat(answerTo("mold binary/read #{2C01} [UI16LE]"))
                    .isEqualTo("\"[300]\"");
        }

        @Test
        @DisplayName("a signed field with its top bit set reads back negative")
        void aSignedFieldIsNegative() {
            // Or an SI8 of -1 reads as 255: the same eight bits and a
            // different number.
            assertThat(answerTo("mold binary/read #{FF} [SI8]")).isEqualTo("\"[-1]\"");
            assertThat(answerTo("mold binary/read #{FFFF} [SI16]")).isEqualTo("\"[-1]\"");
        }

        @Test
        @DisplayName("while the unsigned form of the same bytes is positive")
        void theUnsignedFormIsPositive() {
            assertThat(answerTo("mold binary/read #{FF} [UI8]")).isEqualTo("\"[255]\"");
            assertThat(answerTo("mold binary/read #{FFFF} [UI16]")).isEqualTo("\"[65535]\"");
        }

        @Test
        @DisplayName("SKIP moves without producing a value")
        void skipProducesNothing() {
            assertThat(answerTo("mold binary/read #{0102030405} [SKIP 2 UI8]"))
                    .isEqualTo("\"[3]\"");
        }

        @Test
        @DisplayName("and AT moves to a numbered position, counting from one")
        void atMovesToAPosition() {
            assertThat(answerTo("mold binary/read #{0102030405} [AT 4 UI8]"))
                    .isEqualTo("\"[4]\"");
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class TheRefusals {

        @Test
        @DisplayName("a code this build has not got is refused by name, never skipped")
        void anUnknownCodeIsRefused() {
            // The decision worth defending. Skipping would look friendlier
            // and write a message of the wrong length, leaving the reader at
            // the far end to find out.
            assertThat(answerTo("""
                    e: try [binary/write #{} [NOSUCH 1]] e/id""")).isEqualTo("feature-na");
        }

        @Test
        @DisplayName("and the refusal names the code that was wrong")
        void theRefusalNamesTheCode() {
            assertThat(answerTo(
                    "e: try [binary/write #{} [NOSUCH 1]] true? find form e/arg1 {nosuch}"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a code needing a value and not given one is missing-arg")
        void aCodeWithoutItsValueIsRefused() {
            assertThat(answerTo("""
                    e: try [binary/write #{} [UI8]] e/id""")).isEqualTo("missing-arg");
        }

        @Test
        @DisplayName("and something that is not a code at all is invalid-arg")
        void aNonCodeIsRefused() {
            assertThat(answerTo("""
                    e: try [binary/write #{} [5 5]] e/id""")).isEqualTo("invalid-arg");
        }
    }
}

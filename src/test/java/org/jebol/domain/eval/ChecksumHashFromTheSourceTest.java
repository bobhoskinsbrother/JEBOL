package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code checksum/with value 'hash size}, the one CHECKSUM method that is not
 * a checksum.
 *
 * <p>It answers {@code Hash_Value(value) % size}: a slot in a table of that
 * many slots, rather than a digest. R3 keeps it out of
 * {@code system/catalog/checksums} because it is not one, and its own docs say
 * the number may change between versions -- but the number this Rebol answers
 * is the number JEBOL has to answer, so every figure below was read off a
 * running 3.22.5 first.
 *
 * <p>The mixing is MurmurHash3, and which unit it mixes depends on the
 * datatype. A binary goes four bytes at a time, little endian, case
 * sensitively. A string goes one byte of its UTF-8 at a time, and each byte is
 * lowered on its own -- so only the letters that encode to a single byte fold
 * their case at all. That is the surprise: a real Rebol hashes {@code "é"} and
 * {@code "É"} to different slots while hashing {@code "a"} and {@code "A"} to
 * the same one, because the two bytes of {@code é} are lowered as though each
 * were a whole character.
 */
class ChecksumHashFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("the slot a value lands in")
    class TheSlot {

        @Test
        @DisplayName("the suite's own case: a thousand binaries all land inside the table")
        void everySlotIsInsideTheTable() {
            assertThat(answerTo("""
                    inside: true
                    repeat i 1024 [
                        slot: checksum/with to binary! i 'hash 64
                        inside: all [inside slot >= 0 slot < 64]
                    ]
                    inside""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a binary mixes four bytes at a time, so a fifth byte moves it a long way")
        void aBinaryMixesFourBytesAtATime() {
            assertThat(answerTo("""
                    reduce [
                        checksum/with #{} 'hash 64
                        checksum/with #{00} 'hash 64
                        checksum/with #{FFFEFDFC} 'hash 1000000
                        checksum/with #{FFFEFDFCFB} 'hash 1000000
                        checksum/with #{0102030405} 'hash 1000000
                    ]""")).isEqualTo("[0 55 852719 446155 886613]");
        }

        @Test
        @DisplayName("a string hashes its bytes, not the binary holding them")
        void aStringIsNotItsBytes() {
            assertThat(answerTo("""
                    reduce [
                        checksum/with "abc" 'hash 1000000
                        checksum/with #{616263} 'hash 1000000
                    ]""")).isEqualTo("[827478 205489]");
        }

        @Test
        @DisplayName("and the empty string is not the empty binary either")
        void theEmptyStringIsNotTheEmptyBinary() {
            assertThat(answerTo("""
                    reduce [
                        checksum/with "" 'hash 1000000
                        checksum/with #{} 'hash 1000000
                    ]""")).isEqualTo("[14 0]");
        }
    }

    @Nested
    @DisplayName("which letters fold their case")
    class TheFolding {

        @Test
        @DisplayName("ASCII folds, so the same word in either case is the same slot")
        void asciiFolds() {
            assertThat(answerTo("""
                    (checksum/with "abc" 'hash 1000000)
                        = checksum/with "ABC" 'hash 1000000""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an accented letter does not, being two bytes lowered separately")
        void anAccentedLetterDoesNot() {
            assertThat(answerTo("""
                    reduce [
                        checksum/with "é" 'hash 1000000
                        checksum/with "É" 'hash 1000000
                        checksum/with "š" 'hash 1000000
                        checksum/with "Š" 'hash 1000000
                    ]""")).isEqualTo("[706972 333875 744400 948957]");
        }

        @Test
        @DisplayName("a whole accented word, which is where the suite would have caught it")
        void aWholeAccentedWord() {
            assertThat(answerTo("""
                    reduce [
                        checksum/with "šiška" 'hash 1000000
                        checksum/with "ši" 'hash 1000000
                        checksum/with "ÿ" 'hash 1000000
                        checksum/with "Ā" 'hash 1000000
                    ]""")).isEqualTo("[177366 68728 297825 478326]");
        }
    }

    @Nested
    @DisplayName("the size, which is how many slots there are")
    class TheSize {

        @Test
        @DisplayName("one slot means slot zero, whatever the value")
        void oneSlotMeansSlotZero() {
            assertThat(answerTo("""
                    reduce [
                        checksum/with #{} 'hash 1
                        checksum/with #{00} 'hash 1
                        checksum/with "abcdef" 'hash 1
                    ]""")).isEqualTo("[0 0 0]");
        }

        @Test
        @DisplayName("and nothing or less is read as one rather than dividing by zero")
        void nothingOrLessIsReadAsOne() {
            assertThat(answerTo("""
                    reduce [
                        checksum/with #{00} 'hash 0
                        checksum/with #{00} 'hash -5
                    ]""")).isEqualTo("[0 0]");
        }

        @Test
        @DisplayName("the size is counted in thirty-two bits, so a bigger one wraps first")
        void theSizeIsCountedInThirtyTwoBits() {
            assertThat(answerTo("""
                    reduce [
                        checksum/with #{00} 'hash 4294967295
                        checksum/with #{00} 'hash 100000000000
                    ]""")).isEqualTo("[1364076727 148324535]");
        }

        @Test
        @DisplayName("and a size that wraps to none leaves the hash whole")
        void aSizeThatWrapsToNone() {
            assertThat(answerTo("""
                    checksum/with #{00} 'hash 4294967296""")).isEqualTo("1364076727");
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class TheRefusals {

        private static String errorIdFrom(String source) {
            return answerTo("failure: try [" + source + "] failure/id");
        }

        @Test
        @DisplayName("no /WITH at all, because the table has to have a size")
        void noWithAtAll() {
            assertThat(errorIdFrom("checksum #{00} 'hash")).isEqualTo("missing-arg");
        }

        @Test
        @DisplayName("a /WITH that is not a number, because a key is not a size")
        void aWithThatIsNotANumber() {
            assertThat(errorIdFrom("""
                    checksum/with #{00} 'hash {x}""")).isEqualTo("bad-refine");
            assertThat(errorIdFrom("""
                    checksum/with #{00} 'hash #{FF}""")).isEqualTo("bad-refine");
        }

        @Test
        @DisplayName("and the other way about: a number where a digest wanted a key")
        void aNumberWhereADigestWantedAKey() {
            assertThat(errorIdFrom("""
                    checksum/with #{00} 'md5 5""")).isEqualTo("bad-refine");
            assertThat(errorIdFrom("""
                    checksum/with #{00} 'sha256 5""")).isEqualTo("bad-refine");
        }

        @Test
        @DisplayName("a /WITH on a sum that has no use for one is the refinements quarrelling")
        void aWithOnASumThatHasNoUseForOne() {
            assertThat(errorIdFrom("""
                    checksum/with #{00} 'crc32 5""")).isEqualTo("bad-refines");
            assertThat(errorIdFrom("""
                    checksum/with #{00} 'adler32 5""")).isEqualTo("bad-refines");
            assertThat(errorIdFrom("""
                    checksum/with #{00} 'crc24 5""")).isEqualTo("bad-refines");
            assertThat(errorIdFrom("""
                    checksum/with #{00} 'tcp 5""")).isEqualTo("bad-refines");
        }

        @Test
        @DisplayName("and a method nobody has heard of")
        void aMethodNobodyHasHeardOf() {
            assertThat(errorIdFrom("checksum #{00} 'nosuch")).isEqualTo("invalid-arg");
        }
    }

    @Nested
    @DisplayName("the catalogue, which HASH is deliberately not in")
    class TheCatalogue {

        @Test
        @DisplayName("HASH is not a checksum, so it is not listed as one")
        void hashIsNotListed() {
            assertThat(answerTo("""
                    find system/catalog/checksums 'hash""")).isEqualTo("_");
        }

        @Test
        @DisplayName("though everything the catalogue does list answers to CHECKSUM")
        void everythingListedAnswers() {
            assertThat(answerTo("""
                    every-one-works: true
                    foreach method system/catalog/checksums [
                        every-one-works: all [
                            every-one-works
                            not error? try [checksum #{00} method]
                        ]
                    ]
                    every-one-works""")).isEqualTo("#(true)");
        }
    }
}

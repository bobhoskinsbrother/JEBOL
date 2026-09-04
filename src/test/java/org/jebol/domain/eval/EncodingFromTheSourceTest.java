package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Turning bytes into text and back: ENHEX, DEHEX, ENBASE, DEBASE, CHECKSUM,
 * COMPRESS and DECOMPRESS.
 *
 * <p>Read out of {@code n-strings.c} and {@code u-compress.c}. Each spec is
 * the one declared there, verbatim.
 *
 * <p>These are here rather than deferred with the rest of the codecs because
 * DECODE-URL cannot run without ENHEX: Rebol's own url-parser opens with
 * {@code url: to binary! enhex/except url enhex-bits}, so every caller of
 * DECODE-URL raised not-defined until this existed.
 *
 * <p>Specified in {@code spec/natives.allium} under "Encoding bytes as text,
 * and back".
 */
class EncodingFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("ENHEX and DEHEX: percent encoding")
    class PercentEncoding {

        @Test
        @DisplayName("a character the target will not carry becomes a percent escape")
        void aSpaceBecomesAnEscape() {
            assertThat(answerTo("enhex \"a b\"")).isEqualTo("\"a%20b\"");
        }

        @Test
        @DisplayName("and one it will carry is left alone")
        void aLetterIsLeftAlone() {
            assertThat(answerTo("enhex \"abc\"")).isEqualTo("\"abc\"");
        }

        @Test
        @DisplayName("DEHEX reads it back")
        void dehexReadsItBack() {
            assertThat(answerTo("dehex \"a%20b\"")).isEqualTo("\"a b\"");
        }

        @Test
        @DisplayName("a percent sign that is not an escape stands for itself")
        void aLonePercentStandsForItself() {
            assertThat(answerTo("dehex \"100%\"")).isEqualTo("\"100%\"");
            assertThat(answerTo("dehex \"a%zz\"")).isEqualTo("\"a%zz\"");
        }

        @Test
        @DisplayName("the two are inverses over every byte")
        void theyAreInverses() {
            assertThat(answerTo(
                    "s: \"a b/c?d=e&f%g\" (dehex enhex s) = s")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a file and a url keep the characters a path needs")
        void aUrlKeepsItsPunctuation() {
            assertThat(answerTo("enhex http://a.com/b")).isEqualTo("http://a.com/b");
        }

        @Test
        @DisplayName("/ESCAPE changes the escape character")
        void escapeChangesTheCharacter() {
            assertThat(answerTo("enhex/escape \"a b\" #\"=\"")).isEqualTo("\"a=20b\"");
            assertThat(answerTo("dehex/escape \"a=20b\" #\"=\"")).isEqualTo("\"a b\"");
        }

        @Test
        @DisplayName("/EXCEPT names the set to leave alone")
        void exceptNamesTheSet() {
            assertThat(answerTo(
                    "b: copy system/catalog/bitsets/uri b/(#\"%\"): true "
                    + "enhex/except \"a%20b\" b")).isEqualTo("\"a%20b\"");
        }

        @Test
        @DisplayName("/URI spells a space as a plus, or an underscore after =")
        void theUriFormSpellsASpaceSpecially() {
            assertThat(answerTo("enhex/uri \"a b\"")).isEqualTo("\"a+b\"");
            assertThat(answerTo("enhex/uri/escape \"a b\" #\"=\"")).isEqualTo("\"a_b\"");
            assertThat(answerTo("dehex/uri \"a+b\"")).isEqualTo("\"a b\"");
        }

        @Test
        @DisplayName("a binary is encoded byte by byte, not decoded first")
        void aBinaryIsEncodedByteByByte() {
            assertThat(answerTo("enhex #{4142}")).isEqualTo("\"AB\"");
            assertThat(answerTo("enhex #{00FF}")).isEqualTo("\"%00%FF\"");
        }

        @Test
        @DisplayName("a character above ASCII becomes its UTF-8 bytes, one escape each")
        void aWideCharacterBecomesItsBytes() {
            assertThat(answerTo("enhex \"é\"")).isEqualTo("\"%C3%A9\"");
        }
    }

    @Nested
    @DisplayName("ENBASE and DEBASE")
    class Bases {

        @Test
        @DisplayName("base 16 is hexadecimal")
        void base16() {
            assertThat(answerTo("enbase/flat #{DECAFBAD} 16")).isEqualTo("\"DECAFBAD\"");
            assertThat(answerTo("debase \"DECAFBAD\" 16")).isEqualTo("#{DECAFBAD}");
        }

        @Test
        @DisplayName("base 2 is bits")
        void base2() {
            assertThat(answerTo("enbase/flat #{01} 2")).isEqualTo("\"00000001\"");
            assertThat(answerTo("debase \"00000001\" 2")).isEqualTo("#{01}");
        }

        @Test
        @DisplayName("base 64 is what a URL and an email carry")
        void base64() {
            assertThat(answerTo("enbase/flat #{414243} 64")).isEqualTo("\"QUJD\"");
            assertThat(answerTo("debase \"QUJD\" 64")).isEqualTo("#{414243}");
        }

        @Test
        @DisplayName("base 64 pads to a multiple of four")
        void base64Pads() {
            assertThat(answerTo("enbase/flat #{41} 64")).isEqualTo("\"QQ==\"");
            assertThat(answerTo("debase \"QQ==\" 64")).isEqualTo("#{41}");
        }

        @Test
        @DisplayName("/URL is base 64 with the filename-safe alphabet")
        void base64Url() {
            assertThat(answerTo("find enbase/flat/url #{FBFF} 64 \"_\"")).isNotEqualTo("_");
            assertThat(answerTo("debase/url enbase/flat/url #{FBFF} 64 64"))
                    .isEqualTo("#{FBFF}");
        }

        @Test
        @DisplayName("a string is encoded as its UTF-8 bytes")
        void aStringIsItsBytes() {
            assertThat(answerTo("enbase/flat \"ABC\" 64")).isEqualTo("\"QUJD\"");
        }

        @Test
        @DisplayName("the two are inverses over every base")
        void theyAreInverses() {
            for (String base : new String[] {"2", "16", "64"}) {
                assertThat(answerTo(
                        "b: #{00FF10DECAFBAD} b = debase enbase/flat b " + base
                        + " " + base))
                        .as("enbase then debase in base " + base)
                        .isEqualTo(TRUE);
            }
        }

        @Test
        @DisplayName("a base that is not one of the five is refused")
        void anUnknownBaseIsRefused() {
            assertThat(errorIdFrom("enbase #{00} 8")).isNotEqualTo("no-error");
            assertThat(errorIdFrom("debase \"00\" 8")).isNotEqualTo("no-error");
            assertThat(errorIdFrom("enbase #{00} 0")).isNotEqualTo("no-error");
            assertThat(errorIdFrom("enbase #{00} -16")).isNotEqualTo("no-error");
        }
    }

    @Nested
    @DisplayName("CHECKSUM")
    class Checksums {

        @Test
        @DisplayName("the methods the host offers are in the catalogue")
        void theCatalogueNamesThem() {
            assertThat(answerTo("true? find system/catalog/checksums 'md5"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("true? find system/catalog/checksums 'sha256"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("MD5 of the empty string is the value everything else agrees on")
        void md5OfNothing() {
            assertThat(answerTo("enbase/flat checksum \"\" 'md5 16"))
                    .isEqualTo("\"D41D8CD98F00B204E9800998ECF8427E\"");
        }

        @Test
        @DisplayName("SHA1 and SHA256 likewise")
        void theShaFamily() {
            assertThat(answerTo("enbase/flat checksum \"abc\" 'sha1 16"))
                    .isEqualTo("\"A9993E364706816ABA3E25717850C26C9CD0D89D\"");
            assertThat(answerTo("enbase/flat checksum \"abc\" 'sha256 16"))
                    .isEqualTo("{BA7816BF8F01CFEA414140DE5DAE2223"
                            + "B00361A396177A9CB410FF61F20015AD}");
        }

        @Test
        @DisplayName("a string is hashed as its UTF-8 bytes, so text and binary agree")
        void aStringIsHashedAsBytes() {
            assertThat(answerTo(
                    "(checksum \"abc\" 'md5) = checksum #{616263} 'md5")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the answer is a binary of the method's own length")
        void theLengthFollowsTheMethod() {
            assertThat(answerTo("length? checksum \"a\" 'md5")).isEqualTo("16");
            assertThat(answerTo("length? checksum \"a\" 'sha1")).isEqualTo("20");
            assertThat(answerTo("length? checksum \"a\" 'sha256")).isEqualTo("32");
            assertThat(answerTo("length? checksum \"a\" 'sha512")).isEqualTo("64");
        }

        @Test
        @DisplayName("CRC32 and ADLER32 answer a number, not a digest")
        void theCyclicChecksums() {
            assertThat(answerTo("integer? checksum \"a\" 'crc32")).isEqualTo(TRUE);
            assertThat(answerTo("integer? checksum \"a\" 'adler32")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a method the host has not got is refused")
        void anUnknownMethodIsRefused() {
            assertThat(errorIdFrom("checksum \"a\" 'invented")).isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("/WITH a key makes it an HMAC, which differs from the plain digest")
        void withAKeyIsAnHmac() {
            assertThat(answerTo(
                    "(checksum/with \"a\" 'sha256 \"k\") <> checksum \"a\" 'sha256"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("length? checksum/with \"a\" 'sha256 \"k\""))
                    .isEqualTo("32");
        }

        @Test
        @DisplayName("/PART limits how much is read")
        void partLimitsTheInput() {
            assertThat(answerTo(
                    "(checksum/part \"abcdef\" 'md5 3) = checksum \"abc\" 'md5"))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("COMPRESS and DECOMPRESS")
    class Compression {

        @Test
        @DisplayName("the methods the host offers are in the catalogue")
        void theCatalogueNamesThem() {
            assertThat(answerTo("true? find system/catalog/compressions 'zlib"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("true? find system/catalog/compressions 'gzip"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the two are inverses, which is the whole of what a caller needs")
        void theyAreInverses() {
            for (String method : new String[] {"'zlib", "'gzip", "'deflate"}) {
                assertThat(answerTo(
                        "b: to binary! \"the same text repeated, the same text repeated\" "
                        + "b = decompress (compress b " + method + ") " + method))
                        .as("compress then decompress with " + method)
                        .isEqualTo(TRUE);
            }
        }

        @Test
        @DisplayName("compressing repetitive data makes it smaller")
        void itCompresses() {
            assertThat(answerTo(
                    "b: to binary! \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\" "
                    + "(length? compress b 'zlib) < length? b")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a string is compressed as its UTF-8 bytes")
        void aStringIsItsBytes() {
            assertThat(answerTo(
                    "(compress \"abc\" 'zlib) = compress #{616263} 'zlib"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a method the host has not got is refused")
        void anUnknownMethodIsRefused() {
            assertThat(errorIdFrom("compress \"a\" 'invented")).isNotEqualTo("no-error");
            assertThat(errorIdFrom("decompress #{00} 'invented")).isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("data that was never compressed is refused rather than guessed at")
        void rubbishIsRefused() {
            assertThat(errorIdFrom("decompress #{DEADBEEF} 'zlib"))
                    .isNotEqualTo("no-error");
        }
    }

    @Nested
    @DisplayName("SWAP-ENDIAN")
    class ByteOrder {

        @Test
        @DisplayName("two bytes at a time by default")
        void twoAtATime() {
            assertThat(answerTo("swap-endian #{0102}")).isEqualTo("#{0201}");
            assertThat(answerTo("swap-endian #{01020304}")).isEqualTo("#{02010403}");
        }

        @Test
        @DisplayName("/WIDTH takes four or eight")
        void widthChangesTheGroup() {
            assertThat(answerTo("swap-endian/width #{01020304} 4")).isEqualTo("#{04030201}");
            assertThat(answerTo("swap-endian/width #{0102030405060708} 8"))
                    .isEqualTo("#{0807060504030201}");
        }

        @Test
        @DisplayName("a width that is not 2, 4 or 8 is refused")
        void anOddWidthIsRefused() {
            assertThat(errorIdFrom("swap-endian/width #{010203} 3"))
                    .isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("it changes the binary it was given, and answers it")
        void itChangesInPlace() {
            assertThat(answerTo("b: #{0102} swap-endian b b")).isEqualTo("#{0201}");
        }

        @Test
        @DisplayName("a tail that does not fill a group is left as it is")
        void aShortTailIsLeftAlone() {
            assertThat(answerTo("swap-endian/width #{010203} 4")).isEqualTo("#{010203}");
        }
    }
}

package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemCataloguesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

    private static final String FALSE = "#(false)";

    @Nested
    @DisplayName("the catalogues of what the interpreter carries")
    class TheFunctionCatalogues {

        @Test
        @DisplayName("ACTIONS names the sixty actions.reb declares")
        void theActionsAreNamed() {
            assertThat(answerTo("block? system/catalog/actions")).isEqualTo(TRUE);
            assertThat(answerTo("60 = length? system/catalog/actions")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("beginning with the arithmetic ones, in the order they are declared")
        void theActionsAreInDeclarationOrder() {
            assertThat(answerTo("mold copy/part system/catalog/actions 5"))
                    .isEqualTo("\"[add subtract multiply divide remainder]\"");
        }

        @Test
        @DisplayName("and holding the ones a script actually meets")
        void theFamiliarActionsAreThere() {
            assertThat(answerTo("""
                    empty? remove-each a [append insert find copy sort read write] [
                        true? find system/catalog/actions a
                    ]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("NATIVES names what this build carries that is not an action")
        void theNativesAreNamed() {
            assertThat(answerTo("block? system/catalog/natives")).isEqualTo(TRUE);
            assertThat(answerTo("not empty? system/catalog/natives")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the two lists do not overlap, because the split is the point")
        void theTwoListsDoNotOverlap() {
            assertThat(answerTo("""
                    empty? remove-each n copy system/catalog/natives [
                        none? find system/catalog/actions n
                    ]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a catalogued action reaches its function once it is bound")
        void everyActionIsReachableOnceBound() {
            assertThat(answerTo("""
                    empty? remove-each n copy system/catalog/actions [
                        value? bind n system/contexts/lib
                    ]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and so does every native but the one the boot takes away")
        void everyNativeButTheOneTheBootRemoves() {
            assertThat(answerTo("""
                    (mold remove-each n copy system/catalog/natives [
                        value? bind n system/contexts/lib
                    ]) = {[limit-usage]}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("which is catalogued all the same, because the build does carry it")
        void limitUsageIsStillCatalogued() {
            assertThat(answerTo("true? find system/catalog/natives 'limit-usage"))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("the catalogues that describe a port")
    class ThePortCatalogues {

        @Test
        @DisplayName("BOOT-FLAGS names what a flag may be, not what was passed")
        void theBootFlagsAreNamed() {
            assertThat(answerTo("block? system/catalog/boot-flags")).isEqualTo(TRUE);
            assertThat(answerTo("true? find system/catalog/boot-flags 'quiet"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("true? find system/catalog/boot-flags 'secure"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("CHECKSUMS names the twenty methods in the order Rebol names them")
        void theChecksumsAreNamedInRebolsOrder() {
            assertThat(answerTo("""
                    system/catalog/checksums = [
                        adler32 crc24 crc32
                        md4 md5 ripemd160
                        sha1 sha224 sha256 sha384 sha512
                        sha3-224 sha3-256 sha3-384 sha3-512
                        xxh3 xxh32 xxh64 xxh128
                        tcp
                    ]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and each answers a digest or a number, the way the C splits them")
        void eachAnswersADigestOrANumber() {
            assertThat(answerTo("""
                    digests: copy [] numbers: copy []
                    foreach method system/catalog/checksums [
                        unless method = 'tcp [
                            answer: checksum #{00} method
                            append either binary? answer [digests] [numbers] method
                        ]
                    ]
                    reduce [numbers 16 = length? digests]"""))
                    .isEqualTo("[[adler32 crc24 crc32] #(true)]");
        }

        @Test
        @DisplayName("CIPHERS names what the cipher port serves, which is now all of them")
        void theCiphersAreTheOnesTheCipherPortServes() {
            assertThat(answerTo("block? system/catalog/ciphers")).isEqualTo(TRUE);
            assertThat(answerTo("42 = length? system/catalog/ciphers")).isEqualTo(TRUE);
            for (String served : new String[] {
                    "aes-128-cbc", "aes-128-ccm", "aes-128-gcm", "chacha20",
                    "camellia-128-ecb", "camellia-256-gcm", "chacha20-poly1305",
                    "aria-128-ecb", "aria-192-ccm", "aria-256-gcm"}) {
                assertThat(answerTo("true? find system/catalog/ciphers '" + served))
                        .as(served).isEqualTo(TRUE);
            }
            assertThat(answerTo("true? find system/catalog/ciphers 'nonsense-1"))
                    .isEqualTo(FALSE);
        }

        @Test
        @DisplayName("and FILTERS names the fifteen RESIZE can be asked for")
        void theFiltersAreTheOnesResizeCanBeAskedFor() {
            assertThat(answerTo("block? system/catalog/filters")).isEqualTo(TRUE);
            assertThat(answerTo("15 = length? system/catalog/filters")).isEqualTo(TRUE);
            for (String filter : new String[] {
                    "Point", "Box", "Triangle", "Hermite", "Hanning", "Hamming",
                    "Blackman", "Gaussian", "Quadratic", "Cubic", "Catrom",
                    "Mitchell", "Lanczos", "Bessel", "Sinc"}) {
                assertThat(answerTo("true? find system/catalog/filters '" + filter))
                        .as(filter).isEqualTo(TRUE);
            }
            assertThat(answerTo("true? find system/catalog/filters 'nonsense"))
                    .isEqualTo(FALSE);
        }

        @Test
        @DisplayName("but reading either is a block, not a path failure")
        void readingThemDoesNotFail() {
            assertThat(answerTo("""
                    e: try [system/catalog/ciphers] not error? e""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    e: try [system/catalog/filters] not error? e""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("the locale, the console and the root context")
    class TheRest {

        @Test
        @DisplayName("the days begin at Monday, as sysobj.reb writes them")
        void theDaysAreNamed() {
            assertThat(answerTo("7 = length? system/locale/days")).isEqualTo(TRUE);
            assertThat(answerTo("first system/locale/days")).isEqualTo("\"Monday\"");
            assertThat(answerTo("last system/locale/days")).isEqualTo("\"Sunday\"");
        }

        @Test
        @DisplayName("and the months at January")
        void theMonthsAreNamed() {
            assertThat(answerTo("12 = length? system/locale/months")).isEqualTo(TRUE);
            assertThat(answerTo("first system/locale/months")).isEqualTo("\"January\"");
            assertThat(answerTo("last system/locale/months")).isEqualTo("\"December\"");
        }

        @Test
        @DisplayName("the console carries the line being edited and the ones before it")
        void theConsoleHasItsTwoFields() {
            assertThat(answerTo("true? find words-of system/console 'current"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("true? find words-of system/console 'history"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("both empty until a console adapter fills them")
        void theConsoleStartsEmpty() {
            assertThat(answerTo("none? system/console/current")).isEqualTo(TRUE);
            assertThat(answerTo("empty? system/console/history")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the root context is declared and none, as a real 3.22.1 has it")
        void theRootContextIsThere() {
            assertThat(answerTo("true? find words-of system/contexts 'root"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("none? system/contexts/root")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("SYSTEM/SCHEMES holds schemes and nothing else")
    class TheSchemes {

        @Test
        @DisplayName("no field of a scheme has leaked in beside the schemes")
        void noSchemeFieldsLeakedIn() {
            assertThat(answerTo("""
                    empty? remove-each w [title name spec init find] [
                        none? find words-of system/schemes w
                    ]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and everything in it is an object with a scheme's shape")
        void everythingInItIsAScheme() {
            assertThat(answerTo("""
                    empty? remove-each w copy words-of system/schemes [
                        all [
                            object? s: select system/schemes w
                            true? find words-of s 'name
                        ]
                    ]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the console scheme is still there, so this did not empty it")
        void theSchemesAreStillThere() {
            assertThat(answerTo("true? find words-of system/schemes 'console"))
                    .isEqualTo(TRUE);
        }
    }
}

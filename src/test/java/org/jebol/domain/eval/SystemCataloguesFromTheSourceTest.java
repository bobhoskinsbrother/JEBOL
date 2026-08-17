package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The rest of the SYSTEM object: the catalogues, the locale, the console and
 * the root context.
 *
 * <p>{@code boot/sysobj.reb}. A catalogue says what this interpreter has, so a
 * script can ask rather than guess -- which is what decides their contents. A
 * catalogue naming something this build cannot do is worse than an empty one,
 * because a script reads it precisely so it need not guess, and a list of
 * ciphers nobody can use sends it down a path that fails later and further
 * away.
 *
 * <p>So CIPHERS and FILTERS are empty here where a real 3.22.1 lists
 * forty-two and fifteen. Both describe a port this build has not got: there is
 * no block cipher, and RESIZE samples one way with no choice of filter. The
 * fields exist so a script gets an empty list rather than a path failure, and
 * they grow when the capability does.
 *
 * <p>ACTIONS and NATIVES are the two halves of the function set carried in the
 * host language, and the split is Rebol's declaration rather than a fact about
 * the code here -- JEBOL answers {@code native!} for both where R3 answers
 * {@code action!} for the sixty. {@code actions.reb} is the authority for
 * which is which.
 *
 * <p>Specified in {@code spec/natives.allium} under the catalogues.
 */
class SystemCataloguesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

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
            // Through BIND, because the words in a catalogue are unbound --
            // in a real 3.22.1 as well, where `value? first
            // system/catalog/actions` is also false. A catalogue is a list of
            // names, not of the things they name.
            assertThat(answerTo("""
                    empty? remove-each n copy system/catalog/actions [
                        value? bind n system/contexts/lib
                    ]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and so does every native but the one the boot takes away")
        void everyNativeButTheOneTheBootRemoves() {
            // LIMIT-USAGE is catalogued and unreachable, in a real 3.22.1
            // too. The catalogue says what the build carries; mezz-secure.reb
            // then runs `unset in lib 'limit-usage` and takes the word off
            // the shelf. Both statements are true at once and the catalogue
            // is not the one that changed.
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
        @DisplayName("CIPHERS is an empty block, because there is no block cipher here")
        void thereAreNoCiphersYet() {
            // Empty rather than absent, and empty rather than a list of
            // forty-two things asking for one would not get.
            assertThat(answerTo("block? system/catalog/ciphers")).isEqualTo(TRUE);
            assertThat(answerTo("empty? system/catalog/ciphers")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and FILTERS likewise, because RESIZE samples one way")
        void thereAreNoFiltersYet() {
            assertThat(answerTo("block? system/catalog/filters")).isEqualTo(TRUE);
            assertThat(answerTo("empty? system/catalog/filters")).isEqualTo(TRUE);
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
            // system/schemes/title answered "MIDI": a scheme registration was
            // writing its own spec fields into the schemes object as though
            // each were a scheme. A script walking the schemes to see what it
            // can open found five things it cannot.
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

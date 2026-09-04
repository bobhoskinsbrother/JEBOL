package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FORM of a map, and PROTECT on one.
 *
 * <p>{@code Mold_Map} is one function serving both words, and the branches it
 * takes on {@code molded} are what tell them apart: molding writes the
 * brackets and an indented line before each pair, forming writes neither and
 * puts a bare newline between pairs instead. So an empty map forms as nothing
 * at all, and a map of one pair forms as that pair with no punctuation round
 * it. JEBOL formed a map by molding it.
 *
 * <p>What does not change between them is the pairs themselves:
 * {@code Emit(mold, "V V", val, val+1)} is the same line in both branches, so
 * a key and a value are molded whichever way round the map is being written --
 * a text key keeps the quotes that FORM of that same string would drop.
 *
 * <p>PROTECT on a map set a flag nothing read. Every writing branch of the C's
 * map opens with {@code TRAP_PROTECT}, so a write to a protected map is the
 * error `protected`, and JEBOL let all of them through.
 */
class MapFormAndProtectionFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("failure: try [" + source + "] failure/id");
    }

    @Nested
    @DisplayName("forming one")
    class FormingIt {

        @Test
        @DisplayName("an empty map forms as nothing at all")
        void anEmptyMapFormsAsNothing() {
            assertThat(answerTo("""
                    form make map! []""")).isEqualTo("\"\"");
        }

        @Test
        @DisplayName("one pair is that pair, with no brackets round it")
        void onePairHasNoBrackets() {
            assertThat(answerTo("""
                    form make map! [a 1]""")).isEqualTo("\"a: 1\"");
        }

        @Test
        @DisplayName("and each pair after it goes on a line of its own")
        void eachPairOnItsOwnLine() {
            assertThat(answerTo("""
                    (form make map! [a 1 b 2]) = {a: 1^/b: 2}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("with no line break after the last one")
        void noLineBreakAtTheEnd() {
            assertThat(answerTo("""
                    last form make map! [a 1 b 2]""")).isEqualTo("#\"2\"");
        }

        @Test
        @DisplayName("a key that is not a word keeps its own punctuation")
        void aKeyThatIsNotAWord() {
            assertThat(answerTo("""
                    (form make map! [a 1 "b" 2 <c> 3 9 4 #"d" 5])
                        = {a: 1^/"b" 2^/<c> 3^/9 4^/#"d" 5}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("which is molding, so a text key keeps quotes FORM would drop")
        void aTextKeyKeepsItsQuotes() {
            assertThat(answerTo("""
                    reduce [form make map! ["b" 2] form "b"]"""))
                    .as("the map's key keeps its quotes; forming the same string "
                            + "on its own drops them")
                    .isEqualTo("""
                    [{"b" 2} "b"]""");
        }

        @Test
        @DisplayName("and molding is unchanged: brackets, and a line each under indent")
        void moldingIsUnchanged() {
            assertThat(answerTo("""
                    reduce [
                        mold/flat make map! [a 1 b 2]
                        mold make map! []
                        mold/flat/all make map! [a 1 b 2]
                    ]""")).isEqualTo("""
                    ["#[a: 1 b: 2]" "#[]" "#(map! [a: 1 b: 2])"]""");
        }
    }

    @Nested
    @DisplayName("protecting one")
    class ProtectingIt {

        @Test
        @DisplayName("PROTECTED? says so afterwards, and says no before")
        void protectedSaysSo() {
            assertThat(answerTo("""
                    m: make map! [a 1]
                    before: protected? m
                    protect m
                    reduce [before protected? m]""")).isEqualTo("[#(false) #(true)]");
        }

        @Test
        @DisplayName("a new key cannot be put into one")
        void aNewKeyCannotBePut() {
            assertThat(errorIdFrom("""
                    m: make map! [a 1]
                    protect m
                    put m 'b "foo\"""")).isEqualTo("protected");
        }

        @Test
        @DisplayName("nor can a key it already has be written over")
        void anExistingKeyCannotBeWritten() {
            assertThat(errorIdFrom("""
                    m: make map! [a 1]
                    protect m
                    put m 'a "baz\"""")).isEqualTo("protected");
        }

        @Test
        @DisplayName("nor emptied, nor a key taken out")
        void norEmptiedNorRemoved() {
            assertThat(errorIdFrom("""
                    m: make map! [a 1]
                    protect m
                    clear m""")).isEqualTo("protected");
            assertThat(errorIdFrom("""
                    m: make map! [a 1]
                    protect m
                    remove/key m 'a""")).isEqualTo("protected");
        }

        @Test
        @DisplayName("and UNPROTECT lets it be written again")
        void unprotectLetsItThrough() {
            assertThat(answerTo("""
                    m: make map! [a 1]
                    protect m
                    unprotect m
                    put m 'b 2
                    reduce [protected? m m/b]""")).isEqualTo("[#(false) 2]");
        }

        @Test
        @DisplayName("reading one is never refused")
        void readingIsNeverRefused() {
            assertThat(answerTo("""
                    m: make map! [a 1]
                    protect m
                    reduce [m/a length? m select m 'a]""")).isEqualTo("[1 1 1]");
        }
    }
}

package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code if (ANY_SERIES(value) || IS_MAP(value) || IS_BITSET(value))
 * Protect_Series(value, flags);} puts a bitset under the same lock as a series.
 * The refinements then tell three questions apart: /DEEP on a word reaches what
 * the word holds, /WORDS locks the fields and leaves the object open to new
 * names, /VALUES given a path protects the path's own segments, and /HIDE is
 * refused outright for anything that is not a name --
 * {@code if (GET_FLAG(flags, PROT_HIDE)) Trap0(RE_BAD_REFINES);}.
 */
class ProtectionRefinementsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("a bitset carries protection like a series")
    class TheProtectedBitset {

        @Test
        @DisplayName("APPEND onto a protected set is refused")
        void appendIsRefused() {
            assertThat(answerTo("""
                    letters: charset "abc" protect letters
                    e: try [append letters #"d"] e/id = 'protected""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("INSERT is refused")
        void insertIsRefused() {
            assertThat(answerTo("""
                    letters: charset "abc" protect letters
                    e: try [insert letters #"d"] e/id = 'protected""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("REMOVE is refused")
        void removeIsRefused() {
            assertThat(answerTo("""
                    letters: charset "abc" protect letters
                    e: try [remove/part letters #"a"] e/id = 'protected"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("CLEAR is refused")
        void clearIsRefused() {
            assertThat(answerTo("""
                    letters: charset "abc" protect letters
                    e: try [clear letters] e/id = 'protected""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("POKE is refused")
        void pokeIsRefused() {
            assertThat(answerTo("""
                    letters: charset "abc" protect letters
                    e: try [poke letters #"d" true] e/id = 'protected"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("POKE on an unprotected set writes the bit a character names")
        void pokeSetsABit() {
            assertThat(answerTo("""
                    letters: charset "abc" poke letters #"d" true
                    all [find letters #"d"]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and clears it again when handed a false")
        void pokeClearsABit() {
            assertThat(answerTo("""
                    letters: charset "abc" poke letters #"a" false
                    all [not find letters #"a"]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an integer names the same bit a character would")
        void pokeTakesAnIntegerIndex() {
            assertThat(answerTo("""
                    letters: charset "abc" poke letters 100 true
                    all [find letters #"d"]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a word index is refused for its type")
        void pokeRefusesAWordIndex() {
            assertThat(answerTo("""
                    letters: charset "abc"
                    e: try [poke letters 'd true] e/id = 'invalid-type"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and so is a write through a path")
        void aPathWriteIsRefused() {
            assertThat(answerTo("""
                    letters: charset "abc" protect letters
                    e: try [letters/(#"d"): true] e/id = 'protected""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("FIND still answers, because reading is untouched")
        void findStillAnswers() {
            assertThat(answerTo("""
                    letters: charset "abc" protect letters
                    all [find letters #"a"]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a read through a path still answers as well")
        void aPathReadStillAnswers() {
            assertThat(answerTo("""
                    letters: charset "abc" protect letters
                    letters/(#"a")""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an unprotected set was never refusing anything")
        void theUnprotectedSetIsTheOffPoint() {
            assertThat(answerTo("""
                    letters: charset "abc" append letters #"d"
                    all [find letters #"d"]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and UNPROTECT lets the writes through again")
        void unprotectReleasesTheSet() {
            assertThat(answerTo("""
                    letters: charset "abc" protect letters unprotect letters
                    append letters #"d" all [find letters #"d"]""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("/DEEP on a word reaches what the word holds")
    class DeeplyProtectingAName {

        @Test
        @DisplayName("the series the word holds refuses a change")
        void theHeldSeriesIsLocked() {
            assertThat(answerTo("""
                    held: [1 2] protect/deep 'held
                    e: try [append held 3] e/id = 'protected""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the slot refuses an assignment as well")
        void theSlotIsLockedToo() {
            assertThat(answerTo("""
                    held: [1 2] protect/deep 'held
                    e: try [held: 5] e/id = 'locked-word""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("without /DEEP the slot alone is locked and the series is free")
        void withoutDeepOnlyTheSlotIsLocked() {
            assertThat(answerTo("""
                    held: [1 2] protect 'held
                    append held 3 held = [1 2 3]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a nested series inside a held object is reached")
        void aFieldsSeriesIsReached() {
            assertThat(answerTo("""
                    settings: object [x: [1 2]] protect/deep 'settings
                    e: try [append settings/x 3] e/id = 'protected""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the object's own field refuses an assignment")
        void theFieldIsLocked() {
            assertThat(answerTo("""
                    settings: object [x: [1 2]] protect/deep 'settings
                    e: try [settings/x: 9] e/id = 'locked-word""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("where the shallow form leaves the field writable")
        void theShallowFormLeavesTheFieldWritable() {
            assertThat(answerTo("""
                    settings: object [x: [1 2]] protect 'settings
                    settings/x: 9 settings/x = 9""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("/WORDS locks the fields and leaves the object open")
    class ProtectingTheWordsOnly {

        @Test
        @DisplayName("an existing field refuses reassignment")
        void anExistingFieldIsLocked() {
            assertThat(answerTo("""
                    settings: object [a: 1 b: 2] protect/words settings
                    e: try [settings/a: 9] e/id = 'locked-word""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("while EXTEND still adds a new one")
        void extendStillAddsAField() {
            assertThat(answerTo("""
                    settings: object [a: 1 b: 2] protect/words settings
                    extend settings 'c 3 settings/c = 3""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a full protect closes the object to new names")
        void aFullProtectClosesTheObject() {
            assertThat(answerTo("""
                    settings: object [a: 1] protect settings
                    e: try [extend settings 'c 3] e/id = 'protected""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("/VALUES given a path protects the path's own segments")
    class ProtectingAPathValue {

        @Test
        @DisplayName("the path value refuses a change to its segments")
        void thePathSeriesIsLocked() {
            assertThat(answerTo("""
                    settings: object [b: [1 2]] route: 'settings/b
                    protect/values route
                    e: try [append route 'x] e/id = 'protected""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the field the path leads to is left free to change")
        void theFieldsSeriesIsUntouched() {
            assertThat(answerTo("""
                    settings: object [b: [1 2]] protect/values 'settings/b
                    append settings/b 3 settings/b = [1 2 3]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and free to be reassigned")
        void theFieldIsStillAssignable() {
            assertThat(answerTo("""
                    settings: object [b: [1 2]] protect/values 'settings/b
                    settings/b: 9 settings/b = 9""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("where /WORDS on the same path locks the field it names")
        void wordsOnAPathLocksTheField() {
            assertThat(answerTo("""
                    settings: object [b: [1 2]] protect/words 'settings/b
                    e: try [settings/b: 9] e/id = 'locked-word""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("/HIDE conceals a name and refuses everything else")
    class HidingSomethingThatIsNotAName {

        @Test
        @DisplayName("a binary is refused with bad-refines")
        void aBinaryIsRefused() {
            assertThat(answerTo("""
                    e: try [protect/hide #{01}] e/id = 'bad-refines""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a block is refused")
        void aBlockIsRefused() {
            assertThat(answerTo("""
                    e: try [protect/hide [1 2]] e/id = 'bad-refines""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a string is refused")
        void aStringIsRefused() {
            assertThat(answerTo("""
                    e: try [protect/hide {abc}] e/id = 'bad-refines""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an integer is refused")
        void anIntegerIsRefused() {
            assertThat(answerTo("""
                    e: try [protect/hide 5] e/id = 'bad-refines""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an object is refused, having no name of its own to hide")
        void anObjectIsRefused() {
            assertThat(answerTo("""
                    e: try [protect/hide object [q: 1]] e/id = 'bad-refines"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a word is the thing it is for")
        void aWordIsAccepted() {
            assertThat(answerTo("""
                    concealed: 5 protect/hide 'concealed true""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and so is a field named through IN")
        void aFieldNamedThroughInIsAccepted() {
            assertThat(answerTo("""
                    settings: object [q: 1] protect/hide in settings 'q
                    error? try [settings/q]""")).isEqualTo("#(true)");
        }
    }
}

package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PROTECT/VALUES and PROTECT/WORDS, which take a block of words.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>Complements, and neither does the other's job. Without them,
 * {@code protect/values [a b]} protects the literal block holding the two
 * words and nothing else: accepted, plausible, and leaving everything it
 * was meant to guard wide open.
 */
class ProtectByNameTest {

    private static String errorIdOf(String setup, String attempt) {
        String source = setup + " e: try [" + attempt + "] "
                + "either error? e [e/id] ['no-error]";
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String VALUES = "b: \"ab\" c: \"cd\" protect/values [b c] ";
    private static final String WORDS = "d: \"ef\" protect/words [d] ";

    @Test
    @DisplayName("/values stops the series being changed")
    void valuesProtectsWhatTheWordHolds() {
        assertThat(errorIdOf(VALUES, "append b \"x\"")).isEqualTo("protected");
        assertThat(errorIdOf(VALUES, "clear c")).isEqualTo("protected");
    }

    @Test
    @DisplayName("/values leaves the word free to be reassigned")
    void valuesDoesNotProtectTheWord() {
        assertThat(errorIdOf(VALUES, "b: \"new\"")).isEqualTo("no-error");
    }

    @Test
    @DisplayName("/words stops the word being reassigned")
    void wordsProtectsTheSlot() {
        assertThat(errorIdOf(WORDS, "d: \"new\"")).isEqualTo("locked-word");
    }

    @Test
    @DisplayName("/words leaves the series free to be changed")
    void wordsDoesNotProtectTheValue() {
        assertThat(errorIdOf(WORDS, "append d \"x\"")).isEqualTo("no-error");
    }

    @Test
    @DisplayName("/values reaches every word in the block")
    void everyWordIsCovered() {
        assertThat(errorIdOf(VALUES, "append c \"x\"")).isEqualTo("protected");
    }

    @Test
    @DisplayName("UNPROTECT/VALUES lets the changes through again")
    void unprotectValuesReleasesThem() {
        assertThat(errorIdOf(VALUES + "unprotect/values [b c] ", "append b \"x\""))
                .isEqualTo("no-error");
    }

    @Test
    @DisplayName("a word the block names that holds nothing is passed over")
    void anUnknownWordIsTheDegenerateCase() {
        assertThat(errorIdOf("protect/values [never-set-at-all] ", "1 + 1"))
                .isEqualTo("no-error");
    }

    /** An object holding a number and an object, the shape the suite uses. */
    private static final String NESTED = "o: object [a: 1 o: object [a: 2]] ";

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("PROTECT of a path protects the field the path names")
    void aPathProtectsItsField() {
        assertThat(errorIdOf(NESTED + "protect 'o/a ", "o/a: 9")).isEqualTo("locked-word");
    }

    @Test
    @DisplayName("PROTECT of a path leaves the fields around it free")
    void aPathLeavesItsNeighboursAlone() {
        // The off point. A path read as a list of names would take both
        // ends of it, so this is what tells the two apart.
        assertThat(errorIdOf(NESTED + "protect 'o/a ", "o/o/a: 9")).isEqualTo("no-error");
    }

    @Test
    @DisplayName("PROTECT of a path leaves the enclosing word assignable")
    void aPathDoesNotProtectTheWordItStartsFrom() {
        // The failure that cost six assertions in the suite, and nowhere
        // near where it was noticed: every later `o: something` was
        // refused, so the tests after it ran against a stale object.
        assertThat(errorIdOf(NESTED + "protect/words/deep 'o/o ", "o: 5"))
                .isEqualTo("no-error");
    }

    @Test
    @DisplayName("PROTECT/WORDS/DEEP of a path reaches the field and its contents")
    void aPathReachesInsideWhenAskedDeeply() {
        String protectedNested = NESTED + "protect/words/deep 'o/o ";
        assertThat(answerTo(protectedNested + "protected? 'o/o")).isEqualTo("#(true)");
        assertThat(answerTo(protectedNested + "protected? 'o/o/a")).isEqualTo("#(true)");
        assertThat(answerTo(protectedNested + "protected? 'o/a")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("a path that names nothing changes nothing and raises nothing")
    void aPathThatNamesNothingIsIgnored() {
        // Both degenerate shapes: a field that is not there, and a walk
        // through a number, which cannot be walked into at all.
        assertThat(errorIdOf(NESTED, "protect 'o/missing")).isEqualTo("no-error");
        assertThat(errorIdOf(NESTED, "protect 'o/a/deeper")).isEqualTo("no-error");
    }
}

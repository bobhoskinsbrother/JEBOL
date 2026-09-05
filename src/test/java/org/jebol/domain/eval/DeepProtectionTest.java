package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * /DEEP reaches everything inside, including an object inside an object.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>Stopping at the outer object leaves free exactly the case a caller
 * reached for /DEEP to cover, and it does so quietly -- the shallow guard
 * still refuses the shallow write, so the refinement looks like it works.
 */
class DeepProtectionTest {

    private static String errorIdOf(String setup, String attempt) {
        String source = setup + " e: try [" + attempt + "] "
                + "either error? e [e/id] ['no-error]";
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String NESTED =
            "o: make object! [b: make object! [c: 3]] ignore: protect/deep o ";

    @Test
    @DisplayName("a nested object's field is protected too")
    void deepReachesANestedObject() {
        assertThat(errorIdOf(NESTED, "o/b/c: 4")).isEqualTo("locked-word");
    }

    @Test
    @DisplayName("the outer object is still protected as well")
    void theOuterObjectIsStillGuarded() {
        assertThat(errorIdOf(NESTED, "o/b: 9")).isEqualTo("locked-word");
    }

    @Test
    @DisplayName("without /deep only the outer object is protected")
    void shallowProtectionStopsAtTheOuterObject() {
        assertThat(errorIdOf("o: make object! [b: make object! [c: 3]] ignore: protect o ",
                "o/b/c: 4")).isEqualTo("no-error");
    }

    @Test
    @DisplayName("a series inside an object is reached too")
    void deepReachesASeriesField() {
        assertThat(errorIdOf("o: make object! [b: \"abc\"] ignore: protect/deep o ",
                "insert o/b \"x\"")).isEqualTo("protected");
    }

    @Test
    @DisplayName("/words without /deep guards only the name")
    void wordsAloneGuardsOnlyTheName() {
        String setup = "a: make object! [b: \"abc\"] ignore: protect/words [a] ";
        assertThat(errorIdOf(setup, "a: 9")).isEqualTo("locked-word");
        assertThat(errorIdOf(setup, "insert a/b \"x\"")).isEqualTo("no-error");
    }

    private static String allowedAfter(String unprotecting) {
        String setup = "o: " + unprotecting
                + " protect/deep o: make object! [a: 10 b: [20]] ";
        return errorIdOf(setup, "append o 'd") + " "
                + errorIdOf(setup, "o/a: 0") + " "
                + errorIdOf(setup, "append o/b 0");
    }

    @Test
    @DisplayName("the four UNPROTECT shapes release three different things")
    void theRefinementsSeparateTheThreeThings() {
        assertThat(allowedAfter("unprotect"))
                .as("frees the object and its words, not the values")
                .isEqualTo("no-error no-error protected");
        assertThat(allowedAfter("unprotect/words"))
                .as("frees the words only")
                .isEqualTo("protected no-error protected");
        assertThat(allowedAfter("unprotect/deep"))
                .as("frees all three")
                .isEqualTo("no-error no-error no-error");
        assertThat(allowedAfter("unprotect/words/deep"))
                .as("frees the words and the values, not the object")
                .isEqualTo("protected no-error no-error");
    }

    @Test
    @DisplayName("UNPROTECT/DEEP releases the whole of it again")
    void unprotectingDeeplyReleasesEverything() {
        assertThat(errorIdOf(NESTED + "ignore: unprotect/deep o ", "o/b/c: 4"))
                .isEqualTo("no-error");
    }

    /**
     * A block given to /WORDS may hold paths, not only words.
     *
     * <p>{@code natives.reb} spells the refinement out: {@code /words "Process
     * list as words (and path words)"}. The parenthetical is the whole of it,
     * and skipping a path in the block is silent -- the call answers the block
     * it was given either way, so nothing says the request was ignored.
     *
     * <p>Rebol's own library depends on it. {@code protect-system} in
     * {@code mezz-secure.reb} protects every word of SYSTEM and then hands back
     * the few a script must be able to write, with
     * {@code unprotect/words [system/script]}. When that did nothing here,
     * {@code sys/do*} could not record the script it was about to run, and so
     * DO of any file raised {@code locked-word} instead of running it.
     */
    private static final String NAMED_BY_PATH =
            "o: make object! [a: 10] ignore: protect/words [o/a] ";

    @Test
    @DisplayName("PROTECT/WORDS takes a path in the block, not only a word")
    void protectWordsTakesAPath() {
        assertThat(errorIdOf(NAMED_BY_PATH, "o/a: 11")).isEqualTo("locked-word");
    }

    @Test
    @DisplayName("and UNPROTECT/WORDS hands the same field back")
    void unprotectWordsTakesAPathToo() {
        assertThat(errorIdOf(NAMED_BY_PATH + "ignore: unprotect/words [o/a] ", "o/a: 11"))
                .isEqualTo("no-error");
    }

    @Test
    @DisplayName("naming a field by path leaves the word holding the object alone")
    void thepathNamesTheFieldRatherThanTheHolder() {
        assertThat(errorIdOf(NAMED_BY_PATH, "o: 12"))
                .as("protecting o/a is not protecting o")
                .isEqualTo("no-error");
    }

    @Test
    @DisplayName("a path that names nothing changes nothing and raises nothing")
    void apathThatNamesNothing() {
        assertThat(errorIdOf("o: make object! [a: 10] ",
                "ignore: protect/words [o/never-there]"))
                .isEqualTo("no-error");
    }

    @Test
    @DisplayName("words and paths mix in one block")
    void wordsAndPathsMixInOneBlock() {
        String both = "o: make object! [a: 10] w: 1 ignore: protect/words [w o/a] ";
        assertThat(errorIdOf(both, "w: 2")).isEqualTo("locked-word");
        assertThat(errorIdOf(both, "o/a: 11")).isEqualTo("locked-word");
    }

    /**
     * /DEEP reaches what the slot holds, named by a word or by a path alike.
     *
     * <p>Both checked against a real 3.22.5. They look like the same request
     * written two ways and they are, which is worth pinning: the path form
     * behaved differently here for as long as paths were skipped altogether.
     */
    @Test
    @DisplayName("/deep through a word reaches the value it holds")
    void deepThroughAWordReachesTheValue() {
        String held = "q: make object! [b: [20]] ignore: protect/words/deep [q] ";
        assertThat(errorIdOf(held, "append q/b 0")).isEqualTo("protected");
    }

    @Test
    @DisplayName("and so does /deep through a path")
    void deepThroughApathReachesItToo() {
        String held = "h: make object! [c: make object! [b: [20]]] "
                + "ignore: protect/words/deep [h/c] ";
        assertThat(errorIdOf(held, "h/c: 5")).isEqualTo("locked-word");
        assertThat(errorIdOf(held, "append h/c/b 0")).isEqualTo("protected");
    }
}

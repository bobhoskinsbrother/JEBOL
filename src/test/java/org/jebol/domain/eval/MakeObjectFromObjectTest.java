package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Conclusion;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAKE given a prototype and a second object merges the two.
 *
 * <p>Specified in {@code spec/natives.allium} and confirmed against a real
 * R3. Until now the second argument was cast to a block, so passing an
 * object threw a ClassCastException straight out of JEBOL -- which
 * {@code spec/embed.allium} promises cannot happen.
 *
 * <p>The interesting boundary is not the field values but the methods: one
 * that still closed over the object it was written in would read the wrong
 * values and, worse, write into the prototype when called on the copy.
 */
class MakeObjectFromObjectTest {

    private static final String TWO_OBJECTS =
            "o1: make object! [a: 1 f: does [a]] o2: make object! [a: 2] o3: make o1 o2 ";

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        assertThat(outcome.conclusion())
                .as("%s must not escape as a host exception", source)
                .isEqualTo(Conclusion.PRODUCED_A_VALUE);
        return interpreter.display(outcome);
    }

    @Test
    @DisplayName("the result holds the prototype's fields")
    void theResultKeepsThePrototypesFields() {
        assertThat(answerTo(TWO_OBJECTS + "mold words-of o3")).isEqualTo("\"[a f]\"");
    }

    @Test
    @DisplayName("the second object's values win where they overlap")
    void theSecondObjectOverwritesSharedFields() {
        assertThat(answerTo(TWO_OBJECTS + "o3/a")).isEqualTo("2");
    }

    @Test
    @DisplayName("a method from the prototype reads the merged values")
    void methodsAreReboundToTheResult() {
        assertThat(answerTo(TWO_OBJECTS + "o3/f")).isEqualTo("2");
    }

    @Test
    @DisplayName("the prototype is left alone")
    void thePrototypeIsUntouched() {
        assertThat(answerTo(TWO_OBJECTS + "o1/a")).isEqualTo("1");
    }

    @Test
    @DisplayName("fields only the second object has are added")
    void extraFieldsFromTheSecondObjectArrive() {
        String source = "a: make object! [x: 1 show: does [x]] "
                + "b: make object! [x: 2 show: does [reduce [x y]] y: 3] "
                + "c: make a b ";
        assertThat(answerTo(source + "mold words-of c")).isEqualTo("\"[x show y]\"");
    }

    @Test
    @DisplayName("a method that needs a field only the second object brought still works")
    void aMethodCanReachAFieldTheOtherObjectAdded() {
        String source = "a: make object! [x: 1 show: does [x]] "
                + "b: make object! [x: 2 show: does [reduce [x y]] y: 3] "
                + "c: make a b ";
        assertThat(answerTo(source + "mold c/show")).isEqualTo("\"[2 3]\"");
    }

    @Test
    @DisplayName("merging an empty object changes nothing but the identity")
    void mergingAnEmptyObjectIsTheDegenerateCase() {
        String source = "base: make object! [a: 1] empty-one: make object! [] "
                + "joined: make base empty-one ";
        assertThat(answerTo(source + "mold words-of joined")).isEqualTo("\"[a]\"");
        assertThat(answerTo(source + "joined/a")).isEqualTo("1");
        assertThat(answerTo(source + "same? base joined")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("making from an empty prototype takes everything from the other object")
    void anEmptyPrototypeIsTheOtherDegenerateCase() {
        String source = "empty-one: make object! [] other: make object! [a: 1] "
                + "joined: make empty-one other ";
        assertThat(answerTo(source + "mold words-of joined")).isEqualTo("\"[a]\"");
    }

    @Test
    @DisplayName("SAME? asks whether two objects are one object, not whether they match")
    void sameAsksAboutIdentityAndEqualAsksAboutFields() {
        String source = "left: make object! [a: 1] right: make object! [a: 1] ";

        assertThat(answerTo(source + "equal? left right"))
                .as("matching fields make two objects equal")
                .isEqualTo("#(true)");
        assertThat(answerTo(source + "same? left right"))
                .as("but they are still two objects, which is all SAME? asks")
                .isEqualTo("#(false)");
        assertThat(answerTo(source + "same? left left"))
                .as("an object is itself")
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a block second argument still works, which is the ordinary case")
    void aBlockBodyStillWorks() {
        assertThat(answerTo("o: make object! [a: 1] p: make o [b: 2] mold words-of p"))
                .as("the object case must not have displaced the block case")
                .isEqualTo("\"[a b]\"");
    }
}

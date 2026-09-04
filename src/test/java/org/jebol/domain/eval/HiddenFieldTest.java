package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PROTECT/HIDE conceals a field rather than locking it.
 *
 * <p>Specified in {@code spec/natives.allium} and measured against a real
 * R3 3.22.1.
 *
 * <p>The object stops listing it, molding it and answering for it, and a
 * path to it fails as though there were no such field. Code written inside
 * the object still reaches it, which is the whole point: it is how an
 * object keeps something to itself. JEBOL treated /HIDE as ordinary
 * protection, so the field stayed visible and merely refused assignment.
 */
class HiddenFieldTest {

    /** An object with one field hidden and a function that still uses it. */
    private static final String CONCEALED =
            "o: object [f: 1 g: 2 test: does [f]] protect/hide in o 'f ";

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /** The id of the error a snippet raises, or "no-error" if it raises none. */
    private static String errorIdOf(String setup, String attempt) {
        return answerTo(setup + "e: try [" + attempt + "] "
                + "either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("a path to a hidden field fails as though there were no such field")
    void aHiddenFieldCannotBeReached() {
        assertThat(errorIdOf(CONCEALED, "o/f")).isEqualTo("invalid-path");
    }

    @Test
    @DisplayName("the object's own code still reaches it")
    void theObjectKeepsUsingIt() {
        assertThat(answerTo(CONCEALED + "o/test")).isEqualTo("1");
    }

    @Test
    @DisplayName("the fields around it are unaffected")
    void nothingElseIsConcealed() {
        assertThat(answerTo(CONCEALED + "o/g")).isEqualTo("2");
    }

    @Test
    @DisplayName("it is left out of WORDS-OF and BODY-OF")
    void itIsNotListed() {
        assertThat(answerTo(CONCEALED + "(words-of o) = [g test]")).isEqualTo("#(true)");
        assertThat(answerTo(CONCEALED + "find body-of o to set-word! 'f"))
                .as("nor is it in the body")
                .isEqualTo("_");
    }

    @Test
    @DisplayName("it is left out of the molded form")
    void itIsNotMolded() {
        assertThat(answerTo(CONCEALED + "none? find mold o \"f:\"")).isEqualTo("#(true)");
        assertThat(answerTo(CONCEALED + "true? find mold o \"g:\""))
                .as("and the visible one still is")
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("adding a field over a hidden one is refused")
    void nothingOutsideMayWriteOverIt() {
        assertThat(errorIdOf(CONCEALED, "extend o 'f 2")).isEqualTo("hidden");
        assertThat(errorIdOf(CONCEALED, "append o [f: 2]")).isEqualTo("hidden");
        assertThat(errorIdOf(CONCEALED, "put o to-set-word 'f 2")).isEqualTo("hidden");
    }

    @Test
    @DisplayName("adding an ordinary field is still allowed")
    void theObjectIsNotOtherwiseClosed() {
        assertThat(errorIdOf(CONCEALED, "extend o 'h 3")).isEqualTo("no-error");
    }

    @Test
    @DisplayName("an object with nothing hidden is unaffected")
    void theOrdinaryObjectIsUntouched() {
        assertThat(answerTo("o: object [f: 1] o/f")).isEqualTo("1");
        assertThat(answerTo("o: object [f: 1] (words-of o) = [f]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("two objects hiding a field are equal whatever they hide")
    void hidingIsNotComparable() {
        assertThat(answerTo(
                "equal? context [a: 1 protect/hide 'a] context [a: 2 protect/hide 'a]"))
                .isEqualTo("#(true)");
        assertThat(answerTo(
                "equal? context [a: 1 protect/hide 'a] context [b: 1 protect/hide 'b]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("hiding a field is still not the same as not having one")
    void aHiddenFieldIsStillThere() {
        assertThat(answerTo("equal? context [a: 1 protect/hide 'a] context [a: 1]"))
                .isEqualTo("#(false)");
        assertThat(answerTo("equal? context [a: 1 protect/hide 'a] context []"))
                .isEqualTo("#(false)");
    }
}

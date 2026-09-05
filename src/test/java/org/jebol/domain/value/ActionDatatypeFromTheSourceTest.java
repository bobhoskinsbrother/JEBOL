package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An action says it is an action, where a native says it is a native.
 *
 * <p>Both are written in the host language, so nothing about the
 * implementation tells them apart, and JEBOL answered {@code native!} for all
 * of them. Which is which is Rebol's declaration and not derivable:
 * {@code src/boot/actions.reb} names the sixty and {@code src/boot/natives.reb}
 * names the rest.
 *
 * <p>An action is the polymorphic kind -- one name with an arm per datatype --
 * and {@code Do_Act} dispatches on the value's type where a native has one body
 * for every caller. The distinction is visible to a script through
 * {@code type?}, {@code action?} and {@code native?}, and nothing else here
 * gives it away.
 *
 * <p>Nothing in Rebol's own suite catches this: {@code action?} appears zero
 * times across all sixty-seven vendored files. It was found by asking two
 * running interpreters the same question, which is what
 * {@code scripts/runtime-parity.py} does, and it was 120 of the 582 words
 * Rebol's library holds -- more than sixty, because a second spelling bound to
 * the same function is an action too.
 */
class ActionDatatypeFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";
    private static final String FALSE = "#(false)";

    @Test
    @DisplayName("APPEND is an action, and says so three ways")
    void appendIsAnAction() {
        assertThat(answerTo("type? :append")).isEqualTo("#(action!)");
        assertThat(answerTo("action? :append")).isEqualTo(TRUE);
        assertThat(answerTo("native? :append")).isEqualTo(FALSE);
    }

    @Test
    @DisplayName("PRINT is a native, and still says so")
    void printIsStillANative() {
        assertThat(answerTo("type? :print")).isEqualTo("#(native!)");
        assertThat(answerTo("action? :print")).isEqualTo(FALSE);
        assertThat(answerTo("native? :print")).isEqualTo(TRUE);
    }

    @Test
    @DisplayName("and an action is a function, so nothing that took one stops")
    void anactionIsStillAFunction() {
        assertThat(answerTo("any-function? :append")).isEqualTo(TRUE);
        assertThat(answerTo("function? :append"))
                .as("FUNCTION? is the narrow question and answers false, as in R3")
                .isEqualTo(FALSE);
    }

    @Test
    @DisplayName("a second spelling of the same function is an action too")
    void asecondSpellingIsAnActionToo() {
        assertThat(answerTo("type? :abs"))
                .as("ABS and ABSOLUTE are one function under two names")
                .isEqualTo("#(action!)");
    }

    @Test
    @DisplayName("every name actions.reb declares answers action!")
    void everyDeclaredActionAnswersSo() {
        assertThat(answerTo("""
                wrong: copy []
                foreach name system/catalog/actions [
                    if all [value? name not action? get name] [append wrong name]
                ]
                wrong"""))
                .as("system/catalog/actions is read from actions.reb, so this is "
                        + "the declaration checked against itself")
                .isEqualTo("[]");
    }

    @Test
    @DisplayName("and no native answers action!")
    void nonativeAnswersAction() {
        assertThat(answerTo("""
                wrong: copy []
                foreach name system/catalog/natives [
                    if all [value? name action? get name] [append wrong name]
                ]
                wrong""")).isEqualTo("[]");
    }

    @Test
    @DisplayName("the datatype was already named, and now has values")
    void thedatatypeWasAlreadyNamed() {
        assertThat(answerTo("datatype? action!")).isEqualTo(TRUE);
        assertThat(answerTo("action! = type? :append")).isEqualTo(TRUE);
    }

    /**
     * A type-test is an action too, and that half is a rule rather than a list.
     *
     * <p>{@code types.reb} generates one per datatype, so the datatypes are
     * already enumerated and listing the predicates again would be a second
     * place to keep in step. The line falls exactly at the datatypes: a
     * predicate over a *typeset* is a borrowed REBOL function, which is why
     * {@code series?} answers {@code function!} in a real R3 and must go on
     * doing so here.
     */
    @Test
    @DisplayName("a datatype's own test is an action")
    void adatatypesTestIsAnAction() {
        assertThat(answerTo("type? :block?")).isEqualTo("#(action!)");
        assertThat(answerTo("type? :integer?")).isEqualTo("#(action!)");
        assertThat(answerTo("type? :action?"))
                .as("the predicate for the datatype this test is about")
                .isEqualTo("#(action!)");
    }

    @Test
    @DisplayName("but a typeset's test is not, and neither is every word ending in a question mark")
    void atypesetsTestIsNotAnAction() {
        assertThat(answerTo("type? :series?"))
                .as("SERIES? is over a typeset and is borrowed REBOL")
                .isEqualTo("#(function!)");
        assertThat(answerTo("type? :any-block?")).isEqualTo("#(function!)");
        assertThat(answerTo("type? :true?"))
                .as("TRUE? ends in a question mark and tests nothing")
                .isEqualTo("#(native!)");
    }

    @Test
    @DisplayName("every datatype in the catalogue has a test, and it is an action")
    void everyDatatypeTestIsAnAction() {
        assertThat(answerTo("""
                wrong: copy []
                foreach kind system/catalog/datatypes [
                    asking: to word! head change back tail to string! kind #"?"
                    if all [value? asking not action? get asking] [append wrong asking]
                ]
                wrong"""))
                .as("run through ./r3-head, which answers [] as well: there is no "
                        + "datatype whose own test is not an action")
                .isEqualTo("[]");
    }
}

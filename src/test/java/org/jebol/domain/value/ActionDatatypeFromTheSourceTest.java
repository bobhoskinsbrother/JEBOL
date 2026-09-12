package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

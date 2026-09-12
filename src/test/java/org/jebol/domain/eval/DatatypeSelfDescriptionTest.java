package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatatypeSelfDescriptionTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("SPEC gives an object holding a title and a type")
    void specGivesBoth() {
        assertThat(answerTo("mold words-of reflect integer! 'spec"))
                .isEqualTo("\"[title type]\"");
    }

    @Test
    @DisplayName("TITLE gives the wording a script can compare against")
    void titleGivesTheWording() {
        assertThat(answerTo("reflect integer! 'title")).isEqualTo("\"64 bit integer\"");
    }

    @Test
    @DisplayName("TYPE gives the family the datatype belongs to")
    void typeGivesTheFamily() {
        assertThat(answerTo("mold reflect integer! 'type")).isEqualTo("\"scalar\"");
    }

    @Test
    @DisplayName("the spec object's fields hold the same two answers")
    void theSpecFieldsAgree() {
        assertThat(answerTo("sp: reflect integer! 'spec sp/type = 'scalar"))
                .isEqualTo("#(true)");
        assertThat(answerTo(
                "sp: reflect integer! 'spec sp/title = reflect integer! 'title"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the families other than scalar are named too")
    void theOtherFamiliesAreThere() {
        assertThat(answerTo("mold reduce [reflect string! 'type reflect block! 'type "
                + "reflect word! 'type reflect object! 'type reflect native! 'type]"))
                .isEqualTo("\"[string block word object function]\"");
    }

    @Test
    @DisplayName("a datatype JEBOL has not built still describes itself")
    void anUnbuiltDatatypeStillDescribesItself() {
        assertThat(answerTo("reflect gob! 'title")).isEqualTo("\"graphical object\"");
    }

    @Test
    @DisplayName("a field a datatype does not describe answers none")
    void anUnknownFieldAnswersNone() {
        assertThat(answerTo("mold reflect integer! 'nonsense")).isEqualTo("\"_\"");
    }
}

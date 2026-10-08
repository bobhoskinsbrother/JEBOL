package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TheCatalogueDeclaresEveryTypesetTest {

    private String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return Molder.form(interpreter.run(source).value());
    }

    @Test
    @DisplayName("any-string! holds the six string datatypes, ref! among them, in the catalogue's order")
    void anyString() {
        assertThat(answerTo("mold any-string!"))
                .isEqualTo("make typeset! [string! file! email! ref! url! tag!]");
    }

    @Test
    @DisplayName("any-block! holds hash! as well as the blocks and the paths")
    void anyBlock() {
        assertThat(answerTo("mold any-block!"))
                .isEqualTo("make typeset! [block! paren! path! set-path! get-path! lit-path! hash!]");
    }

    @Test
    @DisplayName("any-function! holds all seven function datatypes")
    void anyFunction() {
        assertThat(answerTo("mold any-function!"))
                .isEqualTo("make typeset! [native! action! rebcode! command! op! closure! function!]");
    }

    @Test
    @DisplayName("any-object! holds the five object datatypes")
    void anyObject() {
        assertThat(answerTo("mold any-object!"))
                .isEqualTo("make typeset! [object! module! error! task! port!]");
    }

    @Test
    @DisplayName("series! holds every string and block datatype, binary!, image! and vector!")
    void series() {
        assertThat(answerTo("mold series!"))
                .isEqualTo("make typeset! [binary! string! file! email! ref! url! tag! image! vector!"
                        + " block! paren! path! set-path! get-path! lit-path! hash!]");
    }

    @Test
    @DisplayName("number! holds integer!, decimal! and percent!")
    void number() {
        assertThat(answerTo("mold number!"))
                .isEqualTo("make typeset! [integer! decimal! percent!]");
    }

    @Test
    @DisplayName("scalar! holds the numbers, money!, char!, pair!, tuple!, time! and date!")
    void scalar() {
        assertThat(answerTo("mold scalar!"))
                .isEqualTo("make typeset! [integer! decimal! percent! money! char! pair! tuple! time! date!]");
    }

    @Test
    @DisplayName("a datatype is a value of datatype!")
    void aDatatypeIsAValue() {
        assertThat(answerTo("mold type? integer!")).isEqualTo("#(datatype!)");
    }
}

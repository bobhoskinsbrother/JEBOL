package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAKE ERROR!, which builds the error its spec asks for.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>Two shapes. A block names a type and an id from the catalogue; a
 * string raises something of the script's own, which needs no catalogue
 * entry at all.
 */
class MakeErrorTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a block spec gives an error of that type and id")
    void aBlockSpecIsHonoured() {
        // Compared as words rather than as text. A real R3 keeps whichever
        // spelling the caller wrote -- `Math` for one it raised itself and
        // `math` for one built from a spec saying 'math -- and word
        // equality ignores case, so nothing depends on which.
        assertThat(answerTo(
                "e: make error! [type: 'math id: 'positive] "
                        + "mold reduce [e/type = 'math e/id = 'positive]"))
                .isEqualTo("\"[#(true) #(true)]\"");
    }

    @Test
    @DisplayName("what it builds really is an error")
    void whatItBuildsIsAnError() {
        assertThat(answerTo("error? make error! [type: 'math id: 'positive]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a code in the spec is ignored")
    void aCodeInTheSpecIsIgnored() {
        // The code follows from the type. Letting a caller set it would
        // let an error claim a category its code contradicts.
        assertThat(answerTo(
                "e: make error! [code: 500 type: 'math id: 'overflow] e/type = 'math"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a string spec gives a User error carrying the string")
    void aStringSpecGivesAUserError() {
        assertThat(answerTo(
                "e: make error! \"message\" reduce [e/type e/id e/arg1]"))
                .isEqualTo("[User message \"message\"]");
    }

    @Test
    @DisplayName("an empty block raises, and what is caught is invalid-error")
    void anEmptyBlockRaises() {
        // This test asserted that the call ANSWERS an error, which was
        // wrong: the C's Find_Error_Info reaches Trap0(RE_INVALID_ERROR)
        // for a spec that names no type or no id, so the call raises and
        // only a TRY holds the internal invalid-error. error-test.r3
        // asserts the raised shape twice.
        assertThat(answerTo("""
                e: try [make error! []] reduce [e/type e/id]"""))
                .isEqualTo("[Internal invalid-error]");
    }
}

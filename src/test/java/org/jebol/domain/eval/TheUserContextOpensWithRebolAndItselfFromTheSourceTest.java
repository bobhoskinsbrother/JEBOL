package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TheUserContextOpensWithRebolAndItselfFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("REBOL and LIB-LOCAL are the first two words in it")
    void rebolAndLibLocalAreTheFirstTwoWordsInIt() {
        assertThat(answerTo("copy/part words-of system/contexts/user 2"))
                .isEqualTo("[REBOL lib-local]");
    }

    @Test
    @DisplayName("REBOL holds the system object")
    void rebolHoldsTheSystemObject() {
        assertThat(answerTo("same? system system/contexts/user/REBOL"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("LIB-LOCAL holds the user context itself")
    void libLocalHoldsTheUserContextItself() {
        assertThat(answerTo("same? system/contexts/user system/contexts/user/lib-local"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("so a script asking for LIB-LOCAL outside a module gets that context")
    void aScriptAskingForLibLocalOutsideAModuleGetsThatContext() {
        assertThat(answerTo("same? system/contexts/user lib-local"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and inside a module it gets the module's own, which is a different one")
    void insideAModuleItGetsTheModulesOwn() {
        assertThat(answerTo("""
                m: module [] [export mine: does [lib-local]]
                reduce [
                    object? m/lib-local
                    empty? m/lib-local
                    same? system/contexts/user m/lib-local
                ]""")).isEqualTo("[#(true) #(true) #(false)]");
    }
}

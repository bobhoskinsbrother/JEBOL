package org.jebol.suite;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnicodeUtilsModuleLoadsTest {

    private static String answerTo(String source) {
        Interpreter interpreter = SuiteHost.installOn(
                Interpreter.withBounds(SuiteHost.grantEverything()));
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the module whose bracket upstream never closes is repaired here and imports")
    void loadsTheRepairedModule() {
        assertThat(answerTo("""
                module? import 'unicode-utils""")).isEqualTo("#(true)");
        assertThat(answerTo("""
                import 'unicode-utils
                value? 'decode-utf8""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and its decoder turns UTF-8 octets back into characters")
    void decodes() {
        assertThat(answerTo("""
                import 'unicode-utils
                (decode-utf8 #{41 42 43}) = {ABC}""")).isEqualTo("#(true)");
        assertThat(answerTo("""
                import 'unicode-utils
                (first (decode-utf8 #{C3 A9})) = to char! 233""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a character above the basic plane is dropped, or an entity when asked")
    void aboveTheBasicPlane() {
        assertThat(answerTo("""
                import 'unicode-utils
                (decode-utf8 #{F0 9F 98 80}) = {}""")).isEqualTo("#(true)");
        assertThat(answerTo("""
                import 'unicode-utils
                (decode-utf8/html #{F0 9F 98 80}) = {&#128512;}""")).isEqualTo("#(true)");
    }
}
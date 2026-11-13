package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnExportNeverDefinedFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("importing a module that exports a word its body never defines says nothing")
    void importingItSaysNothing() {
        assertThat(answerTo("""
                import (module [name: undef-test exports: [never-made made]] [made: 1])
                made""")).isEqualTo("1");
    }

    @Test
    @DisplayName("and the word reaches the library unset")
    void theWordReachesTheLibraryUnset() {
        assertThat(answerTo("""
                import (module [name: undef-test exports: [never-made made]] [made: 1])
                reduce [value? 'never-made  mold :lib/never-made]""")).isEqualTo("""
                [#(false) "#(unset)"]""");
    }
}

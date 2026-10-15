package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

class AnAliasHoldsTheOriginalNamesFunctionTest {

    @ParameterizedTest(name = "once the library has run, :{0} is the very function :{1} is")
    @CsvSource({"abs, absolute", "context, object", "true?, did"})
    void theLibraryMakesTheAliasTheSameFunction(String alias, String originalName) {
        Interpreter interpreter = Interpreter.create();
        assertThat(interpreter.display(interpreter.run("same? :" + alias + " :" + originalName))).isEqualTo("#(true)");
    }

    @ParameterizedTest(name = "type? :{0} is {1}, as in r3")
    @DisplayName("each alias is the kind of function its original name is")
    @CsvSource({"abs, #(action!)", "context, #(native!)", "true?, #(native!)"})
    void theAliasIsTheSameKind(String alias, String kind) {
        Interpreter interpreter = Interpreter.create();
        assertThat(interpreter.display(interpreter.run("type? :" + alias))).isEqualTo(kind);
    }
}

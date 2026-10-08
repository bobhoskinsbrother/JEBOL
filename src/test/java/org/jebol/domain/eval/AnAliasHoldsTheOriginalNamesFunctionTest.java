package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.jebol.domain.value.Context;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

class AnAliasHoldsTheOriginalNamesFunctionTest {

    @ParameterizedTest(name = "{0} holds the very function {1} holds, before the library runs")
    @CsvSource({"abs, absolute", "context, object", "true?, did"})
    void theAliasIsThereFromTheStart(String alias, String originalName) {
        Context natives = RebolNativeWords.standard().asContext();
        assertThat(natives.valueAt(alias)).isSameAs(natives.valueAt(originalName));
    }

    @ParameterizedTest(name = "type? :{0} is {1}, as in r3")
    @DisplayName("each alias is the kind of function its original name is")
    @CsvSource({"abs, #(action!)", "context, #(native!)", "true?, #(native!)"})
    void theAliasIsTheSameKind(String alias, String kind) {
        Interpreter interpreter = Interpreter.create();
        assertThat(interpreter.display(interpreter.run("type? :" + alias))).isEqualTo(kind);
    }
}

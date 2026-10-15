package org.jebol.suite;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThruCacheModuleLoadsTest {

    private static String answerTo(String source) {
        Interpreter interpreter = SuiteHost.installOn(
                Interpreter.withBounds(SuiteHost.grantingEverything()));
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the application data directory has a modules directory beside it")
    void theModulesDirectoryIsSettled() {
        assertThat(answerTo("type? system/options/modules")).isEqualTo("#(file!)");
    }

    @Test
    @DisplayName("IMPORT of a bundled module answers a module")
    void importAnswersAModule() {
        assertThat(answerTo("""
                module? import 'thru-cache""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("importing thru-cache brings READ-THRU into the library")
    void importBringsReadThruIn() {
        assertThat(answerTo("""
                import 'thru-cache value? 'read-thru""")).isEqualTo("#(true)");
    }

}

package org.jebol.extension.sqlite;

import org.jebol.adapter.host.ExtensionDescriptors;
import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.ExtensionReport;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TheSqliteDescriptorFromTheSourceTest {

    private static Interpreter interpreterFromTheDescriptor() {
        Bounds bounds = new ExtensionDescriptors(TheSqliteDescriptorFromTheSourceTest.class.getClassLoader())
                .addJvmSetupsTo(Bounds.standard());
        return AnInterpreter.withBounds(bounds);
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the jar's descriptor names its jvm provider, and the extension it provides installs")
    void installsFromTheDescriptor() {
        assertThat(interpreterFromTheDescriptor().extensions())
                .containsExactly(new ExtensionReport("sqlite", Optional.empty()));
    }

    @Test
    @DisplayName("system/modules holds it until IMPORT, as R3 keeps a delayed extension")
    void waitsForImport() {
        assertThat(answerTo(interpreterFromTheDescriptor(), "block? select system/modules 'sqlite"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("its module carries Rebol-SQLite 3.46.0.0's header")
    void carriesTheHeader() {
        assertThat(answerTo(interpreterFromTheDescriptor(), """
                s: spec-of import 'sqlite
                reduce [s/version s/title s/name s/type s/needs s/author s/license s/url s/exports]"""))
                .isEqualTo("""
                        [3.46.0.0 "Rebol SQLite Extension" sqlite module 3.13.1 Oldes MIT https://github.com/Siskin-framework/Rebol-SQLite []]""");
    }
}

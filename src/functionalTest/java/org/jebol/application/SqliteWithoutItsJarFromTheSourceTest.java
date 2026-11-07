package org.jebol.application;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteWithoutItsJarFromTheSourceTest {

    @TempDir
    Path directory;

    private String answerTo(String source) {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withFiles(FileSystemPort.rootFileSystem(directory)),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.FILES));
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("system/modules names no sqlite, because JEBOL never fetches the address R3 keeps there")
    void namesNoSqlite() {
        assertThat(answerTo("select system/modules 'sqlite")).isEqualTo("_");
    }

    @Test
    @DisplayName("import 'sqlite fails as a module that cannot be found")
    void failsToImport() {
        assertThat(answerTo("""
                system/options/modules: %/
                e: try [import 'sqlite]
                reduce [e/type e/id e/arg1 e/arg2]""")).isEqualTo("""
                [access cannot-open sqlite "module not found"]""");
    }

    @Test
    @DisplayName("and the sqlite: scheme is unknown")
    void hasNoScheme() {
        assertThat(answerTo("none? select system/schemes 'sqlite")).isEqualTo("#(true)");
    }
}

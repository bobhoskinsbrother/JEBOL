package org.jebol.application;

import org.jebol.domain.eval.EnvironmentPort;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChangingDirectoryWritesPwdFromTheSourceTest {

    private static final class AShellThatExported implements EnvironmentPort {

        private final Map<String, String> names = new LinkedHashMap<>();

        private AShellThatExported(String name, String value) {
            names.put(name, value);
        }

        @Override
        public String valueOf(String name) {
            return names.get(name);
        }

        @Override
        public Map<String, String> all() {
            return Map.copyOf(names);
        }

        @Override
        public void nameHolds(String name, String value) {
            if (value == null) {
                names.remove(name);
            } else {
                names.put(name, value);
            }
        }
    }

    private static String answerTo(Path root, EnvironmentPort environment, String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.FILES)
                        .granting(HostService.WORKING_DIRECTORY)
                        .granting(HostService.ENVIRONMENT));
        interpreter.useFileSystem(FileSystemPort.rootedAt(root));
        interpreter.useEnvironment(environment);
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("moving writes PWD, so the two agree afterwards")
    void movingWritesPwd(@TempDir Path root) {
        assertThat(answerTo(root, new AShellThatExported("PWD", "/nowhere/at/all"), """
                make-dir %inner/
                change-dir %inner/
                reduce [pwd = to-rebol-file get-env "PWD"  pwd]"""))
                .isEqualTo("[#(true) %/inner/]");
    }

    @Test
    @DisplayName("and again after moving back up")
    void andAgainAfterMovingBackUp(@TempDir Path root) {
        assertThat(answerTo(root, new AShellThatExported("PWD", "/nowhere/at/all"), """
                make-dir %inner/
                change-dir %inner/
                change-dir %../
                reduce [pwd = to-rebol-file get-env "PWD"  pwd]"""))
                .isEqualTo("[#(true) %/]");
    }

    @Test
    @DisplayName("and what the shell exported is overwritten, not preferred")
    void whatTheShellExportedIsOverwritten(@TempDir Path root) {
        assertThat(answerTo(root, new AShellThatExported("PWD", "/nowhere/at/all"), """
                make-dir %inner/
                change-dir %inner/
                get-env "PWD\"""")).isEqualTo("\"/inner/\"");
    }

    @Test
    @DisplayName("but every other name is left alone")
    void everyOtherNameIsLeftAlone(@TempDir Path root) {
        assertThat(answerTo(root, new AShellThatExported("EDITOR", "vi"), """
                make-dir %inner/
                change-dir %inner/
                get-env "EDITOR\"""")).isEqualTo("\"vi\"");
    }

    @Test
    @DisplayName("CHANGE-DIR answers the new directory, not the path it was given")
    void changeDirAnswersTheNewDirectory(@TempDir Path root) {
        assertThat(answerTo(root, new AShellThatExported("PWD", "/nowhere"), """
                make-dir %inner/
                change-dir %inner""")).isEqualTo("%/inner/");
        assertThat(answerTo(root, new AShellThatExported("PWD", "/nowhere"), """
                what-dir = change-dir %.""")).isEqualTo("#(true)");
        assertThat(answerTo(root, new AShellThatExported("PWD", "/nowhere"), """
                make-dir %inner/
                change-dir %inner/
                reduce [change-dir %../  what-dir]""")).isEqualTo("[%/ %/]");
    }

    @Test
    @DisplayName("and a move that failed names the absolute path it meant")
    void aMoveThatFailedNamesTheAbsolutePath(@TempDir Path root) {
        assertThat(answerTo(root, new AShellThatExported("PWD", "/nowhere"), """
                make-dir %inner/
                change-dir %inner/
                e: try [change-dir %issues/2446]
                reduce [e/id  e/arg1  e/arg1 = join what-dir %issues/2446/]"""))
                .isEqualTo("[cannot-open %/inner/issues/2446/ #(true)]");
    }

    @Test
    @DisplayName("and a host that granted no environment still moves")
    void aHostThatGrantedNoEnvironmentStillMoves(@TempDir Path root) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.FILES)
                        .granting(HostService.WORKING_DIRECTORY));
        interpreter.useFileSystem(FileSystemPort.rootedAt(root));
        String source = """
                make-dir %inner/
                change-dir %inner/
                pwd""";
        interpreter.defineFreshWordsIn(source);
        assertThat(interpreter.display(interpreter.run(source))).isEqualTo("%/inner/");
    }

    @Test
    @DisplayName("and a move that was refused writes nothing")
    void aMoveThatWasRefusedWritesNothing(@TempDir Path root) {
        assertThat(answerTo(root, new AShellThatExported("PWD", "/nowhere/at/all"), """
                try [change-dir %no-such-dir/]
                reduce [pwd  get-env "PWD"]"""))
                .isEqualTo("[%/ \"/nowhere/at/all\"]");
    }
}

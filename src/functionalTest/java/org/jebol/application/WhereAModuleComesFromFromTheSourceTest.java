package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WhereAModuleComesFromFromTheSourceTest {

    private static Interpreter makeAnInterpreterWithAFilesystemAt(Path root) {
        return Interpreter.createOn(
                Interpreter.standardPorts().withFiles(FileSystemPort.rootFileSystem(root)),
                OutputPort.discard(),
                Bounds.standard()
                        .grant(HostService.FILES)
                        .grant(HostService.WORKING_DIRECTORY));
    }

    private static String answerTo(Path root, String source) {
        Interpreter interpreter = makeAnInterpreterWithAFilesystemAt(root);
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String answerWithNoFilesystem(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the modules directory sits beside the data, and is made")
    void putsTheModulesDirectoryBesideTheData(@TempDir Path root) {
        Interpreter interpreter = makeAnInterpreterWithAFilesystemAt(root);
        String makingIt = """
                system/options/data: %/data/
                make-dir/deep system/options/data""";
        interpreter.defineNewWordsIn(makingIt);
        interpreter.run(makingIt);
        interpreter.followApplicationDataDirectory();

        assertThat(interpreter.display(interpreter.run("""
                reduce [
                    system/options/modules = join system/options/data %modules/
                    true? exists? system/options/modules
                ]"""))).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("and with no filesystem, or no data directory, there is none")
    void hasNoneWithoutAFilesystemOrADataDirectory(@TempDir Path root) {
        assertThat(answerWithNoFilesystem("none? system/options/modules"))
                .isEqualTo("#(true)");
        assertThat(answerTo(root, "none? system/options/modules"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and it follows the data directory when a host moves that")
    void followsTheDataDirectory(@TempDir Path root) {
        Interpreter interpreter = makeAnInterpreterWithAFilesystemAt(root);
        String moving = """
                system/options/data: %/elsewhere/
                make-dir/deep system/options/data""";
        interpreter.defineNewWordsIn(moving);
        interpreter.run(moving);
        interpreter.followApplicationDataDirectory();

        assertThat(interpreter.display(interpreter.run(
                "system/options/modules"))).isEqualTo("%/elsewhere/modules/");
    }

    @Test
    @DisplayName("the module table starts as the modules this build bundles")
    void startsTheModuleTableWithWhatIsBundled(@TempDir Path root) {
        assertThat(answerTo(root, "select system/modules 'thru-cache"))
                .isEqualTo("bundled://thru-cache.reb");
        assertThat(answerTo(root, "select system/modules 'httpd"))
                .isEqualTo("bundled://httpd.reb");
    }

    @Test
    @DisplayName("but nothing this build cannot serve is offered")
    void offersNothingThisBuildCannotServe(@TempDir Path root) {
        assertThat(answerTo(root, "select system/modules 'sqlite")).isEqualTo("_");
        assertThat(answerTo(root, "select system/modules 'blend2d")).isEqualTo("_");
        assertThat(answerTo(root, "error? try [import 'brotli]")).isEqualTo("#(true)");
        assertThat(answerTo(root, "type? select system/modules 'json"))
                .as("already vendored and loaded, so it is a module and not an address")
                .isEqualTo("#(module!)");
    }

    @Test
    @DisplayName("a name that is not on it answers none, and so does WINDOW")
    void answersNoneForANameNotOnIt(@TempDir Path root) {
        assertThat(answerTo(root, "select system/modules 'no-such-module-at-all"))
                .isEqualTo("_");
        assertThat(answerTo(root, "select system/modules 'window")).isEqualTo("_");
    }

    @Test
    @DisplayName("and a module this build loaded is a module there, not an address")
    void listsAModuleThisBuildLoaded(@TempDir Path root) {
        assertThat(answerTo(root, "type? select system/modules 'json"))
                .isEqualTo("#(module!)");
    }
}

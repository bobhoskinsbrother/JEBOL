package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WhereAModuleComesFromFromTheSourceTest {

    private static Interpreter withAFilesystemAt(Path root) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.FILES)
                        .granting(HostService.WORKING_DIRECTORY));
        interpreter.useFileSystem(FileSystemPort.rootedAt(root));
        return interpreter;
    }

    private static String answerTo(Path root, String source) {
        Interpreter interpreter = withAFilesystemAt(root);
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String answerWithNoFilesystem(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the modules directory sits beside the data, and is made")
    void theModulesDirectorySitsBesideTheData(@TempDir Path root) {
        Interpreter interpreter = withAFilesystemAt(root);
        String makingIt = """
                system/options/data: %/data/
                make-dir/deep system/options/data""";
        interpreter.defineFreshWordsIn(makingIt);
        interpreter.run(makingIt);
        interpreter.followTheApplicationDataDirectory();

        assertThat(interpreter.display(interpreter.run("""
                reduce [
                    system/options/modules = join system/options/data %modules/
                    true? exists? system/options/modules
                ]"""))).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("and with no filesystem, or no data directory, there is none")
    void withNoFilesystemOrNoDataDirectoryThereIsNone(@TempDir Path root) {
        assertThat(answerWithNoFilesystem("none? system/options/modules"))
                .isEqualTo("#(true)");
        assertThat(answerTo(root, "none? system/options/modules"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and it follows the data directory when a host moves that")
    void itFollowsTheDataDirectory(@TempDir Path root) {
        Interpreter interpreter = withAFilesystemAt(root);
        String moving = """
                system/options/data: %/elsewhere/
                make-dir/deep system/options/data""";
        interpreter.defineFreshWordsIn(moving);
        interpreter.run(moving);
        interpreter.followTheApplicationDataDirectory();

        assertThat(interpreter.display(interpreter.run(
                "system/options/modules"))).isEqualTo("%/elsewhere/modules/");
    }

    @Test
    @DisplayName("the module table starts as the modules this build bundles")
    void theModuleTableStartsAsWhatIsBundled(@TempDir Path root) {
        assertThat(answerTo(root, "select system/modules 'thru-cache"))
                .isEqualTo("bundled://thru-cache.reb");
        assertThat(answerTo(root, "select system/modules 'httpd"))
                .isEqualTo("bundled://httpd.reb");
    }

    @Test
    @DisplayName("but nothing this build cannot serve is offered")
    void nothingThisBuildCannotServeIsOffered(@TempDir Path root) {
        assertThat(answerTo(root, "select system/modules 'sqlite")).isEqualTo("_");
        assertThat(answerTo(root, "select system/modules 'blend2d")).isEqualTo("_");
        assertThat(answerTo(root, "error? try [import 'brotli]")).isEqualTo("#(true)");
        assertThat(answerTo(root, "type? select system/modules 'json"))
                .as("already vendored and loaded, so it is a module and not an address")
                .isEqualTo("#(module!)");
    }

    @Test
    @DisplayName("a name that is not on it answers none, and so does WINDOW")
    void aNameThatIsNotOnItAnswersNone(@TempDir Path root) {
        assertThat(answerTo(root, "select system/modules 'no-such-module-at-all"))
                .isEqualTo("_");
        assertThat(answerTo(root, "select system/modules 'window")).isEqualTo("_");
    }

    @Test
    @DisplayName("and a module this build loaded is a module there, not an address")
    void aModuleThisBuildLoadedIsAModuleThere(@TempDir Path root) {
        assertThat(answerTo(root, "type? select system/modules 'json"))
                .isEqualTo("#(module!)");
    }
}

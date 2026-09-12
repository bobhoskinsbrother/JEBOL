package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two places IMPORT looks that were not there: a directory and a table.
 *
 * <p>{@code system/options/modules} names where an imported module is kept,
 * and {@code sys-start.reb} writes it in one line -- {@code modules: attempt
 * [make-dir/deep join data %modules/]}. The ATTEMPT is the whole of the failure
 * handling: a host that will not let the directory be made leaves the field
 * none, and IMPORT has nowhere to look rather than a path it cannot use.
 *
 * <p>{@code system/modules} is where IMPORT looks last, and {@code sysobj.reb}
 * builds it with forty-odd entries rather than empty -- each a module's name
 * and the url it is fetched from. IMPORT walks the three in order: what is
 * already loaded, a file in the modules directory, and then {@code select
 * system/modules source}, which it downloads and saves. Starting the table
 * empty makes the third step unreachable, and the third is the only one that
 * can find a module nobody has installed.
 *
 * <p>Both were none here, so {@code import 'thru-cache} answered "module not
 * found" with nowhere having been looked.
 */
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

    /**
     * A host that has made its data directory gets a modules directory beside
     * it, made. A host that has not gets none -- the line above this one in
     * {@code sys-start.reb} makes the data directory and JEBOL does not,
     * because the path it defaults to is the operator's own and a confined
     * filesystem reads that path as somewhere else entirely.
     */
    @Test
    @DisplayName("the modules directory sits beside the data, and is made")
    void theModulesDirectorySitsBesideTheData(@TempDir Path root) {
        Interpreter interpreter = withAFilesystemAt(root);
        String makingIt = """
                system/options/data: %/data/
                make-dir/deep system/options/data""";
        interpreter.defineFreshWordsIn(makingIt);
        interpreter.run(makingIt);
        interpreter.putTheModulesDirectoryBesideTheData();

        assertThat(interpreter.display(interpreter.run("""
                reduce [
                    system/options/modules = join system/options/data %modules/
                    true? exists? system/options/modules
                ]"""))).isEqualTo("[#(true) #(true)]");
    }

    /**
     * No filesystem, or no data directory made in it, and there is none.
     * ATTEMPT leaves the field none rather than a path nothing can be written
     * to, which is what IMPORT reads to decide it has nowhere to look.
     */
    @Test
    @DisplayName("and with no filesystem, or no data directory, there is none")
    void withNoFilesystemOrNoDataDirectoryThereIsNone(@TempDir Path root) {
        assertThat(answerWithNoFilesystem("none? system/options/modules"))
                .isEqualTo("#(true)");
        assertThat(answerTo(root, "none? system/options/modules"))
                .isEqualTo("#(true)");
    }

    /**
     * Moving the data directory moves the modules with it. A confined
     * filesystem has to move it -- the operator's own hidden folder is outside
     * the sandbox -- and a modules directory left behind is one IMPORT is not
     * allowed to write to.
     */
    @Test
    @DisplayName("and it follows the data directory when a host moves that")
    void itFollowsTheDataDirectory(@TempDir Path root) {
        Interpreter interpreter = withAFilesystemAt(root);
        String moving = """
                system/options/data: %/elsewhere/
                make-dir/deep system/options/data""";
        interpreter.defineFreshWordsIn(moving);
        interpreter.run(moving);
        interpreter.putTheModulesDirectoryBesideTheData();

        assertThat(interpreter.display(interpreter.run(
                "system/options/modules"))).isEqualTo("%/elsewhere/modules/");
    }

    /**
     * The names are Rebol's and the addresses are not: each names the BUNDLED
     * scheme, so the module is read out of this build rather than fetched from
     * {@code src.rebol.tech} and evaluated as it arrives.
     */
    @Test
    @DisplayName("the module table starts as the modules this build bundles")
    void theModuleTableStartsAsWhatIsBundled(@TempDir Path root) {
        assertThat(answerTo(root, "select system/modules 'thru-cache"))
                .isEqualTo("bundled://thru-cache.reb");
        assertThat(answerTo(root, "select system/modules 'httpd"))
                .isEqualTo("bundled://httpd.reb");
    }

    /**
     * Nothing this build cannot serve is offered. A compiled extension is a
     * shared library and nothing here can load one; a module this build already
     * vendors and loads needs no address at all, and a live address for code
     * that is in the jar is one load-order change away from being reachable.
     */
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

    /**
     * A module that loads takes the place of its address in the same object --
     * {@code repend system/modules [name module]} is the last thing LOAD-MODULE
     * does -- so the table is both the list of what may be fetched and the
     * record of what has been. Which is why it is put down before the library
     * loads rather than after.
     */
    @Test
    @DisplayName("and a module this build loaded is a module there, not an address")
    void aModuleThisBuildLoadedIsAModuleThere(@TempDir Path root) {
        assertThat(answerTo(root, "type? select system/modules 'json"))
                .isEqualTo("#(module!)");
    }
}

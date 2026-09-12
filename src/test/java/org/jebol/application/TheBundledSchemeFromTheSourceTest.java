package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reading a module out of the build instead of off the wire.
 *
 * <p>IMPORT's last resort is {@code select system/modules name}, and what
 * DOWNLOAD-EXTENSION does with what it finds is {@code content: read source}
 * followed by {@code write file content} -- an ordinary READ of whatever the
 * url names. So a scheme served from the build turns the fetch into a read of
 * the build, and neither IMPORT nor DOWNLOAD-EXTENSION has to know the
 * difference.
 *
 * <p>Which is the point rather than an optimisation. Rebol's table sends IMPORT
 * to {@code src.rebol.tech} and evaluates what comes back, with no signature,
 * no checksum and no pinned version between the wire and the evaluator -- so a
 * proxy in between, or an upstream that changes, is evaluated as it arrives. A
 * module bundled with the build has been looked at once, by whoever put it
 * there, and cannot change under a running system.
 */
class TheBundledSchemeFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String answerWithAFilesystem(Path root, String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.FILES)
                        .granting(HostService.WORKING_DIRECTORY));
        interpreter.useFileSystem(FileSystemPort.rootedAt(root));
        String makingIt = """
                system/options/data: %/data/
                make-dir/deep system/options/data""";
        interpreter.defineFreshWordsIn(makingIt);
        interpreter.run(makingIt);
        interpreter.putTheModulesDirectoryBesideTheData();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a bundled module reads as its own source")
    void aBundledModuleReadsAsItsOwnSource() {
        assertThat(answerTo("""
                reduce [
                    binary? read bundled://thru-cache.reb
                    true? find to string! read bundled://thru-cache.reb "path-thru:"
                ]""")).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("and /STRING reads it as text, like any other read")
    void andStringReadsItAsText() {
        assertThat(answerTo("""
                true? find read/string bundled://soundex.reb {soundex}"""))
                .isEqualTo("#(true)");
    }

    /**
     * Refused the way a missing file is, rather than answering nothing. A
     * caller that cannot tell "no such module" from "an empty module" writes
     * the second to disk.
     */
    @Test
    @DisplayName("a name nothing is bundled as is refused, not answered empty")
    void aNameNothingIsBundledAsIsRefused() {
        assertThat(answerTo("""
                e: try [read bundled://no-such-module.reb] e/id""")).isEqualTo("cannot-open");
        assertThat(answerTo("""
                e: try [read bundled://] e/id""")).isEqualTo("cannot-open");
    }

    /**
     * No service asked for, because nothing is reached. The bytes are in the
     * build already, and this interpreter was granted neither a filesystem nor
     * a network.
     */
    @Test
    @DisplayName("and it asks for no service, having nothing to reach")
    void itAsksForNoService() {
        assertThat(answerTo("binary? read bundled://upgrade.reb")).isEqualTo("#(true)");
    }

    /**
     * A path with a directory in it is refused. The scheme names one flat set
     * of modules and nothing about it is a filesystem, so a name that tries to
     * climb out of it is a name it does not carry.
     */
    @Test
    @DisplayName("and a name that tries to climb out of it is refused")
    void aNameThatTriesToClimbOutIsRefused() {
        assertThat(answerTo("""
                e: try [read bundled://../mezz/prot-http.reb] e/id"""))
                .isEqualTo("cannot-open");
        assertThat(answerTo("""
                e: try [read bundled://sub/thru-cache.reb] e/id"""))
                .isEqualTo("cannot-open");
    }

    @Test
    @DisplayName("the module table names the scheme, not an address on the wire")
    void theModuleTableNamesTheScheme() {
        assertThat(answerTo("select system/modules 'thru-cache"))
                .isEqualTo("bundled://thru-cache.reb");
        assertThat(answerTo("select system/modules 'httpd"))
                .isEqualTo("bundled://httpd.reb");
        assertThat(answerTo("""
                collect [
                    foreach name words-of system/modules [
                        if all [
                            url? address: select system/modules name
                            not find address "bundled://"
                        ][keep name]
                    ]
                ]""")).isEqualTo("[]");
    }

    /**
     * The whole of it, end to end: IMPORT finds the address, reads it out of
     * the build, writes it into the modules directory and loads it. Which is
     * exactly what it does for an address on the wire, minus the wire.
     */
    @Test
    @DisplayName("so IMPORT finds a module without reaching anything")
    void soImportFindsAModuleWithoutReachingAnything(@TempDir Path root) {
        assertThat(answerWithAFilesystem(root, """
                reduce [
                    module? import 'soundex
                    true? exists? join system/options/modules %soundex.reb
                ]""")).isEqualTo("[#(true) #(true)]");
    }
}

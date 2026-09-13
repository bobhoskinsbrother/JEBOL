package org.jebol.application;

import org.jebol.adapter.host.JavaProcesses;
import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class BootProcessFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = interpreterWithAHost();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static Interpreter interpreterWithAHost() {
        Bounds bounds = Bounds.standard();
        for (HostService service : HostService.values()) {
            bounds = bounds.granting(service);
        }
        Interpreter interpreter = Interpreter.withBounds(bounds);
        try {
            interpreter.useFileSystem(FileSystemPort.rootedAt(
                    Files.createTempDirectory("jebol-boot")));
        } catch (IOException noDirectory) {
            throw new UncheckedIOException(noDirectory);
        }
        interpreter.useEnvironment(new ProcessEnvironment());
        interpreter.useProcesses(new JavaProcesses());
        return interpreter;
    }

    @Test
    @DisplayName("the boot option names a file a script can hand to the shell")
    void theBootOptionIsAFile() {
        assertThat(answerTo("""
                file? system/options/boot""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a child asked to quit leaves with a status of zero")
    void aChildThatQuitsLeavesWithZero() {
        assertThat(answerTo("""
                0 = call/shell/wait append to-local-file system/options/boot
                    { --do "quit"}""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and one asked to quit with a number leaves with that number")
    void aChildThatQuitsWithANumberReportsIt() {
        assertThat(answerTo("""
                100 = call/shell/wait append to-local-file system/options/boot
                    { --do "quit/return 100"}""")).isEqualTo("#(true)");
    }

    private static Interpreter interpreterWithTheDataDirectoryMoved() {
        Interpreter interpreter = interpreterWithAHost();
        String moving = """
                system/options/data: %/data/
                make-dir/deep system/options/data""";
        interpreter.defineFreshWordsIn(moving);
        interpreter.run(moving);
        interpreter.followTheApplicationDataDirectory();
        return interpreter;
    }

    private static final String AGREED = "agreed";

    private static String doesTheChildSay(
            Interpreter interpreter, String childScript, String expected) {

        String asking = """
                write %%child.r3 {%s}
                heard: copy ""
                call/wait/shell/output reform [
                    to-local-file system/options/boot %%child.r3] heard
                either {%s} = heard ['%s] [heard]"""
                .formatted(childScript, expected, AGREED);
        interpreter.defineFreshWordsIn(asking);
        return interpreter.display(interpreter.run(asking));
    }

    @Test
    @DisplayName("a child is told where its parent keeps application data")
    void aChildIsToldWhereItsParentKeepsApplicationData() {
        assertThat(doesTheChildSay(interpreterWithTheDataDirectoryMoved(), """
                Rebol []
                probe system/options/data
                probe system/options/modules""",
                "%/data/^/%/data/modules/^/")).isEqualTo(AGREED);
    }

    @Test
    @DisplayName("so it imports a module its parent installed a moment earlier")
    void itImportsAModuleItsParentInstalledAMomentEarlier() {
        Interpreter interpreter = interpreterWithTheDataDirectoryMoved();
        String installing = """
                write system/options/modules/whisper.reb
                    {Rebol [name: whisper type: module] export whispered: 42}""";
        interpreter.defineFreshWordsIn(installing);
        interpreter.run(installing);

        assertThat(doesTheChildSay(interpreter, """
                Rebol []
                import 'whisper
                probe whispered""", "42^/")).isEqualTo(AGREED);
    }

    @Test
    @DisplayName("and where the host moved nothing, neither of them has a modules directory")
    void whereTheHostMovedNothingNeitherHasAModulesDirectory() {
        Interpreter interpreter = interpreterWithAHost();
        String asking = "none? system/options/modules";
        interpreter.defineFreshWordsIn(asking);

        assertThat(interpreter.display(interpreter.run(asking))).isEqualTo("#(true)");
        assertThat(doesTheChildSay(interpreter, """
                Rebol []
                probe none? system/options/modules""", "#(true)^/")).isEqualTo(AGREED);
    }
}

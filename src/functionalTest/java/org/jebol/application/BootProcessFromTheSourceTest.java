package org.jebol.application;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.adapter.host.JavaProcess;
import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class BootProcessFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = makeAnInterpreterWithAHost();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static Interpreter makeAnInterpreterWithAHost() {
        Bounds bounds = Bounds.standard();
        for (HostService service : HostService.values()) {
            bounds = bounds.grant(service);
        }
        try {
            return Interpreter.createOn(
                    AnInterpreter.standardPorts()
                            .withFiles(FileSystemPort.rootFileSystem(
                                    Files.createTempDirectory("jebol-boot")))
                            .withEnvironment(new ProcessEnvironment())
                            .withProcesses(new JavaProcess()),
                    OutputPort.discard(),
                    bounds);
        } catch (IOException noDirectory) {
            throw new UncheckedIOException(noDirectory);
        }
    }

    @Test
    @DisplayName("the boot option names a file a script can hand to the shell")
    void takesAFileAsTheBootOption() {
        assertThat(answerTo("""
                file? system/options/boot""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and the child it starts is told to open no window, as a script run this way has none")
    void tellsTheChildToOpenNoWindow() {
        assertThat(answerTo("""
                not none? find read system/options/boot {-Djava.awt.headless=true}"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a child asked to quit leaves with a status of zero")
    void leavesWithZeroWhenAChildQuits() {
        assertThat(answerTo("""
                0 = call/shell/wait append to-local-file system/options/boot
                    { --do "quit"}""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and one asked to quit with a number leaves with that number")
    void reportsTheNumberAChildQuitsWith() {
        assertThat(answerTo("""
                100 = call/shell/wait append to-local-file system/options/boot
                    { --do "quit/return 100"}""")).isEqualTo("#(true)");
    }

    private static Interpreter makeAnInterpreterWithTheDataDirectoryMoved() {
        Interpreter interpreter = makeAnInterpreterWithAHost();
        String moving = """
                system/options/data: %/data/
                make-dir/deep system/options/data""";
        interpreter.defineNewWordsIn(moving);
        interpreter.run(moving);
        interpreter.followApplicationDataDirectory();
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
        interpreter.defineNewWordsIn(asking);
        return interpreter.display(interpreter.run(asking));
    }

    @Test
    @DisplayName("a child is told where its parent keeps application data")
    void tellsAChildWhereItsParentKeepsApplicationData() {
        assertThat(doesTheChildSay(makeAnInterpreterWithTheDataDirectoryMoved(), """
                Rebol []
                probe system/options/data
                probe system/options/modules""",
                "%/data/^/%/data/modules/^/")).isEqualTo(AGREED);
    }

    @Test
    @DisplayName("so it imports a module its parent installed a moment earlier")
    void importsAModuleItsParentInstalledAMomentEarlier() {
        Interpreter interpreter = makeAnInterpreterWithTheDataDirectoryMoved();
        String installing = """
                write system/options/modules/whisper.reb
                    {Rebol [name: whisper type: module] export whispered: 42}""";
        interpreter.defineNewWordsIn(installing);
        interpreter.run(installing);

        assertThat(doesTheChildSay(interpreter, """
                Rebol []
                import 'whisper
                probe whispered""", "42^/")).isEqualTo(AGREED);
    }

    @Test
    @DisplayName("and where the host moved nothing it has no modules directory, while the child makes one as r3 does at start")
    void givesOnlyTheChildAModulesDirectoryWhereTheHostMovedNothing() {
        Interpreter interpreter = makeAnInterpreterWithAHost();
        String asking = "none? system/options/modules";
        interpreter.defineNewWordsIn(asking);

        assertThat(interpreter.display(interpreter.run(asking))).isEqualTo("#(true)");
        assertThat(doesTheChildSay(interpreter, """
                Rebol []
                probe none? system/options/modules""", "#(false)^/")).isEqualTo(AGREED);
    }
}

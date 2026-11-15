package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class AWindowAskedForWithNoDisplayEndToEndTest {

    private static final int PROCESS_TIMEOUT_SECONDS = 60;

    private record Ran(int exitStatus, List<String> lines) {
    }

    private Ran runWithNoDisplay(Path directory, String script, String... switches)
            throws IOException, InterruptedException {

        Path file = directory.resolve("script.r3");
        Files.writeString(file, script);
        Path output = directory.resolve("output.txt");
        List<String> command = new ArrayList<>(List.of(
                ProcessHandle.current().info().command().orElseThrow(),
                "-cp", System.getProperty("java.class.path"),
                Repl.class.getName()));
        command.addAll(List.of(switches));
        command.add(file.toString());
        ProcessBuilder starting = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile());
        starting.environment().remove("DISPLAY");
        starting.environment().remove("WAYLAND_DISPLAY");
        Process process = starting.start();
        if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("the script was still running after "
                    + PROCESS_TIMEOUT_SECONDS + " seconds");
        }
        return new Ran(process.exitValue(), Files.readAllLines(output, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("asked for a window where X11 names no display, the command line says so and runs the script instead of crashing")
    void saysThereIsNoDisplay(@TempDir Path directory) throws Exception {
        Ran ran = runWithNoDisplay(directory, """
                Rebol []
                print "ran"
                """, "--screen=window");

        assertThat(ran.lines()).containsExactly(
                "Screen: asked for a window and this machine has no display.",
                "ran");
        assertThat(ran.exitStatus()).isZero();
    }

    @Test
    @DisplayName("and with no display it still makes a gob into an image, its colour, its draw block and its pane")
    void makesGobsIntoImagesWithNoDisplay(@TempDir Path directory) throws Exception {
        Ran ran = runWithNoDisplay(directory, """
                Rebol []
                shot: to image! make gob! [size: 3x2 color: 10.20.30]
                print [shot/size shot/1]
                drawn: make gob! [size: 4x2]
                drawn/draw: [pen off fill-pen 255.0.0 box 0x0 2x2]
                shot: to image! drawn
                print [shot/1 shot/4]
                parent: make gob! [size: 4x4 color: 0.0.255]
                append parent make gob! [offset: 2x2 size: 8x8 color: 0.255.0]
                shot: to image! parent
                print [shot/1 pick shot 3x3 pick shot 4x4]
                """, "--screen=window");

        assertThat(ran.lines()).containsExactly(
                "Screen: asked for a window and this machine has no display.",
                "3x2 10.20.30.255",
                "255.0.0.255 255.255.255.255",
                "0.0.255.255 0.255.0.255 0.255.0.255");
    }

    @Test
    @DisplayName("with no screen asked for, making a gob into an image is R3's refusal on a host that cannot compose")
    void refusesWithNoScreen(@TempDir Path directory) throws Exception {
        Ran ran = runWithNoDisplay(directory, """
                Rebol []
                made: try [to image! make gob! [size: 2x2]]
                print made/id
                """);

        assertThat(ran.lines()).containsExactly("bad-make-arg");
    }

    @Test
    @DisplayName("PICK takes a fraction of a position as R3's Get_Num_Arg does, and a pixel by a pair rounded as R3 rounds it")
    void picksByAFractionAndAPair(@TempDir Path directory) throws Exception {
        Ran ran = runWithNoDisplay(directory, """
                Rebol []
                print [pick [a b c] 2.7 pick "abc" 2.0 pick #{010203} 3.9 none? pick [a b c] 50%]
                img: make image! 3x2
                img/5: 9.9.9
                print [pick img 5.9 pick img 2x2 pick img 1.6x1.5 img/(1.6x1.5)]
                poke img 3x2 1.2.3
                print img/6
                too-far: try [pick [a b c] 1e10]
                by-percent: try [pick img 50%]
                print [too-far/id by-percent/id]
                """);

        assertThat(ran.lines()).containsExactly(
                "b b 3 true",
                "9.9.9.255 9.9.9.255 9.9.9.255 9.9.9.255",
                "1.2.3.255",
                "out-of-range cannot-use");
    }
}

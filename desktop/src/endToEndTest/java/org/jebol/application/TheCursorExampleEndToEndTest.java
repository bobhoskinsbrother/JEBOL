package org.jebol.application;

import org.jebol.adapter.cli.StreamOutput;
import org.jebol.adapter.host.FileSystemPort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.ScreenCursor;
import org.jebol.domain.host.SystemCursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
class TheCursorExampleEndToEndTest {

    private final RecordingScreen screen = RecordingScreen.measure(1024, 768).closeWhateverOpens();
    private final ByteArrayOutputStream printed = new ByteArrayOutputStream();

    private Interpreter runTheExample() {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts()
                        .withFiles(FileSystemPort.rootFileSystem(Path.of(".").toAbsolutePath()))
                        .withScreen(screen),
                new StreamOutput(new PrintStream(printed, true, StandardCharsets.UTF_8)),
                Bounds.standard()
                        .grant(HostService.WINDOWS)
                        .grant(HostService.FILES)
                        .grant(HostService.WORKING_DIRECTORY));
        interpreter.run("""
                change-dir %examples/cursor/
                do %main.reb""");
        return interpreter;
    }

    private String readPrinted() {
        return printed.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the operator closing the window ends it, after the first cursor")
    void endsWhenTheWindowCloses() {
        runTheExample();

        assertThat(readPrinted()).contains("""
                Move the pointer into the window. Closing the window ends this.
                arrow
                window closed
                """);
        assertThat(screen.listCursorsAskedFor())
                .containsExactly(new ScreenCursor.FromSystem(SystemCursor.ARROW));
        assertThat(screen.listWhatClosed()).hasSize(1);
    }

    @Test
    @DisplayName("and its list shows every system cursor once, the pointer hidden twice, and one picture")
    void showsEveryKindOfCursor() {
        Interpreter interpreter = runTheExample();
        int alreadyAsked = screen.listCursorsAskedFor().size();

        interpreter.run("foreach [name chosen] steps [cursor chosen]");

        List<ScreenCursor> asked = screen.listCursorsAskedFor()
                .subList(alreadyAsked, screen.listCursorsAskedFor().size());
        assertThat(asked).hasSize(SystemCursor.values().length + 3);
        assertThat(asked.stream().filter(ScreenCursor.FromSystem.class::isInstance).distinct())
                .hasSize(SystemCursor.values().length);
        assertThat(asked.stream().filter(ScreenCursor.Hidden.class::isInstance)).hasSize(2);
        assertThat(asked.getLast()).isInstanceOfSatisfying(ScreenCursor.Picture.class, picture -> {
            assertThat(picture.pixels().wide()).isEqualTo(32);
            assertThat(picture.pixels().high()).isEqualTo(32);
            assertThat(picture.pixels().rgba()).hasSize(32 * 32 * 4);
        });
    }

    @Test
    @DisplayName("and its picture is opaque bars of red and blue, eight pixels deep, wide enough to see")
    void paintsBarsYouCanSee() {
        Interpreter interpreter = runTheExample();

        assertThat(interpreter.display(interpreter.run("""
                reduce [picture/1 picture/256 picture/257 picture/512 picture/513]""")))
                .isEqualTo("[255.0.0.255 255.0.0.255 0.0.255.255 0.0.255.255 255.0.0.255]");
    }
}

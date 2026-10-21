package org.jebol.application;

import org.jebol.adapter.cli.StreamOutput;
import org.jebol.adapter.host.FileSystemPort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.WindowPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TheChooseColourExampleEndToEndTest {

    private static final class AnOperatorWithColours implements WindowPort {

        private final Deque<int[]> coloursToPick;
        private final List<List<Integer>> suggestions = new ArrayList<>();

        AnOperatorWithColours(int[]... coloursToPick) {
            this.coloursToPick = new ArrayDeque<>(List.of(coloursToPick));
        }

        @Override
        public Optional<int[]> chooseColour(Optional<int[]> suggested) {
            suggested.ifPresent(each -> suggestions.add(List.of(each[0], each[1], each[2])));
            return Optional.ofNullable(coloursToPick.poll());
        }

        List<List<Integer>> suggestions() {
            return suggestions;
        }

        @Override
        public void browse(String target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> chooseFiles(
                boolean forSaving, boolean allowingMany,
                Optional<String> suggestedName, Optional<String> title,
                List<String> filterPairs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<String> chooseDirectory(
                Optional<String> startingAt, Optional<String> title) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<String> askForPassword() {
            throw new UnsupportedOperationException();
        }
    }

    private static String runTheExampleWith(AnOperatorWithColours operator) {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts()
                        .withFiles(FileSystemPort.rootFileSystem(
                                Path.of(".").toAbsolutePath()))
                        .withWindows(operator),
                new StreamOutput(new PrintStream(printed, true, StandardCharsets.UTF_8)),
                Bounds.standard()
                        .grant(HostService.WINDOWS)
                        .grant(HostService.FILES)
                        .grant(HostService.WORKING_DIRECTORY));
        interpreter.run("""
                change-dir %examples/choose-colour/
                do %main.reb""");
        return printed.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the operator picks a colour and is told which one, and cancelling ends it")
    void picksAColourThenDeclines() {
        AnOperatorWithColours operator = new AnOperatorWithColours(new int[] {10, 20, 30});

        String printed = runTheExampleWith(operator);

        assertThat(printed).contains("""
                Pick a colour. Cancelling the chooser ends this.
                chosen 10.20.30
                no colour chosen
                """);
    }

    @Test
    @DisplayName("the chooser opens on the example's own colour, then on the one just picked")
    void carriesTheLastPickBackAsTheSuggestion() {
        AnOperatorWithColours operator = new AnOperatorWithColours(new int[] {10, 20, 30});

        runTheExampleWith(operator);

        assertThat(operator.suggestions())
                .containsExactly(List.of(0, 128, 255), List.of(10, 20, 30));
    }

    @Test
    @DisplayName("cancelling at once says nothing was chosen, and does not ask again")
    void decliningAtOnce() {
        AnOperatorWithColours operator = new AnOperatorWithColours();

        String printed = runTheExampleWith(operator);

        assertThat(printed).contains("""
                Pick a colour. Cancelling the chooser ends this.
                no colour chosen
                """);
        assertThat(operator.suggestions()).hasSize(1);
    }
}

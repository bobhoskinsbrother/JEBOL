package org.jebol.application;

import org.jebol.domain.host.ConsoleKey;
import org.jebol.domain.host.ConsoleMode;
import org.jebol.domain.host.ConsolePort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.host.ProcessPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TheConsoleModesFromTheSourceTest {

    private static final class RecordingConsole implements ConsolePort {

        private final List<String> told = new ArrayList<>();
        private final Deque<String> lines = new ArrayDeque<>();
        private final Deque<Optional<ConsoleKey>> keys = new ArrayDeque<>();
        private Runnable beforeEachRead = () -> {
        };

        @Override
        public String readLine() {
            beforeEachRead.run();
            return lines.poll();
        }

        @Override
        public String readHiddenLine() {
            beforeEachRead.run();
            return lines.poll();
        }

        @Override
        public Optional<ConsoleKey> readKey() {
            beforeEachRead.run();
            return keys.isEmpty() ? Optional.empty() : keys.poll();
        }

        @Override
        public void switchMode(ConsoleMode mode, boolean on) {
            told.add(mode + " " + on);
        }
    }

    private static final class RecordingOutput implements OutputPort {

        private final StringBuilder printed = new StringBuilder();
        private final StringBuilder complained = new StringBuilder();
        private Consumer<String> afterEachWrite = text -> {
        };

        @Override
        public void write(String text) {
            printed.append(text);
            afterEachWrite.accept(text);
        }

        @Override
        public void writeError(String text) {
            complained.append(text);
        }
    }

    private static final class RecordingProcesses implements ProcessPort {

        private final List<Program> asked = new ArrayList<>();

        @Override
        public ProgramResult run(Program program) {
            asked.add(program);
            return new ProgramResult(1, Optional.of(0), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    private final RecordingConsole console = new RecordingConsole();
    private final RecordingOutput output = new RecordingOutput();
    private final RecordingProcesses processes = new RecordingProcesses();

    private Interpreter makeInterpreter(HostService... granted) {
        Bounds bounds = Bounds.standard();
        for (HostService service : granted) {
            bounds = bounds.grant(service);
        }
        return Interpreter.createOn(
                AnInterpreter.standardPorts()
                        .withConsole(console)
                        .withProcesses(processes)
                        .withPause(milliseconds -> true),
                output, bounds);
    }

    private Interpreter makeInterpreterWithConsole() {
        return makeInterpreter(HostService.CONSOLE, HostService.PROCESSES);
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("MODIFY tells the console, on either port, and keeps nothing on the port")
    class TellingTheConsole {

        @ParameterizedTest(name = "{1} {2} through system/ports/{0}")
        @CsvSource({
                "input,  echo,  true,  ECHO",
                "input,  echo,  false, ECHO",
                "input,  line,  true,  LINE",
                "input,  line,  false, LINE",
                "input,  error, true,  ERROR",
                "input,  error, false, ERROR",
                "output, echo,  true,  ECHO",
                "output, echo,  false, ECHO",
                "output, line,  true,  LINE",
                "output, line,  false, LINE",
                "output, error, true,  ERROR",
                "output, error, false, ERROR",
        })
        void eachModeOnEachPortReachesTheConsole(String port, String mode, String setting, String told) {
            Interpreter interpreter = makeInterpreterWithConsole();

            String answer = answerTo(interpreter, "modify system/ports/" + port + " (quote " + mode + ") " + setting);

            assertThat(answer).isEqualTo("#(" + setting + ")");
            assertThat(console.told).containsExactly(told + " " + setting);
        }

        @Test
        @DisplayName("a mode is told each time it is set, the same setting twice included")
        void tellsEachTime() {
            answerTo(makeInterpreterWithConsole(), """
                    modify system/ports/input 'line false
                    modify system/ports/input 'line false
                    modify system/ports/output 'echo true""");

            assertThat(console.told).containsExactly("LINE false", "LINE false", "ECHO true");
        }

        @ParameterizedTest(name = "system/ports/{0}/{1}")
        @CsvSource({"output, echo", "output, line", "output, error", "input, echo"})
        void leavesNoFieldOnThePort(String port, String mode) {
            Interpreter interpreter = makeInterpreterWithConsole();

            String answer = answerTo(interpreter, "modify system/ports/" + port + " (quote " + mode + ") true "
                    + "failure: try [system/ports/" + port + "/" + mode + "] failure/id");

            assertThat(answer).isEqualTo("invalid-path");
        }

        @Test
        @DisplayName("a mode the console has not got, or a value that is not a logic, tells it nothing")
        void tellsNothingOfARefusal() {
            answerTo(makeInterpreterWithConsole(), """
                    try [modify system/ports/output 'bogus true]
                    try [modify system/ports/output 'echo 1]""");

            assertThat(console.told).isEmpty();
        }
    }

    @Nested
    @DisplayName("CALL/CONSOLE starts the program as CALL does, and only the flag differs")
    class TheConsoleRefinement {

        @Test
        @DisplayName("every stream, the command and the waiting are the same with /console as without")
        void changesNoStream() {
            answerTo(makeInterpreterWithConsole(), """
                    call/wait [{x}]
                    call/wait/console [{x}]""");

            ProcessPort.Program plain = processes.asked.getFirst();
            ProcessPort.Program attached = processes.asked.get(1);
            assertThat(plain.attachedToHostsConsole()).isFalse();
            assertThat(attached.attachedToHostsConsole()).isTrue();
            assertThat(attached).usingRecursiveComparison()
                    .ignoringFields("attachedToHostsConsole")
                    .isEqualTo(plain);
        }
    }

    @Nested
    @DisplayName("READ-KEY answers the key the console decoded")
    class ReadingAKey {

        @Test
        @DisplayName("a character comes back as itself, with the modifiers the console saw")
        void answersACharacterAndItsModifiers() {
            console.keys.add(Optional.of(new ConsoleKey(1, 0, true, false, false)));

            String answer = answerTo(makeInterpreterWithConsole(), """
                    k: read-key
                    reduce [k system/state/control? system/state/shift? system/state/alt?]""");

            assertThat(answer).isEqualTo("[#\"^A\" #(true) #(false) #(false)]");
        }

        @ParameterizedTest(name = "virtual key {0} is {1}")
        @CsvSource({"1, page-up", "6, up", "24, paste-end", "25, escape", "33, begin"})
        void answersAVirtualKeyAsTheWordTheCatalogueGivesIt(int virtualKey, String word) {
            console.keys.add(Optional.of(new ConsoleKey(0, virtualKey, false, true, true)));

            String answer = answerTo(makeInterpreterWithConsole(), """
                    k: read-key
                    reduce [k system/state/control? system/state/shift? system/state/alt?]""");

            assertThat(answer).isEqualTo("[" + word + " #(false) #(true) #(true)]");
        }

        @Test
        @DisplayName("a virtual key past the end of the catalogue, or none at all, answers none")
        void answersNoneForAVirtualKeyTheCatalogueHasNot() {
            console.keys.add(Optional.of(new ConsoleKey(0, 34, false, false, false)));
            console.keys.add(Optional.of(new ConsoleKey(0, 0, false, false, false)));

            assertThat(answerTo(makeInterpreterWithConsole(), """
                    reduce [read-key read-key]""")).isEqualTo("[_ _]");
        }

        @Test
        @DisplayName("no key read answers none and leaves the modifiers as they were")
        void answersNoneWhenNoKeyIsRead() {
            console.keys.add(Optional.of(new ConsoleKey('a', 0, true, true, true)));

            String answer = answerTo(makeInterpreterWithConsole(), """
                    read-key
                    k: read-key
                    reduce [k system/state/control? system/state/shift? system/state/alt?]""");

            assertThat(answer).isEqualTo("[_ #(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("Ctrl-C read as a key is the key, and the escape it raised is spent")
        void ctrlCAsAKeyClearsTheEscape() {
            Interpreter interpreter = makeInterpreterWithConsole();
            console.keys.add(Optional.of(new ConsoleKey(3, 0, true, false, false)));
            console.beforeEachRead = interpreter::escape;

            ScriptOutcome outcome = interpreter.run("""
                    k: read-key
                    print "after"
                    k""");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.PRODUCED_VALUE);
            assertThat(interpreter.display(outcome)).isEqualTo("#\"^C\"");
            assertThat(output.printed).hasToString("after\n");
        }
    }

    @Nested
    @DisplayName("ASK and INPUT at the end of input answer an empty string, as the C does")
    class TheEndOfInput {

        @Test
        @DisplayName("INPUT with nothing left is an empty string, not none")
        void inputAnswersAnEmptyString() {
            assertThat(answerTo(makeInterpreterWithConsole(), "input")).isEqualTo("\"\"");
        }

        @Test
        @DisplayName("ASK with nothing left is an empty string, after its question")
        void askAnswersAnEmptyString() {
            assertThat(answerTo(makeInterpreterWithConsole(), """
                    ask {Q? }""")).isEqualTo("\"\"");
            assertThat(output.printed).hasToString("Q? ");
        }

        @Test
        @DisplayName("READ of the console port with nothing left is an empty string too")
        void readingThePortAnswersAnEmptyString() {
            assertThat(answerTo(makeInterpreterWithConsole(), """
                    to string! read system/ports/input""")).isEqualTo("\"\"");
        }
    }

    @Nested
    @DisplayName("REQUEST-PASSWORD reads a console that reads, before any screen or window")
    class ThePassword {

        @Test
        @DisplayName("the hidden line comes from the console, with the console granted and windows not")
        void readsTheHiddenLine() {
            console.lines.add("secret");

            assertThat(answerTo(makeInterpreter(HostService.CONSOLE), "request-password"))
                    .isEqualTo("\"secret\"");
        }

        @Test
        @DisplayName("ASK/HIDE asks the console too, and prints a line feed after")
        void askHideReadsTheHiddenLine() {
            console.lines.add("secret");

            String answer = answerTo(makeInterpreter(HostService.CONSOLE), """
                    ask/hide {P? }""");

            assertThat(answer).isEqualTo("\"secret\"");
            assertThat(output.printed).hasToString("P? \n");
        }

        @Test
        @DisplayName("a console that answers nothing, as ESC does, answers none")
        void answersNoneWhenTheConsoleDoes() {
            assertThat(answerTo(makeInterpreter(HostService.CONSOLE), "request-password"))
                    .isEqualTo("_");
        }

        @Test
        @DisplayName("without the console granted it is refused, windows or not")
        void needsTheConsole() {
            console.lines.add("secret");

            assertThat(answerTo(makeInterpreter(HostService.WINDOWS), """
                    failure: try [request-password] failure/id""")).isEqualTo("no-service");
            assertThat(console.lines).containsExactly("secret");
        }

        @Test
        @DisplayName("a console that cannot read leaves it to the windows, as before")
        void leavesAConsoleThatCannotReadToTheWindows() {
            Interpreter interpreter = Interpreter.createOn(
                    AnInterpreter.standardPorts().withConsole(ConsolePort.none()),
                    output, Bounds.standard().grant(HostService.CONSOLE).grant(HostService.WINDOWS));

            assertThat(answerTo(interpreter, """
                    failure: try [request-password] failure/arg1"""))
                    .contains("no screen to put a window on");
        }
    }

    @Nested
    @DisplayName("an escape halts the run at its next step")
    class TheEscape {

        @Test
        @DisplayName("raised while a line is read, the run halts once the read returns")
        void haltsAfterTheRead() {
            Interpreter interpreter = makeInterpreterWithConsole();
            console.lines.add("typed");
            console.beforeEachRead = interpreter::escape;

            ScriptOutcome outcome = interpreter.run("""
                    probe ask {Q? }
                    print {after}""");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.HALTED);
            assertThat(output.printed).hasToString("Q? ");
            assertThat(output.complained).isEmpty();
        }

        @Test
        @DisplayName("raised while a password is read, the same")
        void haltsAfterThePassword() {
            Interpreter interpreter = makeInterpreterWithConsole();
            console.lines.add("typed");
            console.beforeEachRead = interpreter::escape;

            ScriptOutcome outcome = interpreter.run("""
                    probe request-password
                    print {after}""");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.HALTED);
            assertThat(output.printed).isEmpty();
        }

        @Test
        @DisplayName("raised during WAIT, it writes [ESC] to the error stream and halts")
        void writesEscInsideWait() {
            List<Interpreter> waiting = new ArrayList<>();
            waiting.add(Interpreter.createOn(
                    AnInterpreter.standardPorts().withConsole(console).withPause(milliseconds -> {
                        waiting.getFirst().escape();
                        return true;
                    }),
                    output, Bounds.standard().grant(HostService.CONSOLE).grant(HostService.CLOCK)));

            ScriptOutcome outcome = waiting.getFirst().run("""
                    wait 1
                    print {after}""");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.HALTED);
            assertThat(output.complained).hasToString("[ESC]\n");
            assertThat(output.printed).isEmpty();
        }

        @Test
        @DisplayName("raised while the script computes, it halts within the run and writes nothing")
        void haltsAComputation() {
            Interpreter interpreter = makeInterpreterWithConsole();
            output.afterEachWrite = text -> interpreter.escape();

            ScriptOutcome outcome = interpreter.run("""
                    print "W"
                    forever [1 + 1]""");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.HALTED);
            assertThat(output.complained).isEmpty();
        }

        @Test
        @DisplayName("an escape raised between runs is forgotten when the next one starts")
        void isForgottenBetweenRuns() {
            Interpreter interpreter = makeInterpreterWithConsole();
            interpreter.escape();

            ScriptOutcome outcome = interpreter.run("1 + 1");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.PRODUCED_VALUE);
        }
    }
}

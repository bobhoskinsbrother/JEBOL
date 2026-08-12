package org.jebol.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.jebol.domain.eval.ProcessPort;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Specified in {@code spec/natives.allium} under "Starting another program"
 * and in {@code spec/embed.allium} as ProgramToStart and ProgramResult, read
 * from {@code n-io.c} and {@code call-test.r3}.
 */
class CallNativeTest {

    private static final class Recorded implements ProcessPort {

        private ProgramToStart asked;
        private byte[] childPrints = new byte[0];
        private byte[] childComplains = new byte[0];
        private Optional<String> refusal = Optional.empty();

        @Override
        public ProgramResult run(ProgramToStart program) {
            this.asked = program;
            if (refusal.isPresent()) {
                return new ProgramResult(0,
                        program.waitedFor() ? Optional.of(0) : Optional.empty(),
                        Optional.empty(), Optional.empty(), refusal);
            }
            return new ProgramResult(4242,
                    program.waitedFor() ? Optional.of(3) : Optional.empty(),
                    program.standardOutput() == ProgramOutput.CAPTURED
                            ? Optional.of(childPrints) : Optional.empty(),
                    program.standardError() == ProgramOutput.CAPTURED
                            ? Optional.of(childComplains) : Optional.empty(),
                    Optional.empty());
        }
    }

    private static Interpreter reaching(boolean granted, Recorded port) {
        Bounds bounds = granted
                ? Bounds.standard().granting(HostService.PROCESSES)
                : Bounds.standard();
        Interpreter interpreter = Interpreter.withBounds(bounds);
        interpreter.useProcesses(port);
        return interpreter;
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(Interpreter interpreter, String source) {
        return answerTo(interpreter,
                "e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Nested
    @DisplayName("the command, in its three forms")
    class TheCommand {

        @Test
        @DisplayName("a block is the program and its arguments, already separated")
        void aBlockIsTakenAsItStands() {
            Recorded port = new Recorded();
            answerTo(reaching(true, port), """
                    call [{ls} {-l}]""");
            assertThat(port.asked.command()).containsExactly("ls", "-l");
        }

        @Test
        @DisplayName("a block does not go through a shell")
        void aBlockNeedsNoShell() {
            Recorded port = new Recorded();
            answerTo(reaching(true, port), """
                    call [{ls}]""");
            assertThat(port.asked.readByTheShell()).isFalse();
        }

        @Test
        @DisplayName("a string is one word, and the shell stays opt-in")
        void aStringIsOneWordWithoutTheShell() {
            Recorded port = new Recorded();
            answerTo(reaching(true, port), """
                    call {ls -l}""");
            assertThat(port.asked.command()).containsExactly("ls -l");
            assertThat(port.asked.readByTheShell())
                    .as("the C sets the flag from /shell alone; an implicit shell"
                            + " hands every joined command string to metacharacters")
                    .isFalse();
        }

        @Test
        @DisplayName("a file is the program itself, and needs no shell")
        void aFileIsTheProgramItself() {
            Recorded port = new Recorded();
            answerTo(reaching(true, port), """
                    call %/bin/ls""");
            assertThat(port.asked.command()).containsExactly("/bin/ls");
            assertThat(port.asked.readByTheShell()).isFalse();
        }

        @Test
        @DisplayName("/SHELL makes a block go through the shell too")
        void theShellCanBeAskedFor() {
            Recorded port = new Recorded();
            answerTo(reaching(true, port), """
                    call/shell [{ls}]""");
            assertThat(port.asked.readByTheShell()).isTrue();
        }

        @Test
        @DisplayName("each kind of block item becomes text its own way")
        void eachBlockItemBecomesText() {
            Recorded port = new Recorded();
            answerTo(reaching(true, port), """
                    prog: {resolved} call [:prog %f.txt {plain} bare]""");
            assertThat(port.asked.command())
                    .containsExactly("resolved", "f.txt", "plain", "bare");
        }

        @Test
        @DisplayName("an empty block raises too-short")
        void anEmptyBlockRaisesTooShort() {
            assertThat(errorIdOf(reaching(true, new Recorded()), """
                    call []""")).isEqualTo("too-short");
        }

        @Test
        @DisplayName("a number in the block raises invalid-arg")
        void aNumberInTheBlockRaises() {
            assertThat(errorIdOf(reaching(true, new Recorded()), """
                    call [1]""")).isEqualTo("invalid-arg");
        }
    }

    @Nested
    @DisplayName("feeding the child's input")
    class FeedingInput {

        @Test
        @DisplayName("a string pipes its UTF-8 bytes, and implies waiting")
        void aStringPipesItsBytes() {
            Recorded port = new Recorded();
            answerTo(reaching(true, port), """
                    call/input [{x}] {héllo}""");
            assertThat(port.asked.standardInput()).isEqualTo(
                    ProcessPort.ProgramInput.SUPPLIED_BYTES);
            assertThat(port.asked.inputBytes().orElseThrow())
                    .isEqualTo("héllo".getBytes(StandardCharsets.UTF_8));
            assertThat(port.asked.waitedFor()).isTrue();
        }

        @Test
        @DisplayName("a binary pipes its bytes exactly, and implies waiting")
        void aBinaryPipesItsBytes() {
            Recorded port = new Recorded();
            answerTo(reaching(true, port), """
                    call/input [{x}] #{00FF}""");
            assertThat(port.asked.inputBytes().orElseThrow())
                    .containsExactly(0x00, (byte) 0xFF);
            assertThat(port.asked.waitedFor()).isTrue();
        }

        @Test
        @DisplayName("none closes the input, and does not imply waiting")
        void noneClosesTheInput() {
            Recorded port = new Recorded();
            String answer = answerTo(reaching(true, port), """
                    call/input [{x}] none""");
            assertThat(port.asked.standardInput()).isEqualTo(
                    ProcessPort.ProgramInput.NOTHING_AT_ALL);
            assertThat(port.asked.waitedFor()).isFalse();
            assertThat(answer).isEqualTo("4242");
        }

        @Test
        @DisplayName("a file names what feeds the input, and does not imply waiting")
        void aFileFeedsTheInput() {
            Recorded port = new Recorded();
            answerTo(reaching(true, port), """
                    call/input [{x}] %in.txt""");
            assertThat(port.asked.standardInput()).isEqualTo(
                    ProcessPort.ProgramInput.A_FILES_CONTENTS);
            assertThat(port.asked.inputFile().orElseThrow()).isEqualTo("in.txt");
            assertThat(port.asked.waitedFor()).isFalse();
        }

        @Test
        @DisplayName("a redirect file resolves where READ would resolve it")
        void aRedirectFileResolvesUnderTheFilePort(
                @TempDir java.nio.file.Path directory) {
            Recorded port = new Recorded();
            Interpreter interpreter = reaching(true, port);
            interpreter.useFileSystem(FileSystemPort.rootedAt(directory));
            answerTo(interpreter, """
                    call/input/output [{x}] %in.txt %out.txt""");
            assertThat(port.asked.inputFile().orElseThrow())
                    .isEqualTo(directory.resolve("in.txt").toString());
            assertThat(port.asked.outputFile().orElseThrow())
                    .isEqualTo(directory.resolve("out.txt").toString());
        }

        @Test
        @DisplayName("an unnamed input is the host's own")
        void anUnnamedInputIsTheHostsOwn() {
            Recorded port = new Recorded();
            answerTo(reaching(true, port), """
                    call [{x}]""");
            assertThat(port.asked.standardInput()).isEqualTo(
                    ProcessPort.ProgramInput.THE_HOSTS_OWN);
        }

        @Test
        @DisplayName("a number is refused as an input")
        void aNumberIsRefusedAsAnInput() {
            assertThat(errorIdOf(reaching(true, new Recorded()), """
                    call/input [{x}] 5""")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("routing the child's output and error streams")
    class RoutingOutput {

        @Test
        @DisplayName("a string buffer captures by appending, and implies waiting")
        void aStringBufferCapturesByAppending() {
            Recorded port = new Recorded();
            port.childPrints = "printed".getBytes(StandardCharsets.UTF_8);
            Interpreter interpreter = reaching(true, port);
            String answer = answerTo(interpreter, """
                    buf: copy {old} call/output [{x}] buf buf""");
            assertThat(answer).isEqualTo("\"oldprinted\"");
            assertThat(port.asked.waitedFor()).isTrue();
            assertThat(port.asked.standardOutput()).isEqualTo(
                    ProcessPort.ProgramOutput.CAPTURED);
        }

        @Test
        @DisplayName("captured text crosses back as UTF-8")
        void capturedTextCrossesBackAsUtf8() {
            Recorded port = new Recorded();
            port.childPrints = "é".getBytes(StandardCharsets.UTF_8);
            assertThat(answerTo(reaching(true, port), """
                    buf: copy {} call/output [{x}] buf buf = {é}"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a binary buffer captures the bytes exactly")
        void aBinaryBufferCapturesTheBytes() {
            Recorded port = new Recorded();
            port.childPrints = new byte[] {0x01, (byte) 0xFF};
            assertThat(answerTo(reaching(true, port), """
                    buf: copy #{} call/output [{x}] buf buf"""))
                    .isEqualTo("#{01FF}");
        }

        @Test
        @DisplayName("output and error capture into two buffers at once")
        void outputAndErrorCaptureTogether() {
            Recorded port = new Recorded();
            port.childPrints = "out".getBytes(StandardCharsets.UTF_8);
            port.childComplains = "err".getBytes(StandardCharsets.UTF_8);
            assertThat(answerTo(reaching(true, port), """
                    o: copy {} e: copy {} call/output/error [{x}] o e reduce [o e]"""))
                    .isEqualTo("[\"out\" \"err\"]");
        }

        @Test
        @DisplayName("a file routes the output without waiting")
        void aFileRoutesTheOutputWithoutWaiting() {
            Recorded port = new Recorded();
            String answer = answerTo(reaching(true, port), """
                    call/output [{x}] %out.txt""");
            assertThat(port.asked.standardOutput()).isEqualTo(
                    ProcessPort.ProgramOutput.INTO_A_FILE);
            assertThat(port.asked.outputFile().orElseThrow()).isEqualTo("out.txt");
            assertThat(port.asked.waitedFor()).isFalse();
            assertThat(answer).isEqualTo("4242");
        }

        @Test
        @DisplayName("none discards the stream")
        void noneDiscardsTheStream() {
            Recorded port = new Recorded();
            answerTo(reaching(true, port), """
                    call/error [{x}] none""");
            assertThat(port.asked.standardError()).isEqualTo(
                    ProcessPort.ProgramOutput.DISCARDED);
        }

        @Test
        @DisplayName("unnamed streams are the host's own")
        void unnamedStreamsAreTheHostsOwn() {
            Recorded port = new Recorded();
            answerTo(reaching(true, port), """
                    call [{x}]""");
            assertThat(port.asked.standardOutput()).isEqualTo(
                    ProcessPort.ProgramOutput.THE_HOSTS_OWN);
            assertThat(port.asked.standardError()).isEqualTo(
                    ProcessPort.ProgramOutput.THE_HOSTS_OWN);
        }

        @Test
        @DisplayName("/CONSOLE attaches the child to the host's console")
        void consoleAttachesTheChild() {
            Recorded port = new Recorded();
            answerTo(reaching(true, port), """
                    call/console [{x}]""");
            assertThat(port.asked.attachedToTheHostsConsole()).isTrue();
        }
    }

    @Nested
    @DisplayName("the answer")
    class TheAnswer {

        @Test
        @DisplayName("without /WAIT the answer is the number of the new process")
        void notWaitingAnswersTheNumber() {
            assertThat(answerTo(reaching(true, new Recorded()), """
                    call [{ls}]""")).isEqualTo("4242");
        }

        @Test
        @DisplayName("/WAIT answers the exit code")
        void waitingAnswersTheCode() {
            assertThat(answerTo(reaching(true, new Recorded()), """
                    call/wait [{ls}]""")).isEqualTo("3");
        }

        @Test
        @DisplayName("/INFO without waiting answers an object holding the number alone")
        void infoWithoutWaitingHoldsTheNumberAlone() {
            Interpreter interpreter = reaching(true, new Recorded());
            assertThat(answerTo(interpreter, """
                    o: call/info [{x}] mold words-of o"""))
                    .isEqualTo("\"[id]\"");
            assertThat(answerTo(interpreter, "o/id")).isEqualTo("4242");
        }

        @Test
        @DisplayName("/INFO with /WAIT adds the exit code")
        void infoWithWaitingAddsTheExitCode() {
            Interpreter interpreter = reaching(true, new Recorded());
            assertThat(answerTo(interpreter, """
                    o: call/info/wait [{x}] reduce [o/id o/exit-code]"""))
                    .isEqualTo("[4242 3]");
        }

        @Test
        @DisplayName("/INFO carries a refusal to start as a field, not an error")
        void infoCarriesTheRefusal() {
            Recorded port = new Recorded();
            port.refusal = Optional.of("no such program");
            assertThat(answerTo(reaching(true, port), """
                    o: call/info [{x}] o/error"""))
                    .isEqualTo("\"no such program\"");
        }

        @Test
        @DisplayName("without /INFO a refusal to start raises call-fail")
        void withoutInfoARefusalRaises() {
            Recorded port = new Recorded();
            port.refusal = Optional.of("no such program");
            assertThat(errorIdOf(reaching(true, port), """
                    call [{x}]""")).isEqualTo("call-fail");
        }

        @Test
        @DisplayName("a port that waited yet answers no exit code is an error, not a throw")
        void aBrokenPortIsAnErrorNotAThrow() {
            ProcessPort broken = program -> new ProcessPort.ProgramResult(
                    1, Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty());
            Interpreter interpreter = Interpreter.withBounds(
                    Bounds.standard().granting(HostService.PROCESSES));
            interpreter.useProcesses(broken);
            assertThat(errorIdOf(interpreter, """
                    call/wait [{x}]""")).isEqualTo("call-fail");
        }
    }

    @Test
    @DisplayName("without the grant CALL is refused")
    void theGrantIsNeeded() {
        assertThat(errorIdOf(reaching(false, new Recorded()), """
                call [{ls}]""")).isEqualTo("no-service");
    }

    @Test
    @DisplayName("with the grant and no port, CALL still fails")
    void aPortIsAlsoNeeded() {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.PROCESSES));
        assertThat(errorIdOf(interpreter, """
                call [{ls}]""")).isEqualTo("no-port");
    }
}

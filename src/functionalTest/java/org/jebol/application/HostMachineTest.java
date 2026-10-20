package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.MachinePort;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.host.ProcessPort;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HostMachineTest {

    private record LauncherRequest(String root, String dataDirectory) {
    }

    private static final class DescribedMachine implements MachinePort {

        private String operatingSystem = "Linux";
        private char fileSeparator = '/';
        private String homeDirectory = "/home/someone";
        private String currentDirectory = "/work";
        private long processNumber = 4242;
        private final Map<String, Value> launchersByRoot = new HashMap<>();
        private final List<LauncherRequest> launchersAskedFor = new ArrayList<>();

        DescribedMachine named(String name) {
            operatingSystem = name;
            return this;
        }

        DescribedMachine separatedBy(char separator) {
            fileSeparator = separator;
            return this;
        }

        DescribedMachine homeAt(String directory) {
            homeDirectory = directory;
            return this;
        }

        DescribedMachine startedIn(String directory) {
            currentDirectory = directory;
            return this;
        }

        DescribedMachine numbered(long number) {
            processNumber = number;
            return this;
        }

        DescribedMachine launchesFor(String root, String launcher) {
            launchersByRoot.put(root, FileValue.of(launcher));
            return this;
        }

        @Override
        public String operatingSystem() {
            return operatingSystem;
        }

        @Override
        public char fileSeparator() {
            return fileSeparator;
        }

        @Override
        public String homeDirectory() {
            return homeDirectory;
        }

        @Override
        public String currentDirectory() {
            return currentDirectory;
        }

        @Override
        public long processNumber() {
            return processNumber;
        }

        @Override
        public Value launcherFor(String root, String dataDirectory) {
            launchersAskedFor.add(new LauncherRequest(root, dataDirectory));
            return launchersByRoot.getOrDefault(root, NoneValue.none());
        }
    }

    private static final class RecordedProcesses implements ProcessPort {

        private final Set<Long> running = new HashSet<>();
        private boolean mayEnd = true;
        private final List<String> signalsSent = new ArrayList<>();

        RecordedProcesses running(long number) {
            running.add(number);
            return this;
        }

        RecordedProcesses refusingToEnd() {
            mayEnd = false;
            return this;
        }

        @Override
        public ProgramResult run(Program program) {
            throw new UnsupportedOperationException("no program is started here");
        }

        @Override
        public boolean isRunning(long processNumber) {
            return running.contains(processNumber);
        }

        @Override
        public boolean end(long processNumber, int signal) {
            signalsSent.add(processNumber + " " + signal);
            return mayEnd;
        }
    }

    private Interpreter interpreterOn(MachinePort machine) {
        return interpreterOn(machine, Bounds.standard());
    }

    private Interpreter interpreterOn(MachinePort machine, Bounds bounds) {
        return Interpreter.createOn(
                Interpreter.standardPorts().withMachine(machine),
                OutputPort.discard(),
                bounds);
    }

    private String answerOn(MachinePort machine, String source) {
        return interpreterOn(machine).run(source).display();
    }

    private Bounds grantingProcesses() {
        return Bounds.standard().grant(HostService.PROCESSES);
    }

    @Nested
    @DisplayName("a script sees the machine the host described")
    class ScriptSeesHostMachine {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"Windows", "macOS", "Linux", "Haiku", "X"})
        @DisplayName("system/platform is the word the machine names itself by, one of Rebol's three or not")
        void namesPlatform(String name) {
            assertThat(answerOn(new DescribedMachine().named(name), """
                    system/platform""")).isEqualTo(name);
        }

        @Test
        @DisplayName("an operating system Rebol does not know is still a word a script can test")
        void keepsUnknownPlatformWord() {
            assertThat(answerOn(new DescribedMachine().named("Haiku"), """
                    reduce [word? system/platform  system/platform = 'Haiku]""")).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("system/options/home is the machine's home, as a directory")
        void placesHome() {
            assertThat(answerOn(new DescribedMachine().homeAt("/home/someone"), """
                    system/options/home""")).isEqualTo("%/home/someone/");
        }

        @Test
        @DisplayName("a home that already ends in a slash gets no second one")
        void keepsOneSlashOnHome() {
            assertThat(answerOn(new DescribedMachine().homeAt("/home/someone/"), """
                    system/options/home""")).isEqualTo("%/home/someone/");
        }

        @Test
        @DisplayName("a home at the root of the machine is the root, not two slashes")
        void placesHomeAtRoot() {
            assertThat(answerOn(new DescribedMachine().homeAt("/"), """
                    reduce [system/options/home  system/options/data]""")).isEqualTo("[%/ %/.jebol/]");
        }

        @Test
        @DisplayName("a machine that reports no home and no start directory gives the root for both")
        void placesEmptyDirectoriesAtRoot() {
            assertThat(answerOn(new DescribedMachine().homeAt("").startedIn(""), """
                    reduce [system/options/home  system/options/path]""")).isEqualTo("[%/ %/]");
        }

        @Test
        @DisplayName("system/options/path is where the machine says the host was started, as a directory")
        void placesPath() {
            assertThat(answerOn(new DescribedMachine().startedIn("/work"), """
                    system/options/path""")).isEqualTo("%/work/");
        }

        @Test
        @DisplayName("system/options/data sits in the machine's home")
        void placesData() {
            assertThat(answerOn(new DescribedMachine().homeAt("/home/someone"), """
                    system/options/data""")).isEqualTo("%/home/someone/.jebol/");
        }

        @Test
        @DisplayName("to-local-file writes the machine's separator, a backslash on a machine that uses one")
        void writesBackslash() {
            Object written = interpreterOn(new DescribedMachine().separatedBy('\\')).run("""
                    to-local-file %/a/b""").asHostValue();

            assertThat(written).isEqualTo("\\a\\b");
        }

        @Test
        @DisplayName("to-local-file writes a slash on a machine that uses one")
        void writesSlash() {
            Object written = interpreterOn(new DescribedMachine().separatedBy('/')).run("""
                    to-local-file %/a/b""").asHostValue();

            assertThat(written).isEqualTo("/a/b");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(longs = {1, 4242, 2147483647L, 2147483648L})
        @DisplayName("access-os 'pid is the number the machine knows the process by")
        void answersProcessNumber(long number) {
            assertThat(answerOn(new DescribedMachine().numbered(number), """
                    access-os 'pid""")).isEqualTo(Long.toString(number));
        }
    }

    @Nested
    @DisplayName("a machine with no name is refused")
    class MachineWithoutName {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"", " ", "\t"})
        @DisplayName("a blank operating system is refused when the interpreter is built")
        void refusesBlankName(String blank) {
            DescribedMachine machine = new DescribedMachine().named(blank);

            assertThatThrownBy(() -> interpreterOn(machine))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("no machine at all is refused when the interpreter is built")
        void refusesNoMachine() {
            assertThatThrownBy(() -> Interpreter.createOn(null, OutputPort.discard(), Bounds.standard()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("system/options/boot is the launcher the machine makes")
    class MachineMakesLauncher {

        @Test
        @DisplayName("with processes granted, boot is the machine's launcher for the whole machine")
        void pointsBootAtLauncher() {
            DescribedMachine machine = new DescribedMachine().launchesFor("/", "/tmp/launch.sh");

            assertThat(interpreterOn(machine, grantingProcesses()).run("""
                    system/options/boot""").display()).isEqualTo("%/tmp/launch.sh");
            assertThat(machine.launchersAskedFor).containsExactly(new LauncherRequest("/", ""));
        }

        @Test
        @DisplayName("a launcher at a path with a space in it is still the whole path")
        void keepsSpaceInLauncher() {
            DescribedMachine machine = new DescribedMachine().launchesFor("/", "/tmp/my launch.sh");

            assertThat(interpreterOn(machine, grantingProcesses()).run("""
                    to-local-file system/options/boot""").asHostValue()).isEqualTo("/tmp/my launch.sh");
        }

        @Test
        @DisplayName("a machine that cannot make a launcher leaves boot none")
        void leavesBootNoneWithoutLauncher() {
            assertThat(interpreterOn(new DescribedMachine(), grantingProcesses()).run("""
                    none? system/options/boot""").display()).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("given a filesystem, the launcher is asked for again, rooted there and told the data directory")
        void asksAgainForConfinedLauncher(@TempDir Path directory) {
            String root = directory.toAbsolutePath().normalize().toString();
            DescribedMachine machine = new DescribedMachine()
                    .launchesFor("/", "/tmp/whole.sh")
                    .launchesFor(root, "/tmp/confined.sh");
            Interpreter interpreter = Interpreter.createOn(
                    Interpreter.standardPorts()
                            .withMachine(machine)
                            .withFiles(FileSystemPort.rootFileSystem(directory)),
                    OutputPort.discard(),
                    grantingProcesses().grant(HostService.FILES));

            assertThat(machine.launchersAskedFor).last()
                    .isEqualTo(new LauncherRequest(root, "/home/someone/.jebol/"));
            assertThat(interpreter.run("""
                    system/options/boot""").display()).isEqualTo("%/tmp/confined.sh");
        }

        @Test
        @DisplayName("a confined launcher the machine cannot make leaves boot none, never the unconfined one")
        void dropsUnconfinedLauncher(@TempDir Path directory) {
            DescribedMachine machine = new DescribedMachine().launchesFor("/", "/tmp/whole.sh");
            Interpreter interpreter = Interpreter.createOn(
                    Interpreter.standardPorts()
                            .withMachine(machine)
                            .withFiles(FileSystemPort.rootFileSystem(directory)),
                    OutputPort.discard(),
                    grantingProcesses().grant(HostService.FILES));

            assertThat(interpreter.run("""
                    none? system/options/boot""").display()).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("without processes, there is no launcher")
    class NoProcessesNoLauncher {

        @Test
        @DisplayName("boot is none and the machine is never asked to write one")
        void neverAsksForLauncher() {
            DescribedMachine machine = new DescribedMachine().launchesFor("/", "/tmp/launch.sh");

            assertThat(interpreterOn(machine).run("""
                    none? system/options/boot""").display()).isEqualTo("#(true)");
            assertThat(machine.launchersAskedFor).isEmpty();
        }
    }

    @Nested
    @DisplayName("ending a process asks the host")
    class EndingProcessAsksHost {

        private Interpreter withProcesses(RecordedProcesses processes) {
            return Interpreter.createOn(
                    Interpreter.standardPorts()
                            .withMachine(new DescribedMachine())
                            .withProcesses(processes),
                    OutputPort.discard(),
                    grantingProcesses());
        }

        @Test
        @DisplayName("a host that gave no way to start a program gives no way to end one: no-port")
        void refusesWithoutProcessPort() {
            ScriptOutcome outcome = interpreterOn(new DescribedMachine(), grantingProcesses()).run("""
                    access-os/set 'pid 77""");

            assertThat(outcome.errorId()).contains("no-port");
        }

        @Test
        @DisplayName("a number the host has no process under is process-not-found naming it, and nothing is signalled")
        void namesMissingProcess() {
            RecordedProcesses processes = new RecordedProcesses();

            assertThat(withProcesses(processes).run("""
                    e: try [access-os/set 'pid 77]
                    reduce [e/id e/arg1]""").display()).isEqualTo("[process-not-found 77]");
            assertThat(processes.signalsSent).isEmpty();
        }

        @Test
        @DisplayName("a process the host could not end is permission-denied")
        void refusesAsPermission() {
            ScriptOutcome outcome = withProcesses(new RecordedProcesses().running(77).refusingToEnd()).run("""
                    access-os/set 'pid 77""");

            assertThat(outcome.errorId()).contains("permission-denied");
        }

        @Test
        @DisplayName("a number alone sends the terminate signal, and the host's yes is true")
        void sendsTerminate() {
            RecordedProcesses processes = new RecordedProcesses().running(77);

            assertThat(withProcesses(processes).run("""
                    access-os/set 'pid 77""").display()).isEqualTo("#(true)");
            assertThat(processes.signalsSent).containsExactly("77 15");
        }

        @Test
        @DisplayName("a pair sends the signal it names")
        void sendsNamedSignal() {
            RecordedProcesses processes = new RecordedProcesses().running(77);

            withProcesses(processes).run("""
                    access-os/set 'pid [77 9]""");

            assertThat(processes.signalsSent).containsExactly("77 9");
        }

        @Test
        @DisplayName("without processes granted it is no-service, and the host is never asked")
        void refusesWithoutGrant() {
            RecordedProcesses processes = new RecordedProcesses().running(77);
            Interpreter interpreter = Interpreter.createOn(
                    Interpreter.standardPorts()
                            .withMachine(new DescribedMachine())
                            .withProcesses(processes),
                    OutputPort.discard(),
                    Bounds.standard());

            assertThat(interpreter.run("""
                    access-os/set 'pid 77""").errorId()).contains("no-service");
            assertThat(processes.signalsSent).isEmpty();
        }
    }

    @Nested
    @DisplayName("a host that describes no machine gets the JVM's")
    class DefaultMachine {

        @Test
        @DisplayName("Interpreter.create reports what the JVM's machine says")
        void reportsJvmMachine() {
            JavaMachine machine = new JavaMachine();

            assertThat(Interpreter.create().run("""
                    reduce [system/platform  system/options/home  access-os 'pid]""").display())
                    .isEqualTo("[" + machine.operatingSystem() + " %" + machine.homeDirectory() + "/ "
                            + machine.processNumber() + "]");
        }
    }
}

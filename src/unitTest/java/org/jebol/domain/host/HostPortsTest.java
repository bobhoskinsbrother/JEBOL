package org.jebol.domain.host;

import org.jebol.domain.render.Colour;
import org.jebol.domain.render.TextRun;
import org.jebol.domain.value.Raised;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HostPortsTest {

    private static final List<Function<HostPorts, Object>> EVERY_PORT = List.of(
            HostPorts::files,
            HostPorts::carriedFiles,
            HostPorts::digests,
            HostPorts::environment,
            HostPorts::images,
            HostPorts::console,
            HostPorts::windows,
            HostPorts::screen,
            HostPorts::pause,
            HostPorts::processes,
            HostPorts::machine,
            HostPorts::network,
            HostPorts::certificateAuthority,
            HostPorts::http,
            HostPorts::clipboard,
            HostPorts::midiDevices,
            HostPorts::tasks);

    private static final List<String> EVERY_PORT_NAME = List.of(
            "files", "carriedFiles", "digests", "environment", "images", "console", "windows",
            "screen", "pause", "processes", "machine", "network", "certificateAuthority", "http",
            "clipboard", "midiDevices", "tasks");

    @Test
    @DisplayName("an interpreter given no ports reaches nothing through any of them")
    void everyPortRefusesByDefault() {
        HostPorts none = HostPorts.none();

        assertThatThrownBy(() -> none.files().readBytes("/anything"))
                .isInstanceOfSatisfying(FilePort.Denied.class,
                        refused -> assertThat(refused.errorId()).isEqualTo("no-port"));
        assertThatThrownBy(() -> none.carriedFiles().readBytes("/anything"))
                .isInstanceOfSatisfying(FilePort.Denied.class,
                        refused -> assertThat(refused.errorId()).isEqualTo("no-port"));
        assertThatThrownBy(() -> none.digests().methods())
                .isInstanceOfSatisfying(DigestPort.Refused.class,
                        refused -> assertThat(refused.errorId()).isEqualTo("no-port"));
        assertThatThrownBy(() -> none.environment().valueOf("anything"))
                .isInstanceOfSatisfying(FilePort.Denied.class,
                        refused -> assertThat(refused.errorId()).isEqualTo("no-port"));
        assertThatThrownBy(() -> none.images().hasImageType("png"))
                .isInstanceOf(Raised.class);
        assertThatThrownBy(() -> none.console().readLine())
                .isInstanceOfSatisfying(FilePort.Denied.class,
                        refused -> assertThat(refused.errorId()).isEqualTo("no-port"));
        assertThatThrownBy(() -> none.windows().browse("anything"))
                .isInstanceOf(WindowPort.Denied.class);
        assertThat(none.screen().hasDisplay()).isFalse();
        assertThatThrownBy(() -> none.screen().textMeasurement()
                .measureTheExtentOf(aRunOfText()))
                .isInstanceOf(ScreenPort.Denied.class);
        assertThatThrownBy(() -> none.pause().pauseFor(1))
                .isInstanceOfSatisfying(FilePort.Denied.class,
                        refused -> assertThat(refused.errorId()).isEqualTo("no-port"));
        assertThatThrownBy(() -> none.processes().isRunning(1))
                .isInstanceOfSatisfying(FilePort.Denied.class,
                        refused -> assertThat(refused.errorId()).isEqualTo("no-port"));
        assertThatThrownBy(() -> none.machine().operatingSystem())
                .isInstanceOfSatisfying(FilePort.Denied.class,
                        refused -> assertThat(refused.errorId()).isEqualTo("no-port"));
        assertThatThrownBy(() -> none.network().connectTo("example.com", 80))
                .isInstanceOf(NetworkPort.Refused.class);
        assertThat(none.certificateAuthority().judge(List.of()))
                .isInstanceOfSatisfying(ChainVerdict.Mistrusted.class,
                        mistrusted -> assertThat(mistrusted.why())
                                .isEqualTo(CertificateMistrust.UNKNOWN_AUTHORITY));
        assertThat(none.http()).isEmpty();
        assertThatThrownBy(() -> none.clipboard().read())
                .isInstanceOf(ClipboardPort.Unreachable.class);
        assertThat(none.midiDevices().inputs()).isEmpty();
        assertThat(none.midiDevices().outputs()).isEmpty();
        assertThatThrownBy(() -> none.midiDevices().connect(
                new MidiRoute(Optional.of(new MidiDevice(1)), Optional.empty())))
                .isInstanceOf(MidiDevices.Refused.class);

        StringBuilder written = new StringBuilder();
        none.tasks().runBody("print \"never\"", written::append);
        assertThat(written).isEmpty();
    }

    @Test
    @DisplayName("each wither replaces exactly the one port it names, with the port it was given, leaving the original alone")
    void eachWitherReplacesOnePort() {
        HostPorts none = HostPorts.none();
        FilePort files = FilePort.none();
        FilePort carriedFiles = FilePort.none();
        DigestPort digests = DigestPort.none();
        EnvironmentPort environment = EnvironmentPort.none();
        ImagePort images = ImagePort.none();
        ConsolePort console = ConsolePort.none();
        WindowPort windows = WindowPort.none();
        ScreenPort screen = ScreenPort.none();
        PausePort pause = milliseconds -> true;
        ProcessPort processes = program -> {
            throw new UnsupportedOperationException("no program is started here");
        };
        MachinePort machine = MachinePort.none();
        NetworkPort network = NetworkPort.none();
        CertificateAuthority authority = new ZeroTrustAuthority();
        HttpPort http = HttpPort.none();
        ClipboardPort clipboard = ClipboardPort.none();
        MidiDevices midiDevices = new NoMidiDevices();
        TaskPort tasks = (bodyAsWritten, output) -> {
        };

        List<Replacement> everyReplacement = List.of(
                new Replacement("files", files, none.withFiles(files)),
                new Replacement("carriedFiles", carriedFiles,
                        none.withCarriedFiles(carriedFiles)),
                new Replacement("digests", digests, none.withDigests(digests)),
                new Replacement("environment", environment, none.withEnvironment(environment)),
                new Replacement("images", images, none.withImages(images)),
                new Replacement("console", console, none.withConsole(console)),
                new Replacement("windows", windows, none.withWindows(windows)),
                new Replacement("screen", screen, none.withScreen(screen)),
                new Replacement("pause", pause, none.withPause(pause)),
                new Replacement("processes", processes, none.withProcesses(processes)),
                new Replacement("machine", machine, none.withMachine(machine)),
                new Replacement("network", network, none.withNetwork(network)),
                new Replacement("certificateAuthority", authority,
                        none.withCertificateAuthority(authority)),
                new Replacement("http", Optional.of(http), none.withHttp(http)),
                new Replacement("clipboard", clipboard, none.withClipboard(clipboard)),
                new Replacement("midiDevices", midiDevices, none.withMidiDevices(midiDevices)),
                new Replacement("tasks", tasks, none.withTasks(tasks)));

        for (Replacement replacement : everyReplacement) {
            List<String> replaced = new ArrayList<>();
            for (int at = 0; at < EVERY_PORT.size(); at++) {
                if (EVERY_PORT.get(at).apply(replacement.changed())
                        != EVERY_PORT.get(at).apply(none)) {
                    replaced.add(EVERY_PORT_NAME.get(at));
                }
            }

            assertThat(replaced)
                    .as("the ports %s replaced", replacement.name())
                    .containsExactly(replacement.name());
            assertThat(EVERY_PORT.get(EVERY_PORT_NAME.indexOf(replacement.name()))
                    .apply(replacement.changed()))
                    .as("the port %s answers with", replacement.name())
                    .isEqualTo(replacement.asAnswered());
        }
    }

    @Test
    @DisplayName("a null port is refused where it is supplied, naming the port")
    void refusesANullPort() {
        assertThatThrownBy(() -> HostPorts.none().withFiles(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("files");
        assertThatThrownBy(() -> HostPorts.none().withCarriedFiles(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("carried files");
        assertThatThrownBy(() -> HostPorts.none().withDigests(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digests");
    }

    private static TextRun aRunOfText() {
        return new TextRun("text", Colour.BLACK, 12, false, false);
    }

    private record Replacement(String name, Object asAnswered, HostPorts changed) {
    }
}

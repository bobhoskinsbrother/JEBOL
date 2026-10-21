package org.jebol.domain.host;

import java.util.Optional;

public record HostPorts(
        FilePort files,
        EnvironmentPort environment,
        ImagePort images,
        ConsolePort console,
        WindowPort windows,
        ScreenPort screen,
        PausePort pause,
        ProcessPort processes,
        MachinePort machine,
        NetworkPort network,
        CertificateAuthority certificateAuthority,
        Optional<HttpPort> http,
        ClipboardPort clipboard,
        MidiDevices midiDevices,
        TaskPort tasks) {

    public HostPorts {
        files = aPort(files, "files");
        environment = aPort(environment, "environment");
        images = aPort(images, "images");
        console = aPort(console, "console");
        windows = aPort(windows, "windows");
        screen = aPort(screen, "screen");
        pause = aPort(pause, "pause");
        processes = aPort(processes, "processes");
        machine = aPort(machine, "machine");
        network = aPort(network, "network");
        certificateAuthority = aPort(certificateAuthority, "certificate authority");
        http = aPort(http, "http");
        clipboard = aPort(clipboard, "clipboard");
        midiDevices = aPort(midiDevices, "midi devices");
        tasks = aPort(tasks, "tasks");
    }

    public static HostPorts none() {
        return new HostPorts(
                FilePort.none(),
                EnvironmentPort.none(),
                ImagePort.none(),
                ConsolePort.none(),
                WindowPort.none(),
                ScreenPort.none(),
                PausePort.none(),
                ProcessPort.none(),
                MachinePort.none(),
                NetworkPort.none(),
                new ZeroTrustAuthority(),
                Optional.empty(),
                ClipboardPort.none(),
                new NoMidiDevices(),
                TaskPort.none());
    }

    public HostPorts withFiles(FilePort port) {
        return new HostPorts(port, environment, images, console, windows, screen, pause,
                processes, machine, network, certificateAuthority, http, clipboard,
                midiDevices, tasks);
    }

    public HostPorts withEnvironment(EnvironmentPort port) {
        return new HostPorts(files, port, images, console, windows, screen, pause,
                processes, machine, network, certificateAuthority, http, clipboard,
                midiDevices, tasks);
    }

    public HostPorts withImages(ImagePort port) {
        return new HostPorts(files, environment, port, console, windows, screen, pause,
                processes, machine, network, certificateAuthority, http, clipboard,
                midiDevices, tasks);
    }

    public HostPorts withConsole(ConsolePort port) {
        return new HostPorts(files, environment, images, port, windows, screen, pause,
                processes, machine, network, certificateAuthority, http, clipboard,
                midiDevices, tasks);
    }

    public HostPorts withWindows(WindowPort port) {
        return new HostPorts(files, environment, images, console, port, screen, pause,
                processes, machine, network, certificateAuthority, http, clipboard,
                midiDevices, tasks);
    }

    public HostPorts withScreen(ScreenPort port) {
        return new HostPorts(files, environment, images, console, windows, port, pause,
                processes, machine, network, certificateAuthority, http, clipboard,
                midiDevices, tasks);
    }

    public HostPorts withPause(PausePort port) {
        return new HostPorts(files, environment, images, console, windows, screen, port,
                processes, machine, network, certificateAuthority, http, clipboard,
                midiDevices, tasks);
    }

    public HostPorts withProcesses(ProcessPort port) {
        return new HostPorts(files, environment, images, console, windows, screen, pause,
                port, machine, network, certificateAuthority, http, clipboard,
                midiDevices, tasks);
    }

    public HostPorts withMachine(MachinePort port) {
        return new HostPorts(files, environment, images, console, windows, screen, pause,
                processes, port, network, certificateAuthority, http, clipboard,
                midiDevices, tasks);
    }

    public HostPorts withNetwork(NetworkPort port) {
        return new HostPorts(files, environment, images, console, windows, screen, pause,
                processes, machine, port, certificateAuthority, http, clipboard,
                midiDevices, tasks);
    }

    public HostPorts withCertificateAuthority(CertificateAuthority authority) {
        return new HostPorts(files, environment, images, console, windows, screen, pause,
                processes, machine, network, authority, http, clipboard,
                midiDevices, tasks);
    }

    public HostPorts withHttp(HttpPort port) {
        return new HostPorts(files, environment, images, console, windows, screen, pause,
                processes, machine, network, certificateAuthority, Optional.of(port),
                clipboard, midiDevices, tasks);
    }

    public HostPorts withClipboard(ClipboardPort port) {
        return new HostPorts(files, environment, images, console, windows, screen, pause,
                processes, machine, network, certificateAuthority, http, port,
                midiDevices, tasks);
    }

    public HostPorts withMidiDevices(MidiDevices devices) {
        return new HostPorts(files, environment, images, console, windows, screen, pause,
                processes, machine, network, certificateAuthority, http, clipboard,
                devices, tasks);
    }

    public HostPorts withTasks(TaskPort port) {
        return new HostPorts(files, environment, images, console, windows, screen, pause,
                processes, machine, network, certificateAuthority, http, clipboard,
                midiDevices, port);
    }

    private static <T> T aPort(T port, String which) {
        if (port == null) {
            throw new IllegalArgumentException(
                    "the " + which + " port is a decision; supply the refusing default to offer none");
        }
        return port;
    }
}

package org.jebol.android;

import org.jebol.adapter.android.AndroidHistory;
import org.jebol.adapter.android.AndroidHost;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.application.MemoryFiles;
import org.jebol.application.OneSurfaceScreen;
import org.jebol.domain.host.HistoryEntry;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.host.ScreenEventType;

import java.util.function.Consumer;

public final class AppSession implements AutoCloseable {

    private final AndroidHistory history;
    private final OneSurfaceScreen screen;
    private final Interpreter interpreter;

    private AppSession(AndroidHistory history, OneSurfaceScreen screen, Interpreter interpreter) {
        this.history = history;
        this.screen = screen;
        this.interpreter = interpreter;
    }

    public static AppSession open(JebolView view, MemoryFiles files, OutputPort output,
            Consumer<Boolean> backWanted) {

        AndroidHistory history = new AndroidHistory(backWanted);
        HostPorts ports = new AndroidHost().ports().withFiles(files);
        OneSurfaceScreen screen = OneSurfaceScreen.showTo(view, history, ports.carriedFiles());
        view.reportTo(screen);
        Interpreter interpreter = Interpreter.createOn(ports.withScreen(screen), output, appBounds());
        return new AppSession(history, screen, interpreter);
    }

    static Bounds appBounds() {
        return Bounds.standard()
                .withoutTimeLimit()
                .grant(HostService.FILES)
                .grant(HostService.WORKING_DIRECTORY)
                .grant(HostService.CLOCK)
                .grant(HostService.WINDOWS)
                .grant(HostService.NAVIGATION)
                .markUnavailable(HostService.CLIPBOARD)
                .markUnavailable(HostService.MIDI)
                .markUnavailable(HostService.WEB)
                .markUnavailable(HostService.NETWORK)
                .markUnavailable(HostService.PROCESSES)
                .markUnavailable(HostService.ENVIRONMENT)
                .markUnavailable(HostService.CERTIFICATES)
                .calls("an Android app");
    }

    public void resumeAt(String address) {
        screen.navigation().reportStart(0, address);
        history.replace(new HistoryEntry(0, address));
    }

    public void followOperatorBack() {
        history.followOperatorBack();
        screen.navigation().reportMoveRequest(ScreenEventType.BACK);
    }

    public Interpreter interpreter() {
        return interpreter;
    }

    public OneSurfaceScreen screen() {
        return screen;
    }

    public AndroidHistory history() {
        return history;
    }

    @Override
    public void close() {
        interpreter.cancel();
    }
}

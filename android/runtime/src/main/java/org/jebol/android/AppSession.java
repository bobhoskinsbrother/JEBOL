package org.jebol.android;

import android.content.Context;

import org.jebol.adapter.android.ActivityRequests;
import org.jebol.adapter.android.AndroidHistory;
import org.jebol.adapter.android.AndroidHost;
import org.jebol.adapter.android.BrowsedFiles;
import org.jebol.adapter.host.ExtensionDescriptors;
import org.jebol.adapter.host.JavaCertificateAuthority;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.application.OneSurfaceScreen;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.HistoryEntry;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.host.ScreenEventType;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BooleanSupplier;
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

    public static AppSession open(JebolView view, FilePort carriedApp, OutputPort output,
            Consumer<Boolean> backWanted, BooleanSupplier inFront) {

        return open(view, carriedApp, output, backWanted, inFront, Optional.empty(), Optional.empty());
    }

    public static AppSession open(JebolView view, FilePort carriedApp, OutputPort output,
            Consumer<Boolean> backWanted, BooleanSupplier inFront, Optional<Path> rootsForThisLaunch,
            Optional<ActivityRequests> requests) {

        Context context = view.getContext().getApplicationContext();
        AndroidHost.emptyTemporaryFolder(context);
        BrowsedFiles.forgetAll(context);
        AndroidHistory history = new AndroidHistory(backWanted);
        Bounds bounds = appBounds();
        HostPorts given = AndroidHost.appPorts(context, carriedApp, inFront, bounds, requests);
        HostPorts ports = rootsForThisLaunch
                .map(roots -> given.withCertificateAuthority(JavaCertificateAuthority.trustingMachineAnd(roots)))
                .orElse(given);
        OneSurfaceScreen screen = OneSurfaceScreen.showTo(view, history, ports.carriedFiles());
        view.reportTo(screen);
        Interpreter interpreter = Interpreter.createOn(ports.withScreen(screen), output, bounds);
        return new AppSession(history, screen, interpreter);
    }

    public static Bounds appBounds() {
        Bounds withExtensions = new ExtensionDescriptors(AppSession.class.getClassLoader(), ExtensionDescriptors.ANDROID_SETUP)
                .addSetupsTo(Bounds.standard());
        return withExtensions
                .withoutTimeLimit()
                .withMemoryCeiling(AndroidHost.memoryCeiling())
                .grant(HostService.FILES)
                .grant(HostService.WORKING_DIRECTORY)
                .grant(HostService.ENVIRONMENT)
                .grant(HostService.CLOCK)
                .grant(HostService.WINDOWS)
                .grant(HostService.NAVIGATION)
                .grant(HostService.CLIPBOARD)
                .grant(HostService.NETWORK)
                .grant(HostService.CERTIFICATES)
                .markUnavailable(HostService.MIDI)
                .markUnavailable(HostService.WEB)
                .markUnavailable(HostService.PROCESSES)
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

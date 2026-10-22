package org.jebol.adapter.cli;

import org.jebol.adapter.host.DesktopScreen;
import org.jebol.adapter.host.DesktopWindows;
import org.jebol.adapter.web.BrowserScreen;
import org.jebol.adapter.web.PageWindows;
import org.jebol.adapter.web.PickedFiles;
import org.jebol.adapter.web.WebScreenServer;
import org.jebol.domain.host.HostPorts;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

final class ChosenScreen {

    private static final String SWITCH = "--screen=";
    private static final String WINDOW = "window";
    private static final String PAGE = "web";

    private static final int ANY_AVAILABLE_PORT = 0;

    private static final String HEADLESS_PROPERTY = "java.awt.headless";

    private static final long BROWSER_POLLING_WAIT_MILLISECONDS = 50;

    private static final String PICK_FOLDER = "picked/";

    private final String[] arguments;

    ChosenScreen(String[] arguments) {
        this.arguments = arguments.clone();
    }

    void chooseHeadlessMode() {
        System.setProperty(HEADLESS_PROPERTY, Boolean.toString(!isWindow()));
    }

    boolean isAskedFor() {
        return switchValue().isPresent();
    }

    String[] withoutSwitch() {
        return Arrays.stream(arguments)
                .filter(each -> !each.startsWith(SWITCH))
                .toArray(String[]::new);
    }

    HostPorts attachTo(HostPorts ports, PrintStream out, boolean forScript) {
        String asked = switchValue().orElseThrow();
        return switch (asked.toLowerCase(Locale.ROOT)) {
            case WINDOW -> ports
                    .withScreen(windowScreen(out, ports))
                    .withWindows(DesktopWindows.onThisMachine());
            case PAGE -> attachPage(ports, out, forScript);
            default -> throw new IllegalArgumentException(
                    "there is no screen called " + asked + "; it is " + WINDOW + " or " + PAGE);
        };
    }

    private boolean isWindow() {
        return switchValue().filter(WINDOW::equalsIgnoreCase).isPresent();
    }

    private DesktopScreen windowScreen(PrintStream out, HostPorts ports) {
        DesktopScreen screen = DesktopScreen.onThisMachine(ports.carriedFiles());
        out.println(screen.hasDisplay()
                ? "Screen: a native window."
                : "Screen: asked for a window and this machine has no display.");
        return screen;
    }

    private HostPorts attachPage(HostPorts ports, PrintStream out, boolean forScript) {
        try {
            WebScreenServer serving = WebScreenServer.on(ANY_AVAILABLE_PORT, ports.carriedFiles());
            BrowserScreen screen = BrowserScreen.showTo(serving, ports.carriedFiles());
            serving.reportTo(screen);
            PickedFiles picked = new PickedFiles(ports.files(), PICK_FOLDER, serving);
            if (forScript) {
                out.println("Screen: a page at " + serving.address()
                        + " -- the script starts once it is open.");
                waitForBrowser(screen);
            } else {
                out.println("Screen: a page at " + serving.address() + " -- open it, then VIEW.");
            }
            return ports.withScreen(screen)
                    .withFiles(picked)
                    .withWindows(new PageWindows(serving, picked));
        } catch (IOException couldNotListen) {
            throw new IllegalStateException(
                    "the page could not be served: " + couldNotListen.getMessage(), couldNotListen);
        }
    }

    private void waitForBrowser(BrowserScreen screen) {
        while (!screen.hasDisplay()) {
            try {
                Thread.sleep(BROWSER_POLLING_WAIT_MILLISECONDS);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private Optional<String> switchValue() {
        for (String each : arguments) {
            if (each.startsWith(SWITCH)) {
                return Optional.of(each.substring(SWITCH.length()));
            }
        }
        return Optional.empty();
    }
}

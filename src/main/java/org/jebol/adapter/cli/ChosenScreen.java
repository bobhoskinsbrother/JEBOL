package org.jebol.adapter.cli;

import org.jebol.adapter.host.DesktopScreen;
import org.jebol.adapter.web.BrowserScreen;
import org.jebol.adapter.web.WebScreenServer;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.ScreenPort;

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

    private ChosenScreen() {
    }

    static void setHeadlessBasedOnWhetherScreenIsNeeded(String[] arguments) {
        boolean isWindow = switchValue(arguments)
                .filter(WINDOW::equalsIgnoreCase)
                .isPresent();
        System.setProperty(HEADLESS_PROPERTY, Boolean.toString(!isWindow));
    }

    static boolean isAskedFor(String[] arguments) {
        return switchValue(arguments).isPresent();
    }

    static String[] withoutSwitch(String[] arguments) {
        return Arrays.stream(arguments)
                .filter(each -> !each.startsWith(SWITCH))
                .toArray(String[]::new);
    }

    static ScreenPort screenFor(
            String[] arguments, PrintStream out, boolean forAScript, FilePort files) {
        String asked = switchValue(arguments).orElseThrow();
        return switch (asked.toLowerCase(Locale.ROOT)) {
            case WINDOW -> windowScreen(out, files);
            case PAGE -> pageScreen(out, forAScript, files);
            default -> refuseAsNotUnderstood(asked);
        };
    }

    private static ScreenPort windowScreen(PrintStream out, FilePort files) {
        DesktopScreen screen = DesktopScreen.onThisMachine(files);
        out.println(screen.hasDisplay()
                ? "Screen: a native window."
                : "Screen: asked for a window and this machine has no display.");
        return screen;
    }

    private static ScreenPort pageScreen(PrintStream out, boolean forAScript, FilePort files) {
        try {
            WebScreenServer serving = WebScreenServer.on(ANY_AVAILABLE_PORT, files);
            BrowserScreen screen = BrowserScreen.showTo(serving, files);
            serving.reportTo(screen);
            if (forAScript) {
                out.println("Screen: a page at " + serving.address()
                        + " -- the script starts once it is open.");
                waitForBrowser(screen);
                return screen;
            }
            out.println("Screen: a page at " + serving.address()
                    + " -- open it, then VIEW.");
            return screen;
        } catch (IOException couldNotListen) {
            throw new IllegalStateException(
                    "the page could not be served: " + couldNotListen.getMessage(),
                    couldNotListen);
        }
    }

    private static void waitForBrowser(BrowserScreen screen) {
        while (!screen.hasDisplay()) {
            try {
                Thread.sleep(BROWSER_POLLING_WAIT_MILLISECONDS);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static ScreenPort refuseAsNotUnderstood(String asked) {
        throw new IllegalArgumentException(
                "there is no screen called " + asked
                        + "; it is " + WINDOW + " or " + PAGE);
    }

    private static Optional<String> switchValue(String[] arguments) {
        for (String each : arguments) {
            if (each.startsWith(SWITCH)) {
                return Optional.of(each.substring(SWITCH.length()));
            }
        }
        return Optional.empty();
    }
}

package org.jebol.adapter.cli;

import org.jebol.adapter.host.DesktopScreen;
import org.jebol.adapter.web.BrowserScreen;
import org.jebol.adapter.web.WebScreenServer;
import org.jebol.application.Interpreter;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

final class ChosenScreen {

    private static final String SWITCH = "--screen=";
    private static final String A_WINDOW = "window";
    private static final String A_PAGE = "web";

    private static final int WHICHEVER_PORT_IS_FREE = 0;

    private ChosenScreen() {
    }

    static boolean wasAskedFor(String[] arguments) {
        return theSwitchValueIn(arguments).isPresent();
    }

    static String[] withoutTheSwitch(String[] arguments) {
        return Arrays.stream(arguments)
                .filter(each -> !each.startsWith(SWITCH))
                .toArray(String[]::new);
    }

    static void attachTo(Interpreter interpreter, String[] arguments, PrintStream out) {
        String asked = theSwitchValueIn(arguments).orElseThrow();
        switch (asked.toLowerCase(Locale.ROOT)) {
            case A_WINDOW -> attachAWindow(interpreter, out);
            case A_PAGE -> attachAPage(interpreter, out);
            default -> refuseRatherThanStartWithoutTheScreenAsked(asked);
        }
    }

    private static void attachAWindow(Interpreter interpreter, PrintStream out) {
        DesktopScreen screen = DesktopScreen.onThisMachine();
        interpreter.useScreen(screen);
        out.println(screen.hasADisplay()
                ? "Screen: a native window."
                : "Screen: asked for a window and this machine has no display.");
    }

    private static void attachAPage(Interpreter interpreter, PrintStream out) {
        try {
            WebScreenServer serving = WebScreenServer.on(WHICHEVER_PORT_IS_FREE);
            BrowserScreen screen = BrowserScreen.seenBy(serving);
            serving.reportTo(screen);
            interpreter.useScreen(screen);
            out.println("Screen: a page at " + serving.address()
                    + " -- open it, then VIEW.");
        } catch (IOException couldNotListen) {
            throw new IllegalStateException(
                    "the page could not be served: " + couldNotListen.getMessage(),
                    couldNotListen);
        }
    }

    private static void refuseRatherThanStartWithoutTheScreenAsked(String asked) {
        throw new IllegalArgumentException(
                "there is no screen called " + asked
                        + "; it is " + A_WINDOW + " or " + A_PAGE);
    }

    private static Optional<String> theSwitchValueIn(String[] arguments) {
        for (String each : arguments) {
            if (each.startsWith(SWITCH)) {
                return Optional.of(each.substring(SWITCH.length()));
            }
        }
        return Optional.empty();
    }
}

package org.jebol.adapter.cli;

import org.jebol.adapter.host.DesktopScreen;
import org.jebol.adapter.web.BrowserScreen;
import org.jebol.adapter.web.WebScreenServer;
import org.jebol.application.Interpreter;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Locale;
import java.util.Optional;

/**
 * Which renderer a script gets, read off the command line.
 *
 * <p>{@code --screen=window} puts it in a native window and
 * {@code --screen=web} puts it on a page. The REBOL is identical either way,
 * which is the point of having a port at all: the person starting the
 * interpreter chooses, and a script is portable because it never finds out.
 *
 * <p>There is deliberately no way for a script to choose. A script that could
 * would be a script that behaves differently depending on where it runs, which
 * is the thing the port exists to prevent.
 *
 * <p>Saying nothing gets no screen, which is not the same as getting a broken
 * one: the library still loads, {@code system/view/screen-gob} still exists,
 * and VIEW refuses by saying the screen is not present. A console session that
 * never mentions graphics is unaffected.
 */
final class ChosenScreen {

    private static final String SWITCH = "--screen=";
    private static final String A_WINDOW = "window";
    private static final String A_PAGE = "web";

    /** Any free port, which is what the operating system picks for zero. */
    private static final int WHICHEVER_PORT_IS_FREE = 0;

    private ChosenScreen() {
    }

    /** Whether these arguments ask for a screen at all. */
    static boolean wasAskedFor(String[] arguments) {
        return named(arguments).isPresent();
    }

    /** The arguments with the screen switch taken out, for the rest to read. */
    static String[] withoutTheSwitch(String[] arguments) {
        return java.util.Arrays.stream(arguments)
                .filter(each -> !each.startsWith(SWITCH))
                .toArray(String[]::new);
    }

    /**
     * Attaches whichever screen was asked for, and says where to look at it.
     *
     * <p>A name nobody serves is refused here rather than silently ignored.
     * Starting without the screen somebody asked for looks like a working
     * session until the first VIEW does nothing.
     */
    static void attachTo(Interpreter interpreter, String[] arguments, PrintStream out) {
        String asked = named(arguments).orElseThrow();
        switch (asked.toLowerCase(Locale.ROOT)) {
            case A_WINDOW -> attachAWindow(interpreter, out);
            case A_PAGE -> attachAPage(interpreter, out);
            default -> refuse(asked);
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

    private static void refuse(String asked) {
        throw new IllegalArgumentException(
                "there is no screen called " + asked
                        + "; it is " + A_WINDOW + " or " + A_PAGE);
    }

    private static Optional<String> named(String[] arguments) {
        for (String each : arguments) {
            if (each.startsWith(SWITCH)) {
                return Optional.of(each.substring(SWITCH.length()));
            }
        }
        return Optional.empty();
    }
}

package org.jebol.adapter.web;

import org.jebol.domain.host.ConsoleMode;
import org.jebol.domain.host.ConsolePort;
import org.jebol.domain.host.FilePort;

public final class WebConsole implements ConsolePort {

    private boolean printsToErrors;

    @Override
    public String readLine() {
        throw new FilePort.Denied("no-port",
                "this page types its lines into a form on its screen, and no page is attached to show it");
    }

    @Override
    public String readHiddenLine() {
        throw new FilePort.Denied("no-port",
                "this page has no way to read a line without showing it");
    }

    @Override
    public boolean isTypedOnScreen() {
        return true;
    }

    @Override
    public void switchMode(ConsoleMode mode, boolean on) {
        if (mode == ConsoleMode.ERROR) {
            printsToErrors = on;
        }
    }

    public boolean printsToErrors() {
        return printsToErrors;
    }
}

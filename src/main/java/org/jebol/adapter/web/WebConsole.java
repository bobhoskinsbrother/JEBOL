package org.jebol.adapter.web;

import org.jebol.domain.host.ConsolePort;
import org.jebol.domain.host.FilePort;

public final class WebConsole implements ConsolePort {

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
}

package org.jebol.application;

import java.io.File;

public final class TheShell {

    private static final char WINDOWS_SEPARATOR = '\\';

    private TheShell() {
    }

    public static String echo(String variable) {
        return File.separatorChar == WINDOWS_SEPARATOR
                ? "echo %" + variable + "%"
                : "echo $" + variable;
    }
}

package org.jebol.testing;

public enum Needs {

    CHROME_WITH_EXNREF("the web build's WebAssembly needs exnref, which Chrome has from 137");

    private final String reason;

    Needs(String reason) {
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}

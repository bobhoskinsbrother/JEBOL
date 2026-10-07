package org.jebol.domain.eval;

public final class LocalFileSeparator {

    private char separator = '/';

    public char separator() {
        return separator;
    }

    public void use(char chosen) {
        this.separator = chosen;
    }
}

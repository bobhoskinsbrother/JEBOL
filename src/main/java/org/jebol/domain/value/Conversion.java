package org.jebol.domain.value;

public enum Conversion {
    MAKE, TO;

    public boolean builds() {
        return this == MAKE;
    }
}

package org.jebol.domain.value;

/** The catalogue groups R3-Alpha sorts errors into. */
public enum ErrorCategory {
    SYNTAX("syntax"),
    SCRIPT("script"),
    MATH("math"),
    ACCESS("access"),
    USER("user"),
    INTERNAL("internal");

    private final String spelling;

    ErrorCategory(String spelling) {
        this.spelling = spelling;
    }

    public String spelling() {
        return spelling;
    }
}

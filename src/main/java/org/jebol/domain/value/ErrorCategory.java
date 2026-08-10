package org.jebol.domain.value;

/** The catalogue groups R3-Alpha sorts errors into. */
public enum ErrorCategory {
    SYNTAX("syntax"),
    SCRIPT("script"),
    MATH("math"),
    ACCESS("access"),
    USER("user"),
    INTERNAL("internal"),
    /**
     * What a control-flow signal looks like once TRY/ALL has made it a
     * value. Nothing raises one of these directly.
     */
    THROW("throw");

    /** The category a spelling names, ignoring case. */
    public static java.util.Optional<ErrorCategory> named(String spelling) {
        for (ErrorCategory category : values()) {
            if (category.spelling.equalsIgnoreCase(spelling)) {
                return java.util.Optional.of(category);
            }
        }
        return java.util.Optional.empty();
    }

    private final String spelling;

    ErrorCategory(String spelling) {
        this.spelling = spelling;
    }

    public String spelling() {
        return spelling;
    }
}

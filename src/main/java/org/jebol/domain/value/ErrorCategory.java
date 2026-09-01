package org.jebol.domain.value;

import java.util.Optional;

/** The catalogue groups R3-Alpha sorts errors into. */
public enum ErrorCategory {
    /**
     * Not a failure so much as a remark: a script that exited, a file that
     * would not load, a function nobody should call any more. Nothing in
     * JEBOL raises one, and {@code make error! [type: 'Note id: 'exited]}
     * still has to build one, because a script is free to name any category
     * the catalogue has.
     */
    NOTE("note"),
    SYNTAX("syntax"),
    SCRIPT("script"),
    MATH("math"),
    ACCESS("access"),
    /** What a native extension fails with, and JEBOL has no extensions. */
    COMMAND("command"),
    USER("user"),
    INTERNAL("internal"),
    /**
     * What a control-flow signal looks like once TRY/ALL has made it a
     * value. Nothing raises one of these directly.
     */
    THROW("throw");

    /** The category a spelling names, ignoring case. */
    public static Optional<ErrorCategory> named(String spelling) {
        for (ErrorCategory category : values()) {
            if (category.spelling.equalsIgnoreCase(spelling)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }

    private final String spelling;

    ErrorCategory(String spelling) {
        this.spelling = spelling;
    }

    public String spelling() {
        return spelling;
    }
}

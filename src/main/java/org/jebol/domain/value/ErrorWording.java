package org.jebol.domain.value;

import java.util.Locale;
import java.util.Optional;

public record ErrorWording(Context system) {

    public static final String NOTHING_IN_THE_CATALOGUE =
            "(improperly formatted error)";

    public static ErrorWording none() {
        return new ErrorWording(Context.unbound());
    }

    public Optional<Value> forTheId(String category, String errorId) {
        Value said = system.valueAt("system", "catalog", "errors",
                category.toLowerCase(Locale.ROOT), errorId.toLowerCase(Locale.ROOT));
        return said instanceof NoneValue ? Optional.empty() : Optional.of(said);
    }
}

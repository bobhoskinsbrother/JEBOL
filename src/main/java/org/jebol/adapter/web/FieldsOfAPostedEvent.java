package org.jebol.adapter.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class FieldsOfAPostedEvent {

    private static final String A_QUOTE = String.valueOf('"');

    private final Map<String, String> asPosted;

    FieldsOfAPostedEvent(String posted) {
        this.asPosted = Map.copyOf(theFieldsOf(posted.trim()));
    }

    private Map<String, String> theFieldsOf(String body) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (!body.startsWith("{") || !body.endsWith("}")) {
            return fields;
        }
        for (String pair : body.substring(1, body.length() - 1).split(",")) {
            int colon = pair.indexOf(':');
            if (colon >= 0) {
                fields.put(withoutQuotes(pair.substring(0, colon).trim()), pair.substring(colon + 1).trim());
            }
        }
        return fields;
    }

    boolean saysNothing() {
        return asPosted.isEmpty();
    }

    Optional<String> text(String field) {
        return Optional.ofNullable(asPosted.get(field))
                .filter(this::isQuoted)
                .map(this::withoutQuotes);
    }

    Optional<Integer> wholeNumber(String field) {
        String posted = asPosted.get(field);
        if (posted == null || isQuoted(posted)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(posted));
        } catch (NumberFormatException notAWholeNumber) {
            return Optional.empty();
        }
    }

    private boolean isQuoted(String posted) {
        return posted.length() >= 2 && posted.startsWith(A_QUOTE) && posted.endsWith(A_QUOTE);
    }

    private String withoutQuotes(String posted) {
        return isQuoted(posted) ? posted.substring(1, posted.length() - 1) : posted;
    }
}

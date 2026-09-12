package org.jebol.adapter.web;

import java.util.LinkedHashMap;
import java.util.Map;

final class FieldsOfAPostedEvent {

    private FieldsOfAPostedEvent() {
    }

    static Map<String, String> read(String posted) {
        Map<String, String> fields = new LinkedHashMap<>();
        String body = posted.trim();
        if (!body.startsWith("{") || !body.endsWith("}")) {
            return nothingThisCanReadRatherThanRaisingOnABrowsersNonsense();
        }
        for (String pair : body.substring(1, body.length() - 1).split(",")) {
            int colon = pair.indexOf(':');
            if (colon < 0) {
                continue;
            }
            fields.put(unquoted(pair.substring(0, colon)),
                    unquoted(pair.substring(colon + 1)));
        }
        return Map.copyOf(fields);
    }

    private static Map<String, String>
            nothingThisCanReadRatherThanRaisingOnABrowsersNonsense() {

        return Map.of();
    }

    private static String unquoted(String piece) {
        String trimmed = piece.trim();
        return trimmed.length() >= 2
                        && trimmed.startsWith("\"") && trimmed.endsWith("\"")
                ? trimmed.substring(1, trimmed.length() - 1)
                : trimmed;
    }
}

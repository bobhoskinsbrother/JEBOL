package org.jebol.adapter.web;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The fields of an event a browser posted.
 *
 * <p>A reader for exactly one shape: an object whose values are strings or
 * whole numbers, which is all an event from a page ever is. It is not a JSON
 * parser and must not grow into one, because a parser is a thing with a
 * specification and this is thirty lines with a job.
 *
 * <p>What arrives here came from a browser and so from anywhere. Nothing it
 * says is trusted: an unknown field is dropped, a number that is not one
 * becomes zero, and a kind nobody serves is ignored rather than passed on.
 */
final class FieldsOfAPostedEvent {

    private FieldsOfAPostedEvent() {
    }

    /**
     * The fields, or none of them.
     *
     * <p>Malformed input answers an empty map rather than raising, because
     * the caller is an HTTP handler and a browser sending nonsense is not a
     * reason to fail a request. What it costs is that a genuine bug in the
     * page looks like silence, which is why the page sends nothing this
     * cannot read.
     */
    static Map<String, String> read(String posted) {
        Map<String, String> fields = new LinkedHashMap<>();
        String body = posted.trim();
        if (!body.startsWith("{") || !body.endsWith("}")) {
            return Map.of();
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

    private static String unquoted(String piece) {
        String trimmed = piece.trim();
        return trimmed.length() >= 2
                        && trimmed.startsWith("\"") && trimmed.endsWith("\"")
                ? trimmed.substring(1, trimmed.length() - 1)
                : trimmed;
    }
}

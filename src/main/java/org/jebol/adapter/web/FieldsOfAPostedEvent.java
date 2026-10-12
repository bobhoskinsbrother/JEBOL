package org.jebol.adapter.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class FieldsOfAPostedEvent {

    private static final char A_QUOTE = '"';

    private static final String A_QUOTE_AS_TEXT = String.valueOf(A_QUOTE);

    private final Map<String, String> asPosted;

    FieldsOfAPostedEvent(String posted) {
        this.asPosted = Map.copyOf(theFieldsOf(posted.trim()));
    }

    private static Map<String, String> theFieldsOf(String body) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (!body.startsWith("{") || !body.endsWith("}")) {
            return fields;
        }
        int at = 1;
        while (at < body.length()) {
            at = skippingWhitespaceIn(body, at);
            char here = body.charAt(at);
            if (here == '}' ) {
                break;
            }
            if (here == ',') {
                at++;
                continue;
            }
            if (here != A_QUOTE) {
                break;
            }
            int nameEnds = theEndOfTheStringAt(body, at);
            if (nameEnds < 0) {
                break;
            }
            String name = unescaped(body, at + 1, nameEnds);
            at = skippingWhitespaceIn(body, nameEnds + 1);
            if (at >= body.length() || body.charAt(at) != ':') {
                break;
            }
            at = skippingWhitespaceIn(body, at + 1);
            int valueEnds = theEndOfTheValueAt(body, at);
            if (valueEnds < 0) {
                break;
            }
            fields.put(name, body.substring(at, valueEnds));
            at = valueEnds;
        }
        return fields;
    }

    private static int skippingWhitespaceIn(String body, int at) {
        while (at < body.length() && Character.isWhitespace(body.charAt(at))) {
            at++;
        }
        return at;
    }

    private static int theEndOfTheStringAt(String body, int at) {
        for (int here = at + 1; here < body.length(); here++) {
            char seen = body.charAt(here);
            if (seen == '\\') {
                here++;
                continue;
            }
            if (seen == A_QUOTE) {
                return here;
            }
        }
        return -1;
    }

    private static int theEndOfTheValueAt(String body, int at) {
        if (at >= body.length()) {
            return -1;
        }
        char here = body.charAt(at);
        if (here == A_QUOTE) {
            int closed = theEndOfTheStringAt(body, at);
            return closed < 0 ? -1 : closed + 1;
        }
        if (here == '{' || here == '[') {
            return theEndOfTheGroupAt(body, at, here);
        }
        int end = at;
        while (end < body.length() && body.charAt(end) != ','
                && body.charAt(end) != '}' && body.charAt(end) != ']') {
            end++;
        }
        return end;
    }

    private static int theEndOfTheGroupAt(String body, int at, char opened) {
        char closed = opened == '{' ? '}' : ']';
        int depth = 0;
        for (int here = at; here < body.length(); here++) {
            char seen = body.charAt(here);
            if (seen == A_QUOTE) {
                here = theEndOfTheStringAt(body, here);
                if (here < 0) {
                    return -1;
                }
                continue;
            }
            if (seen == opened) {
                depth++;
            } else if (seen == closed && --depth == 0) {
                return here + 1;
            }
        }
        return -1;
    }

    private static String unescaped(String body, int from, int to) {
        StringBuilder read = new StringBuilder();
        for (int at = from; at < to; at++) {
            char here = body.charAt(at);
            if (here != '\\' || at + 1 >= to) {
                read.append(here);
                continue;
            }
            char escaped = body.charAt(++at);
            switch (escaped) {
                case 'n' -> read.append('\n');
                case 'r' -> read.append('\r');
                case 't' -> read.append('\t');
                case 'b' -> read.append('\b');
                case 'f' -> read.append('\f');
                case 'u' -> {
                    if (at + 4 < to) {
                        read.append((char) Integer.parseInt(
                                body.substring(at + 1, at + 5), 16));
                        at += 4;
                    }
                }
                default -> read.append(escaped);
            }
        }
        return read.toString();
    }

    boolean saysNothing() {
        return asPosted.isEmpty();
    }

    Optional<String> text(String field) {
        return Optional.ofNullable(asPosted.get(field))
                .filter(FieldsOfAPostedEvent::isQuoted)
                .map(posted -> unescaped(posted, 1, posted.length() - 1));
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

    private static boolean isQuoted(String posted) {
        return posted.length() >= 2 && posted.startsWith(A_QUOTE_AS_TEXT)
                && posted.endsWith(A_QUOTE_AS_TEXT);
    }
}

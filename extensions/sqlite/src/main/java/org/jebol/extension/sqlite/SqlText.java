package org.jebol.extension.sqlite;

import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class SqlText {

    record Parameter(int start, int end, int index) {
    }

    record Shape(boolean isEmpty, boolean isReadOnly, List<Parameter> parameters, int parameterCount) {
    }

    private static final Set<String> READING_WORDS = Set.of("SELECT", "VALUES", "EXPLAIN");
    private static final Set<String> WRITING_WORDS = Set.of("INSERT", "UPDATE", "DELETE", "REPLACE");
    private static final int SIGNIFICANT_DIGITS = 15;
    private static final char QUOTE = '\'';

    private SqlText() {
    }

    private static boolean isWordByte(int c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c >= 0x80;
    }

    private static int skipQuoted(byte[] sql, int at, byte close) {
        int next = at + 1;
        while (next < sql.length) {
            if (sql[next] == close) {
                if (close != ']' && next + 1 < sql.length && sql[next + 1] == close) {
                    next += 2;
                    continue;
                }
                return next + 1;
            }
            next++;
        }
        return sql.length;
    }

    private static int skipComment(byte[] sql, int at) {
        if (sql[at] == '-') {
            int next = at + 2;
            while (next < sql.length && sql[next] != '\n') {
                next++;
            }
            return next;
        }
        int next = at + 2;
        while (next + 1 < sql.length && !(sql[next] == '*' && sql[next + 1] == '/')) {
            next++;
        }
        return Math.min(sql.length, next + 2);
    }

    private static boolean startsComment(byte[] sql, int at) {
        return at + 1 < sql.length && ((sql[at] == '-' && sql[at + 1] == '-') || (sql[at] == '/' && sql[at + 1] == '*'));
    }

    static int endOfStatement(byte[] sql, int offset) {
        int at = offset;
        int words = 0;
        boolean createsATrigger = false;
        boolean inTheTriggersBody = false;
        boolean lastWasEnd = false;
        boolean maybeTrigger = false;
        boolean saidSomething = false;
        while (at < sql.length) {
            byte c = sql[at];
            if (!Character.isWhitespace(c) && c != ';' && !startsComment(sql, at)) {
                saidSomething = true;
            }
            if (c == '\'' || c == '"' || c == '`') {
                at = skipQuoted(sql, at, c);
                lastWasEnd = false;
            } else if (c == '[') {
                at = skipQuoted(sql, at, (byte) ']');
                lastWasEnd = false;
            } else if (startsComment(sql, at)) {
                at = skipComment(sql, at);
            } else if (c == ';') {
                if (saidSomething && (!inTheTriggersBody || lastWasEnd)) {
                    return at + 1;
                }
                at++;
            } else if (isWordByte(c)) {
                int start = at;
                while (at < sql.length && isWordByte(sql[at])) {
                    at++;
                }
                String word = new String(sql, start, at - start, StandardCharsets.UTF_8)
                        .toUpperCase(Locale.ROOT);
                words++;
                if (words == 1) {
                    maybeTrigger = word.equals("CREATE");
                } else if (maybeTrigger && words <= 3 && word.equals("TRIGGER")) {
                    createsATrigger = true;
                } else if (maybeTrigger && words == 2 && !word.equals("TEMP") && !word.equals("TEMPORARY")) {
                    maybeTrigger = false;
                }
                if (createsATrigger && word.equals("BEGIN")) {
                    inTheTriggersBody = true;
                }
                lastWasEnd = inTheTriggersBody && word.equals("END");
            } else {
                if (!Character.isWhitespace(c)) {
                    lastWasEnd = false;
                }
                at++;
            }
        }
        return sql.length;
    }

    static Shape shape(String text) {
        List<Parameter> parameters = new ArrayList<>();
        Map<String, Integer> named = new HashMap<>();
        List<String> words = new ArrayList<>();
        boolean hasEquals = false;
        boolean hasAnything = false;
        int highest = 0;
        int at = 0;
        while (at < text.length()) {
            char c = text.charAt(at);
            if (c == '\'' || c == '"' || c == '`' || c == '[') {
                at = skipQuotedText(text, at, c == '[' ? ']' : c);
                hasAnything = true;
            } else if (at + 1 < text.length() && ((c == '-' && text.charAt(at + 1) == '-')
                    || (c == '/' && text.charAt(at + 1) == '*'))) {
                at = c == '-' ? lineEnd(text, at) : blockEnd(text, at);
            } else if (c == '?') {
                int start = at++;
                while (at < text.length() && Character.isDigit(text.charAt(at))) {
                    at++;
                }
                int index = at > start + 1 ? Integer.parseInt(text.substring(start + 1, at)) : highest + 1;
                highest = Math.max(highest, index);
                parameters.add(new Parameter(start, at, index));
                hasAnything = true;
            } else if ((c == ':' || c == '@' || c == '$') && at + 1 < text.length() && isWordChar(text.charAt(at + 1))) {
                int start = at++;
                while (at < text.length() && isWordChar(text.charAt(at))) {
                    at++;
                }
                String name = text.substring(start, at);
                Integer index = named.get(name);
                if (index == null) {
                    index = highest + 1;
                    named.put(name, index);
                    highest = index;
                }
                parameters.add(new Parameter(start, at, index));
                hasAnything = true;
            } else if (isWordChar(c)) {
                int start = at;
                while (at < text.length() && isWordChar(text.charAt(at))) {
                    at++;
                }
                words.add(text.substring(start, at).toUpperCase(Locale.ROOT));
                hasAnything = true;
            } else {
                if (c == '=') {
                    hasEquals = true;
                }
                if (!Character.isWhitespace(c) && c != ';') {
                    hasAnything = true;
                }
                at++;
            }
        }
        return new Shape(!hasAnything, isReadOnly(words, hasEquals), List.copyOf(parameters), highest);
    }

    private static boolean isReadOnly(List<String> words, boolean hasEquals) {
        if (words.isEmpty()) {
            return true;
        }
        String first = words.getFirst();
        if (READING_WORDS.contains(first)) {
            return true;
        }
        if (first.equals("WITH")) {
            return words.stream().noneMatch(WRITING_WORDS::contains);
        }
        return first.equals("PRAGMA") && !hasEquals;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c >= 0x80;
    }

    private static int skipQuotedText(String text, int at, char close) {
        int next = at + 1;
        while (next < text.length()) {
            if (text.charAt(next) == close) {
                if (close != ']' && next + 1 < text.length() && text.charAt(next + 1) == close) {
                    next += 2;
                    continue;
                }
                return next + 1;
            }
            next++;
        }
        return text.length();
    }

    private static int lineEnd(String text, int at) {
        int next = text.indexOf('\n', at);
        return next < 0 ? text.length() : next;
    }

    private static int blockEnd(String text, int at) {
        int next = text.indexOf("*/", at + 2);
        return next < 0 ? text.length() : next + 2;
    }

    static String expanded(String text, Shape shape, Object[] bound) {
        StringBuilder written = new StringBuilder();
        int from = 0;
        for (Parameter parameter : shape.parameters()) {
            written.append(text, from, parameter.start());
            Object value = parameter.index() <= bound.length ? bound[parameter.index() - 1] : null;
            written.append(literal(value));
            from = parameter.end();
        }
        written.append(text.substring(from));
        return written.toString();
    }

    private static String literal(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Long whole) {
            return Long.toString(whole);
        }
        if (value instanceof Double fraction) {
            return writeDouble(fraction);
        }
        String text = value.toString();
        return QUOTE + text.replace(String.valueOf(QUOTE), String.valueOf(QUOTE) + QUOTE) + QUOTE;
    }

    static String writeDouble(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Inf" : "-Inf";
        }
        if (value == 0) {
            return "0.0";
        }
        BigDecimal rounded = new BigDecimal(value).round(new MathContext(SIGNIFICANT_DIGITS)).stripTrailingZeros();
        int exponent = rounded.precision() - rounded.scale() - 1;
        if (exponent < -4 || exponent >= SIGNIFICANT_DIGITS) {
            BigDecimal mantissa = rounded.movePointLeft(exponent);
            String digits = mantissa.toPlainString();
            if (!digits.contains(".")) {
                digits = digits + ".0";
            }
            return digits + "e" + (exponent < 0 ? "-" : "+") + String.format(Locale.ROOT, "%02d", Math.abs(exponent));
        }
        String plain = rounded.toPlainString();
        return plain.contains(".") ? plain : plain + ".0";
    }
}

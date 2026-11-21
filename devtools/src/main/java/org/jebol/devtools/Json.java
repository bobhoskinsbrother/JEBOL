package org.jebol.devtools;

import java.util.List;
import java.util.stream.Collectors;

final class Json {

    private static final char QUOTE = '"';
    private static final char BACKSLASH = '\\';

    private Json() {
    }

    static String escape(String text) {
        StringBuilder escaped = new StringBuilder();
        for (char each : text.toCharArray()) {
            if (each == QUOTE || each == BACKSLASH) {
                escaped.append(BACKSLASH).append(each);
            } else if (each < 0x20) {
                escaped.append(String.format("\\u%04x", (int) each));
            } else {
                escaped.append(each);
            }
        }
        return escaped.toString();
    }

    static String quoted(String text) {
        return QUOTE + escape(text) + QUOTE;
    }

    static String appJson(List<String> files, String entry) {
        return "{" + quoted("files") + ":" + files.stream().map(Json::quoted).collect(Collectors.joining(",", "[", "]"))
                + "," + quoted("entry") + ":" + quoted(entry) + "}";
    }
}

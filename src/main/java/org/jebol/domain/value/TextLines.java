package org.jebol.domain.value;

import java.util.ArrayList;
import java.util.List;

public final class TextLines {

    private TextLines() {
    }

    public static List<String> of(String text) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int at = 0; at < text.length(); at++) {
            char here = text.charAt(at);
            if (here != '\n' && here != '\r') {
                continue;
            }
            lines.add(text.substring(start, at));
            if (here == '\r' && at + 1 < text.length() && text.charAt(at + 1) == '\n') {
                at++;
            }
            start = at + 1;
        }
        if (start < text.length()) {
            lines.add(text.substring(start));
        }
        return lines;
    }
}

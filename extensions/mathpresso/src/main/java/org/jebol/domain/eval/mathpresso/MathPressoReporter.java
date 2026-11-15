package org.jebol.domain.eval.mathpresso;

import java.util.ArrayList;
import java.util.List;

final class MathPressoReporter {

    static final class Refused extends RuntimeException {

        private static final long serialVersionUID = 1L;

        Refused() {
            super(null, null, false, false);
        }
    }

    private final byte[] source;
    private final List<MathPressoMessage> messages = new ArrayList<>();

    MathPressoReporter(byte[] source) {
        this.source = source;
    }

    List<MathPressoMessage> messages() {
        return List.copyOf(messages);
    }

    Refused refuse(String text) {
        messages.add(new MathPressoMessage(true, text));
        return new Refused();
    }

    Refused refuseSilently() {
        return new Refused();
    }

    void warn(String text) {
        messages.add(new MathPressoMessage(false, text));
    }

    String locate(int position) {
        if (position >= source.length) {
            return "[0:0]";
        }
        int p = position;
        int column = 0;
        int line = 1;
        while (source[p] != '\n') {
            column++;
            if (p == 0) {
                break;
            }
            p--;
        }
        while (p != 0) {
            if (source[p] == '\n') {
                line++;
            }
            p--;
        }
        return "[" + line + ":" + column + "]";
    }
}

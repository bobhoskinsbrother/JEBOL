package org.jebol.domain.value;

record WrittenText(String text) {

    private static final char MOST_LETTERS_ARE_ONE_BYTE = 127;

    private static final char END_OF_FILE = 0;

    private static final char ASCII_ENDS_AT = 127;

    private static final int LONGEST_TIME_A_STRING_MAY_SPELL = 30;

    String theOneNumberIn(String reading, int mostCharacters) {
        int start = theFirstNonSpace();
        int past = start;
        while (past < text.length() && !isLexicalSpace(text.charAt(past))) {
            if (text.charAt(past) > MOST_LETTERS_ARE_ONE_BYTE) {
                throw Raised.of(EvaluationFailure.INVALID_CHARS,
                        "\"" + text + "\" holds a character a number may not");
            }
            past++;
            if (past - start > mostCharacters) {
                throw Raised.of(EvaluationFailure.TOO_LONG,
                        "\"" + text + "\" is longer than a written number may be");
            }
        }
        if (past == start) {
            throw Raised.of(EvaluationFailure.TOO_SHORT,
                    "there is nothing in \"" + text + "\" to read as " + reading);
        }
        if (anythingButSpacesAndTabsFrom(past)) {
            throw Raised.of(EvaluationFailure.INVALID_CHARS,
                    "\"" + text + "\" has more than one value in it");
        }
        return text.substring(start, past);
    }

    String theOneWordIn() {
        int from = theFirstNonSpace();
        int end = from;
        while (end < text.length() && !isLexicalSpace(text.charAt(end))) {
            end++;
        }
        String trimmed = text.substring(from, end);
        if (trimmed.isEmpty()) {
            throw Raised.of(EvaluationFailure.TOO_SHORT);
        }
        if (anythingButSpacesAndTabsFrom(end)) {
            throw Raised.of(EvaluationFailure.INVALID_CHARS);
        }
        return trimmed;
    }

    String theOneTimeIn() {
        int from = 0;
        while (from < text.length() && isSpaceOrTab(text.charAt(from))) {
            from++;
        }
        int to = from;
        while (to < text.length() && !isSpaceOrTab(text.charAt(to))) {
            if (text.charAt(to) > ASCII_ENDS_AT) {
                throw Raised.of(EvaluationFailure.INVALID_CHARS, text);
            }
            to++;
        }
        if (to == from) {
            throw Raised.of(EvaluationFailure.TOO_SHORT, text);
        }
        if (to - from > LONGEST_TIME_A_STRING_MAY_SPELL) {
            throw Raised.of(EvaluationFailure.TOO_LONG, text);
        }
        if (anythingButSpacesAndTabsFrom(to)) {
            throw Raised.of(EvaluationFailure.INVALID_CHARS, text);
        }
        return text.substring(from, to);
    }

    private int theFirstNonSpace() {
        int at = 0;
        while (at < text.length() && isLexicalSpace(text.charAt(at))) {
            at++;
        }
        return at;
    }

    private boolean anythingButSpacesAndTabsFrom(int start) {
        for (int after = start; after < text.length(); after++) {
            if (!isSpaceOrTab(text.charAt(after))) {
                return true;
            }
        }
        return false;
    }

    private boolean isLexicalSpace(char letter) {
        return (letter <= ' ' || letter == MOST_LETTERS_ARE_ONE_BYTE)
                && letter != '\n' && letter != '\r' && letter != END_OF_FILE;
    }

    private boolean isSpaceOrTab(char letter) {
        return letter == ' ' || letter == '\t';
    }
}

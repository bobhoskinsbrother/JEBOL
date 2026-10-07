package org.jebol.adapter.cli;

final class ConsoleContinuation {

    private enum Reading { CODE, ONE_LINE_STRING, MULTI_LINE_STRING, RAW_STRING }

    private static final int DEEPEST_REMEMBERED = 1024;

    private static final char DEEPER_THAN_REMEMBERED = '-';

    private static final char END_OF_LINE = 0;

    private final char[] openedBy = new char[DEEPEST_REMEMBERED];

    private int level;

    private int braceLevel;

    private int rawStringPercents;

    private Reading reading = Reading.CODE;

    boolean waitingForMore() {
        return level > 0;
    }

    char whatIsStillOpen() {
        return level <= DEEPEST_REMEMBERED ? openedBy[level - 1] : DEEPER_THAN_REMEMBERED;
    }

    void inputTaken() {
        level = 0;
        rawStringPercents = 0;
    }

    void read(String line) {
        for (int at = 0; at < line.length(); at++) {
            char each = line.charAt(at);
            switch (reading) {
                case CODE -> {
                    if (each == ';') {
                        at = line.length();
                    } else {
                        at = afterCode(line, at, each);
                    }
                }
                case ONE_LINE_STRING -> at = afterOneLineString(line, at, each);
                case MULTI_LINE_STRING -> at = afterMultiLineString(line, at, each);
                case RAW_STRING -> at = afterRawString(line, at, each);
            }
        }
        if (reading == Reading.ONE_LINE_STRING) {
            reading = Reading.CODE;
        }
    }

    private int afterCode(String line, int at, char each) {
        switch (each) {
            case '"' -> reading = Reading.ONE_LINE_STRING;
            case '[', '(' -> open(each);
            case ']', ')' -> {
                if (level > 0) {
                    level--;
                }
            }
            case '{' -> {
                open(each);
                braceLevel++;
                reading = Reading.MULTI_LINE_STRING;
            }
            case '%' -> {
                return afterAPercent(line, at);
            }
            default -> {
            }
        }
        return at;
    }

    private int afterAPercent(String line, int at) {
        if (charAt(line, at + 1) == '"') {
            return at;
        }
        int percents = percentsFrom(line, at);
        if (charAt(line, at + percents) != '{') {
            return at;
        }
        rawStringPercents = percents;
        open('{');
        reading = Reading.RAW_STRING;
        return at + percents;
    }

    private int afterOneLineString(String line, int at, char each) {
        if (each == '^' && charAt(line, at + 1) != END_OF_LINE) {
            return at + 1;
        }
        if (each == '"') {
            reading = Reading.CODE;
        }
        return at;
    }

    private int afterMultiLineString(String line, int at, char each) {
        if (each == '^' && charAt(line, at + 1) != END_OF_LINE) {
            return at + 1;
        }
        if (each == '{') {
            open(each);
            braceLevel++;
        } else if (each == '}') {
            level--;
            braceLevel--;
            if (braceLevel == 0) {
                reading = Reading.CODE;
            }
        }
        return at;
    }

    private int afterRawString(String line, int at, char each) {
        if (each != '}' || charAt(line, at + 1) != '%') {
            return at;
        }
        int closing = percentsFrom(line, at);
        if (rawStringPercents >= closing) {
            return at;
        }
        rawStringPercents = 0;
        level--;
        reading = Reading.CODE;
        return at + closing;
    }

    private int percentsFrom(String line, int at) {
        int counted = 1;
        while (charAt(line, at + counted) == '%') {
            counted++;
        }
        return counted;
    }

    private void open(char bracket) {
        if (level < DEEPEST_REMEMBERED) {
            openedBy[level] = bracket;
        }
        level++;
    }

    private char charAt(String line, int at) {
        return at < line.length() ? line.charAt(at) : END_OF_LINE;
    }
}

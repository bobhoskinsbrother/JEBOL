package org.jebol.domain.read;

import java.util.Arrays;

final class LexicalClasses {

    static final int SHIFT = 5;

    static final int VALUE_MASK = 0x1F;

    static final int CLASS_DELIMIT = 0;

    static final int CLASS_SPECIAL = 1;

    static final int CLASS_WORD = 2;

    static final int CLASS_NUMBER = 3;

    static final int DELIMIT = CLASS_DELIMIT << SHIFT;

    static final int SPECIAL = CLASS_SPECIAL << SHIFT;

    static final int WORD = CLASS_WORD << SHIFT;

    static final int NUMBER = CLASS_NUMBER << SHIFT;

    private static final int CLASS_MASK = 3 << SHIFT;

    static final int DELIMIT_SPACE = 0;

    static final int DELIMIT_END_FILE = 1;

    static final int DELIMIT_LINEFEED = 2;

    static final int DELIMIT_RETURN = 3;

    static final int DELIMIT_LEFT_PAREN = 4;

    static final int DELIMIT_RIGHT_PAREN = 5;

    static final int DELIMIT_LEFT_BRACKET = 6;

    static final int DELIMIT_RIGHT_BRACKET = 7;

    static final int DELIMIT_LEFT_BRACE = 8;

    static final int DELIMIT_RIGHT_BRACE = 9;

    static final int DELIMIT_QUOTE = 10;

    static final int DELIMIT_SLASH = 11;

    static final int DELIMIT_SEMICOLON = 12;

    static final int SPECIAL_AT = 0;

    static final int SPECIAL_PERCENT = 1;

    static final int SPECIAL_BACKSLASH = 2;

    static final int SPECIAL_COLON = 3;

    static final int SPECIAL_TICK = 4;

    static final int SPECIAL_LESSER = 5;

    static final int SPECIAL_GREATER = 6;

    static final int SPECIAL_UNDERSCORE = 7;

    static final int SPECIAL_PLUS = 8;

    static final int SPECIAL_MINUS = 9;

    static final int SPECIAL_TILDE = 10;

    static final int SPECIAL_PERIOD = 11;

    static final int SPECIAL_COMMA = 12;

    static final int SPECIAL_POUND = 13;

    static final int SPECIAL_DOLLAR = 14;

    static final int SPECIAL_WORD = 15;

    static final int WORD_FLAGS = (1 << SPECIAL_AT) | (1 << SPECIAL_PERCENT) | (1 << SPECIAL_BACKSLASH)
            | (1 << SPECIAL_COMMA) | (1 << SPECIAL_POUND) | (1 << SPECIAL_DOLLAR) | (1 << SPECIAL_COLON);

    private static final int CONTROL = DELIMIT | DELIMIT_SPACE;

    private final int[] map = new int[256];

    LexicalClasses() {
        Arrays.fill(map, WORD);
        Arrays.fill(map, 0x01, 0x20, CONTROL);
        map[0x00] = DELIMIT | DELIMIT_END_FILE;
        map['\n'] = DELIMIT | DELIMIT_LINEFEED;
        map['\r'] = DELIMIT | DELIMIT_RETURN;
        map[' '] = DELIMIT | DELIMIT_SPACE;
        map['"'] = DELIMIT | DELIMIT_QUOTE;
        map['#'] = SPECIAL | SPECIAL_POUND;
        map['$'] = SPECIAL | SPECIAL_DOLLAR;
        map['%'] = SPECIAL | SPECIAL_PERCENT;
        map['\''] = SPECIAL | SPECIAL_TICK;
        map['('] = DELIMIT | DELIMIT_LEFT_PAREN;
        map[')'] = DELIMIT | DELIMIT_RIGHT_PAREN;
        map['+'] = SPECIAL | SPECIAL_PLUS;
        map[','] = SPECIAL | SPECIAL_COMMA;
        map['-'] = SPECIAL | SPECIAL_MINUS;
        map['.'] = SPECIAL | SPECIAL_PERIOD;
        map['/'] = DELIMIT | DELIMIT_SLASH;
        for (int digit = 0; digit <= 9; digit++) {
            map['0' + digit] = NUMBER | digit;
        }
        map[':'] = SPECIAL | SPECIAL_COLON;
        map[';'] = DELIMIT | DELIMIT_SEMICOLON;
        map['<'] = SPECIAL | SPECIAL_LESSER;
        map['>'] = SPECIAL | SPECIAL_GREATER;
        map['@'] = SPECIAL | SPECIAL_AT;
        for (int letter = 0; letter < 6; letter++) {
            map['A' + letter] = WORD | (10 + letter);
            map['a' + letter] = WORD | (10 + letter);
        }
        map['['] = DELIMIT | DELIMIT_LEFT_BRACKET;
        map['\\'] = SPECIAL | SPECIAL_BACKSLASH;
        map[']'] = DELIMIT | DELIMIT_RIGHT_BRACKET;
        map['_'] = SPECIAL | SPECIAL_UNDERSCORE;
        map['{'] = DELIMIT | DELIMIT_LEFT_BRACE;
        map['}'] = DELIMIT | DELIMIT_RIGHT_BRACE;
        map[0x7F] = CONTROL;
        map[0xC0] = CONTROL;
        map[0xC1] = CONTROL;
        map[0xF5] = CONTROL;
        map[0xFF] = CONTROL;
    }

    int entry(int octet) {
        return map[octet & 0xFF];
    }

    int classOf(int octet) {
        return entry(octet) >> SHIFT;
    }

    int valueOf(int octet) {
        return entry(octet) & VALUE_MASK;
    }

    boolean isSpace(int octet) {
        return entry(octet) == 0;
    }

    boolean isAnySpace(int octet) {
        return entry(octet) <= DELIMIT_RETURN;
    }

    boolean isDelimit(int octet) {
        return (entry(octet) & CLASS_MASK) == DELIMIT;
    }

    boolean isSpecial(int octet) {
        return (entry(octet) & CLASS_MASK) == SPECIAL;
    }

    boolean isWord(int octet) {
        return (entry(octet) & CLASS_MASK) == WORD;
    }

    boolean isNumber(int octet) {
        return (entry(octet) & CLASS_MASK) == NUMBER;
    }

    boolean isAtLeastSpecial(int octet) {
        return entry(octet) >= SPECIAL;
    }

    boolean isAtLeastWord(int octet) {
        return entry(octet) >= WORD;
    }

    boolean isAtLeastNumber(int octet) {
        return entry(octet) >= NUMBER;
    }

    boolean isHexDigit(int octet) {
        int lexical = entry(octet);
        return lexical >= WORD && ((lexical & VALUE_MASK) != 0 || lexical >= NUMBER);
    }
}

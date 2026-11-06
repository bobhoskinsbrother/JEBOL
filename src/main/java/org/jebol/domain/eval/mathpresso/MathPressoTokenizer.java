package org.jebol.domain.eval.mathpresso;

import org.jebol.domain.eval.mathpresso.MathPressoToken.Type;

import java.nio.charset.StandardCharsets;

final class MathPressoTokenizer {

    private static final int LAST_DIGIT_CLASS = 9;
    private static final int SYMBOL_CLASS = 16;
    private static final int PUNCTUATION_CLASS = 17;
    private static final int SPACE_CLASS = 63;
    private static final int INVALID_CLASS = 65;

    private static final int SAFE_DIGITS = 15;
    private static final int POWERS_IN_TABLE = 16;
    private static final int MOST_SIGNIFICANT_DIGITS_KEPT_SAFE = 999999;
    private static final int LONGEST_EXPONENT_KEPT_SAFE = 6;
    private static final int EXPONENT_DIGITS_THAT_CANNOT_OVERFLOW = 10;

    private static final double[] POWERS_OF_TEN_AND_THE_CHARACTER_CLASSES_STORED_AFTER_THEM = {
            1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11, 1e12, 1e13, 1e14, 1e15,
            Double.longBitsToDouble(0x4141414141414141L)
    };

    private final byte[] source;
    private int at;
    private MathPressoToken buffered = MathPressoToken.of(Type.INVALID, 0, 0);

    MathPressoTokenizer(byte[] source) {
        this.source = source;
    }

    String textAt(MathPressoToken token) {
        return new String(source, token.position(), token.size(), StandardCharsets.ISO_8859_1);
    }

    MathPressoToken peek() {
        if (buffered.type() != Type.INVALID) {
            return buffered;
        }
        buffered = read();
        return buffered;
    }

    MathPressoToken next() {
        if (buffered.type() != Type.INVALID) {
            MathPressoToken held = buffered;
            buffered = MathPressoToken.of(Type.INVALID, held.position(), held.size());
            return held;
        }
        return read();
    }

    void putBack(MathPressoToken token) {
        at = token.position() + token.size();
        buffered = token;
    }

    void consume() {
        buffered = MathPressoToken.of(Type.INVALID, buffered.position(), buffered.size());
    }

    private int classAt(int index) {
        return classify(source[index] & 0xFF);
    }

    private static int classify(int octet) {
        if (octet >= '0' && octet <= '9') {
            return octet - '0';
        }
        if ((octet >= 'a' && octet <= 'f') || (octet >= 'A' && octet <= 'F')) {
            return 10 + ((octet | 0x20) - 'a');
        }
        if ((octet >= 'g' && octet <= 'z') || (octet >= 'G' && octet <= 'Z') || octet == '_') {
            return SYMBOL_CLASS;
        }
        if (octet == ' ' || (octet >= 0x09 && octet <= 0x0D)) {
            return SPACE_CLASS;
        }
        return punctuationTypeFor(octet) == Type.INVALID ? INVALID_CLASS : PUNCTUATION_CLASS;
    }

    private static Type punctuationTypeFor(int octet) {
        return switch (octet) {
            case '.' -> Type.DOT;
            case ',' -> Type.COMMA;
            case ';' -> Type.SEMICOLON;
            case '?' -> Type.QUESTION_MARK;
            case ':' -> Type.COLON;
            case '{' -> Type.LEFT_CURLY;
            case '}' -> Type.RIGHT_CURLY;
            case '[' -> Type.LEFT_BRACKET;
            case ']' -> Type.RIGHT_BRACKET;
            case '(' -> Type.LEFT_PAREN;
            case ')' -> Type.RIGHT_PAREN;
            case '+' -> Type.ADD;
            case '-' -> Type.SUB;
            case '*' -> Type.MUL;
            case '/' -> Type.DIV;
            case '%' -> Type.MOD;
            case '!' -> Type.NOT;
            case '&' -> Type.BIT_AND;
            case '|' -> Type.BIT_OR;
            case '^' -> Type.BIT_XOR;
            case '~' -> Type.BIT_NEG;
            case '=' -> Type.ASSIGN;
            case '<' -> Type.LT;
            case '>' -> Type.GT;
            default -> Type.INVALID;
        };
    }

    private static boolean isSingleCharacterOnly(Type type) {
        return type.ordinal() >= Type.DOT.ordinal() && type.ordinal() <= Type.RIGHT_PAREN.ordinal();
    }

    private MathPressoToken read() {
        int end = source.length;
        int p = at;
        while (true) {
            if (p == end) {
                at = end;
                return MathPressoToken.of(Type.END, p, 0);
            }
            int kind = classAt(p);
            if (kind == SPACE_CLASS) {
                p++;
                continue;
            }
            int start = p;
            if (kind <= LAST_DIGIT_CLASS || source[p] == '.') {
                return readNumber(start);
            }
            if (kind <= SYMBOL_CLASS) {
                do {
                    p++;
                } while (p != end && classAt(p) <= SYMBOL_CLASS);
                at = p;
                int size = p - start;
                Type type = size == 3 && source[start] == 'v' && source[start + 1] == 'a' && source[start + 2] == 'r'
                        ? Type.VAR : Type.SYMBOL;
                return MathPressoToken.of(type, start, size);
            }
            if (kind == INVALID_CLASS) {
                at = start;
                return MathPressoToken.of(Type.INVALID, start, 0);
            }
            Type single = punctuationTypeFor(source[p] & 0xFF);
            if (isSingleCharacterOnly(single)) {
                at = p + 1;
                return MathPressoToken.of(single, start, 1);
            }
            int second = (++p != end) ? source[p] & 0xFF : 0;
            if (single == Type.DIV && second == '/') {
                p = skipComment(p);
                continue;
            }
            Type type = single;
            switch (single) {
                case ADD -> {
                    if (second == '=') { type = Type.ASSIGN_ADD; p++; }
                    else if (second == '+') { type = Type.PLUS_PLUS; p++; }
                }
                case SUB -> {
                    if (second == '=') { type = Type.ASSIGN_SUB; p++; }
                    else if (second == '-') { type = Type.MINUS_MINUS; p++; }
                }
                case MUL -> {
                    if (second == '=') { type = Type.ASSIGN_MUL; p++; }
                }
                case DIV -> {
                    if (second == '=') { type = Type.ASSIGN_DIV; p++; }
                }
                case MOD -> {
                    if (second == '=') { type = Type.ASSIGN_MOD; p++; }
                }
                case BIT_AND -> {
                    if (second == '=') { type = Type.ASSIGN_BIT_AND; p++; }
                    else if (second == '&') { type = Type.LOGICAL_AND; p++; }
                }
                case BIT_OR -> {
                    if (second == '=') { type = Type.ASSIGN_BIT_OR; p++; }
                    else if (second == '|') { type = Type.LOGICAL_OR; p++; }
                }
                case BIT_XOR -> {
                    if (second == '=') { type = Type.ASSIGN_BIT_XOR; p++; }
                }
                case NOT -> {
                    if (second == '=') { type = Type.NE; p++; }
                }
                case ASSIGN -> {
                    if (second == '=') { type = Type.EQ; p++; }
                }
                case LT -> {
                    if (second == '<') {
                        if (++p != end && source[p] == '=') { type = Type.ASSIGN_SHIFT_LEFT; p++; }
                        else { type = Type.SHIFT_LEFT; }
                    } else if (second == '=') { type = Type.LE; p++; }
                }
                case GT -> {
                    if (second == '>') {
                        type = readShiftRight(p);
                        p = at;
                    } else if (second == '=') { type = Type.GE; p++; }
                }
                default -> {
                }
            }
            at = p;
            return MathPressoToken.of(type, start, p - start);
        }
    }

    private Type readShiftRight(int atSecond) {
        int end = source.length;
        int p = atSecond;
        if (++p != end) {
            int third = source[p] & 0xFF;
            if (third == '>') {
                if (++p != end && source[p] == '=') {
                    at = p + 1;
                    return Type.ASSIGN_SHIFT_RIGHT;
                }
                at = p;
                return Type.SHIFT_RIGHT;
            }
            if (third == '=') {
                at = p;
                return Type.ASSIGN_SHIFT_RIGHT_ARITHMETIC;
            }
        }
        at = p;
        return Type.SHIFT_RIGHT_ARITHMETIC;
    }

    private int skipComment(int afterFirstSlash) {
        int p = afterFirstSlash;
        int end = source.length;
        while (p != end) {
            if (source[p++] == '\n') {
                return p;
            }
        }
        return end;
    }

    private MathPressoToken readNumber(int start) {
        int end = source.length;
        int p = start;
        double value = 0.0;
        int digits = 0;
        while (source[p] == '0') {
            if (++p == end) {
                break;
            }
        }
        int scale = 0;
        while (p != end) {
            int digit = (source[p] & 0xFF) - '0';
            if (digit < 0 || digit > 9) {
                break;
            }
            scale++;
            if (digit != 0) {
                if (scale < POWERS_IN_TABLE) {
                    value = value * POWERS_OF_TEN_AND_THE_CHARACTER_CLASSES_STORED_AFTER_THEM[scale] + digit;
                }
                digits += scale;
                scale = 0;
            }
            p++;
        }
        int significantDigits = digits + scale;
        if (p != end && source[p] == '.') {
            while (++p != end) {
                int digit = (source[p] & 0xFF) - '0';
                if (digit < 0 || digit > 9) {
                    break;
                }
                scale++;
                if (digit != 0) {
                    if (scale < POWERS_IN_TABLE) {
                        value = value * POWERS_OF_TEN_AND_THE_CHARACTER_CLASSES_STORED_AFTER_THEM[scale] + digit;
                    }
                    digits += scale;
                    scale = 0;
                }
            }
            if (p - start == 1) {
                at = p;
                return MathPressoToken.of(Type.DOT, start, 1);
            }
        }
        boolean safe = digits <= SAFE_DIGITS && significantDigits < MOST_SIGNIFICANT_DIGITS_KEPT_SAFE;
        int exponent = safe ? significantDigits - digits : 0;
        if (p != end && (source[p] | 0x20) == 'e') {
            if (++p == end) {
                return refuseNumber(start, p);
            }
            int sign = source[p] & 0xFF;
            boolean negative = sign == '-';
            if ((negative || sign == '+') && ++p == end) {
                return refuseNumber(start, p);
            }
            int written = 0;
            int exponentSize = 0;
            do {
                int digit = (source[p] & 0xFF) - '0';
                if (digit < 0 || digit > 9) {
                    break;
                }
                written = written * 10 + digit;
                exponentSize++;
            } while (++p != end);
            if (exponentSize == 0) {
                return refuseNumber(start, p);
            }
            if (written == 0 && exponentSize < EXPONENT_DIGITS_THAT_CANNOT_OVERFLOW) {
                exponentSize = 0;
            }
            if (exponentSize <= LONGEST_EXPONENT_KEPT_SAFE) {
                exponent += negative ? -written : written;
            } else {
                safe = false;
            }
        }
        if (p != end && classAt(p) <= SYMBOL_CLASS) {
            return refuseNumber(start, p);
        }
        safe = safe && exponent >= -POWERS_IN_TABLE && exponent <= POWERS_IN_TABLE;
        int size = p - start;
        if (safe) {
            if (exponent != 0) {
                value = exponent < 0
                        ? value / POWERS_OF_TEN_AND_THE_CHARACTER_CLASSES_STORED_AFTER_THEM[-exponent]
                        : value * POWERS_OF_TEN_AND_THE_CHARACTER_CLASSES_STORED_AFTER_THEM[exponent];
            }
        } else {
            value = Double.parseDouble(new String(source, start, size, StandardCharsets.ISO_8859_1));
        }
        at = p;
        return new MathPressoToken(Type.NUMBER, start, size, value);
    }

    private MathPressoToken refuseNumber(int start, int reached) {
        at = start;
        return MathPressoToken.of(Type.INVALID, start, reached - start);
    }
}

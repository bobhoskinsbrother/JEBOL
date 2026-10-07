package org.jebol.domain.read;

import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.jebol.domain.read.LexicalClasses.*;

final class SourceScanner {

    static final int TOKEN_EOF = 0;
    static final int TOKEN_LINE = 1;
    static final int TOKEN_BLOCK_END = 2;
    static final int TOKEN_PAREN_END = 3;
    static final int TOKEN_WORD = 4;
    static final int TOKEN_SET = 5;
    static final int TOKEN_GET = 6;
    static final int TOKEN_LIT = 7;
    static final int TOKEN_NONE = 8;
    static final int TOKEN_INTEGER = 10;
    static final int TOKEN_DECIMAL = 11;
    static final int TOKEN_PERCENT = 12;
    static final int TOKEN_MONEY = 13;
    static final int TOKEN_TIME = 14;
    static final int TOKEN_DATE = 15;
    static final int TOKEN_CHAR = 16;
    static final int TOKEN_BLOCK = 17;
    static final int TOKEN_PAREN = 18;
    static final int TOKEN_STRING = 19;
    static final int TOKEN_BINARY = 20;
    static final int TOKEN_PAIR = 21;
    static final int TOKEN_TUPLE = 22;
    static final int TOKEN_FILE = 23;
    static final int TOKEN_EMAIL = 24;
    static final int TOKEN_URL = 25;
    static final int TOKEN_ISSUE = 26;
    static final int TOKEN_TAG = 27;
    static final int TOKEN_PATH = 28;
    static final int TOKEN_REF = 29;
    static final int TOKEN_REFINE = 30;
    static final int TOKEN_CONSTRUCT = 31;
    static final int TOKEN_MAP = 32;
    static final int TOKEN_INTEGER_SPEC = 33;

    private static final String[] TOKEN_NAMES = {
        "end-of-script", "line", "end-of-block", "end-of-paren", "word", "word-set", "word-get",
        "word-lit", "none", "logic", "integer", "decimal", "percent", "money", "time", "date",
        "char", "block", "paren", "string", "binary", "pair", "tuple", "file", "email", "url",
        "issue", "tag", "path", "ref", "refine", "construct", "map",
    };

    private static final int PADDING = 16;

    private static final int LF = '\n';

    private static final int CR = '\r';

    private static final int NO_MODE = 0;

    private static final int NOT_FOUND = -1;

    private static final int INVALID_CHAR = -1;

    private static final int MAX_UNI = 0x10FFFF;

    private static final int MOST_HEX_ESCAPE_DIGITS = 8;

    private static final int LONGEST_DATE = 50;

    private static final String[] ESCAPE_NAMES = {"line", "tab", "page", "escape", "esc", "back", "del", "null"};

    private static final int[] ESCAPE_CODES = {10, 9, 12, 27, 27, 8, 127, 0};

    private static final String REFUSED_IN_A_FILE = ":;()[]\"^";

    private static final String REFUSED_IN_A_QUOTED_FILE = ":;\"";

    record ScannedBlock(List<Value> values, Set<Integer> lineStarts) {
    }

    private final LexicalClasses lex = new LexicalClasses();

    private final byte[] in;

    private final int sourceLength;

    private final ScannedValues scanned;

    private final ConstructedValues constructions;

    private final OctetBuffer scanBuffer = new OctetBuffer();

    private final int maximumNesting;

    private int begin;

    private int end;

    private int headLine;

    private int lineCount;

    private boolean next;

    private boolean only;

    private int depth;

    private final List<Value> topValues = new ArrayList<>();

    private final List<Integer> topStarts = new ArrayList<>();

    private final List<Integer> topEnds = new ArrayList<>();

    private final Set<Integer> topLineStarts = new LinkedHashSet<>();

    SourceScanner(byte[] source, int firstLine, Construction construction, int maximumNesting) {
        this.in = new byte[source.length + PADDING];
        System.arraycopy(source, 0, in, 0, source.length);
        this.sourceLength = source.length;
        this.scanned = new ScannedValues(in, lex);
        this.constructions = new ConstructedValues(construction);
        this.maximumNesting = maximumNesting;
        this.lineCount = firstLine;
    }

    void stopAfterOneValue() {
        next = true;
    }

    void stopAtEveryDepth() {
        only = true;
    }

    int end() {
        return Math.min(end, sourceLength);
    }

    int lineCount() {
        return lineCount;
    }

    List<Value> topValues() {
        return topValues;
    }

    List<Integer> topStarts() {
        return topStarts;
    }

    List<Integer> topEnds() {
        return topEnds;
    }

    Set<Integer> topLineStarts() {
        return topLineStarts;
    }

    ScannedBlock scanTheWholeSource() {
        return scanBlock(NO_MODE);
    }

    private int at(int index) {
        return index < 0 || index >= in.length ? 0 : in[index] & 0xFF;
    }

    private boolean notNewline(int octet) {
        return octet != 0 && octet != CR && octet != LF;
    }

    private int flag(int special) {
        return 1 << special;
    }

    private boolean hasFlag(int flags, int special) {
        return (flags & flag(special)) != 0;
    }

    private boolean onlyFlag(int flags, int special) {
        return flags == flag(special);
    }

    private int skipToChar(int from, int until, int wanted) {
        int cp = from;
        while (cp != until && at(cp) != wanted && cp < in.length - 1) {
            cp++;
        }
        return at(cp) == wanted ? cp : NOT_FOUND;
    }

    private int prescan() {
        int cp = begin;
        int flags = 0;
        while (lex.isSpace(at(cp)) && cp < sourceLength) {
            cp++;
        }
        begin = cp;
        while (true) {
            switch (lex.classOf(at(cp))) {
                case CLASS_DELIMIT -> {
                    if (cp == begin) {
                        cp++;
                    }
                    end = cp;
                    return flags;
                }
                case CLASS_SPECIAL -> {
                    if (cp != begin) {
                        flags |= flag(lex.valueOf(at(cp)));
                    }
                    cp++;
                }
                case CLASS_WORD -> {
                    flags |= flag(SPECIAL_WORD);
                    while (lex.isAtLeastWord(at(cp))) {
                        cp++;
                    }
                }
                default -> {
                    while (lex.isAtLeastNumber(at(cp))) {
                        cp++;
                    }
                }
            }
        }
    }

    private int prescanPart(int length) {
        int cp = begin;
        int flags = 0;
        int remaining = length;
        while (lex.isSpace(at(cp)) && remaining > 0) {
            cp++;
            remaining--;
        }
        begin = cp;
        while (remaining > 0) {
            switch (lex.classOf(at(cp))) {
                case CLASS_DELIMIT -> {
                    if (cp == begin) {
                        cp++;
                    }
                    end = cp;
                    return flags;
                }
                case CLASS_SPECIAL -> {
                    if (cp != begin) {
                        flags |= flag(lex.valueOf(at(cp)));
                    }
                    cp++;
                    remaining--;
                }
                case CLASS_WORD -> {
                    flags |= flag(SPECIAL_WORD);
                    while (lex.isAtLeastWord(at(cp)) && remaining > 0) {
                        cp++;
                        remaining--;
                    }
                }
                default -> {
                    while (lex.isAtLeastNumber(at(cp)) && remaining > 0) {
                        cp++;
                        remaining--;
                    }
                }
            }
        }
        return flags;
    }

    private int scanToken() {
        int flags = prescan();
        int cp = begin;
        return switch (lex.classOf(at(cp))) {
            case CLASS_DELIMIT -> delimiterToken(cp);
            case CLASS_SPECIAL -> specialToken(cp, flags);
            case CLASS_WORD -> onlyFlag(flags, SPECIAL_WORD) ? TOKEN_WORD : wordToken(cp, flags, TOKEN_WORD);
            default -> numberToken(cp, flags);
        };
    }

    private int delimiterToken(int from) {
        int cp = from;
        switch (lex.valueOf(at(cp))) {
            case DELIMIT_SPACE, DELIMIT_SEMICOLON -> {
                while (notNewline(at(cp))) {
                    cp++;
                }
                if (at(cp) == 0) {
                    cp--;
                }
                if (at(cp) == LF) {
                    return lineToken(cp);
                }
                if (at(cp + 1) == LF) {
                    cp++;
                }
                return lineToken(cp);
            }
            case DELIMIT_RETURN -> {
                if (at(cp + 1) == LF) {
                    cp++;
                }
                return lineToken(cp);
            }
            case DELIMIT_LINEFEED -> {
                return lineToken(cp);
            }
            case DELIMIT_LEFT_BRACKET -> {
                return TOKEN_BLOCK;
            }
            case DELIMIT_RIGHT_BRACKET -> {
                return TOKEN_BLOCK_END;
            }
            case DELIMIT_LEFT_PAREN -> {
                return TOKEN_PAREN;
            }
            case DELIMIT_RIGHT_PAREN -> {
                return TOKEN_PAREN_END;
            }
            case DELIMIT_QUOTE, DELIMIT_LEFT_BRACE -> {
                return stringToken(quoted(cp));
            }
            case DELIMIT_RIGHT_BRACE -> {
                return -TOKEN_STRING;
            }
            case DELIMIT_SLASH -> {
                return slashToken(cp);
            }
            case DELIMIT_END_FILE -> {
                end--;
                return TOKEN_EOF;
            }
            default -> {
                return -TOKEN_WORD;
            }
        }
    }

    private int lineToken(int cp) {
        lineCount++;
        end = cp + 1;
        return TOKEN_LINE;
    }

    private int stringToken(int quotedEnd) {
        if (quotedEnd != NOT_FOUND) {
            end = quotedEnd;
            return TOKEN_STRING;
        }
        int cp = begin + 1;
        while (notNewline(at(cp))) {
            cp++;
        }
        end = cp;
        return -TOKEN_STRING;
    }

    private int slashToken(int from) {
        int cp = from;
        while (at(cp) == '/') {
            cp++;
        }
        int following = at(cp);
        if (lex.isAtLeastWord(following) || following == '+' || following == '-' || following == '.' || following == '_') {
            if (begin + 1 != cp) {
                while (!lex.isDelimit(at(cp))) {
                    cp++;
                }
                end = cp;
                return -TOKEN_REFINE;
            }
            if (following == '_' && lex.isDelimit(at(cp + 1))) {
                return TOKEN_WORD;
            }
            begin = cp;
            int flags = prescan();
            begin--;
            if (onlyFlag(flags, SPECIAL_WORD)) {
                return TOKEN_REFINE;
            }
            if (at(end - 1) == ':') {
                return -TOKEN_REFINE;
            }
            return wordToken(cp, flags, TOKEN_REFINE);
        }
        if (following == '<' || following == '>') {
            return arrowWordToken(cp, TOKEN_REFINE);
        }
        if (following == ':' && lex.isDelimit(at(cp + 1))) {
            end = cp + 1;
            return TOKEN_SET;
        }
        end = cp;
        return TOKEN_WORD;
    }

    private int specialToken(int cp, int flags) {
        if (hasFlag(flags, SPECIAL_AT) && at(cp) != '<' && at(cp) != '%') {
            if (at(cp) == '\'' || at(cp) == ':') {
                return -TOKEN_WORD;
            }
            return TOKEN_EMAIL;
        }
        return specialKindToken(cp, flags);
    }

    private int specialKindToken(int from, int flags) {
        int cp = from;
        switch (lex.valueOf(at(cp))) {
            case SPECIAL_AT -> {
                return TOKEN_REF;
            }
            case SPECIAL_PERCENT -> {
                return percentToken(cp);
            }
            case SPECIAL_COLON -> {
                return colonToken(cp, flags);
            }
            case SPECIAL_TICK -> {
                return tickToken(cp, flags);
            }
            case SPECIAL_COMMA, SPECIAL_PERIOD -> {
                int marked = flags | flag(lex.valueOf(at(cp)));
                if (lex.isNumber(at(cp + 1))) {
                    return numberToken(cp, marked);
                }
                if (lex.valueOf(at(cp)) != SPECIAL_PERIOD) {
                    return -TOKEN_WORD;
                }
                return wordToken(cp, marked, TOKEN_WORD);
            }
            case SPECIAL_GREATER -> {
                if (lex.isDelimit(at(cp + 1))) {
                    return TOKEN_WORD;
                }
                int following = at(cp + 1);
                if (following == '>' || following == '=' || following == '-' || following == '~') {
                    int np = skipRightArrow(cp);
                    if (np != NOT_FOUND) {
                        end = np;
                        return at(np - 1) == ':' ? TOKEN_SET : TOKEN_WORD;
                    }
                }
                return -TOKEN_WORD;
            }
            case SPECIAL_LESSER -> {
                return lesserToken(cp);
            }
            case SPECIAL_PLUS, SPECIAL_MINUS -> {
                return signToken(cp, flags);
            }
            case SPECIAL_POUND -> {
                return poundToken(cp);
            }
            case SPECIAL_UNDERSCORE -> {
                if (lex.isDelimit(at(cp + 1))) {
                    return TOKEN_NONE;
                }
                if (at(cp + 1) == ':') {
                    return -TOKEN_SET;
                }
                return wordToken(cp, flags, TOKEN_WORD);
            }
            case SPECIAL_DOLLAR -> {
                if (hasFlag(flags, SPECIAL_AT)) {
                    return TOKEN_EMAIL;
                }
                return TOKEN_MONEY;
            }
            default -> {
                return -TOKEN_WORD;
            }
        }
    }

    private int percentToken(int from) {
        int cp = from;
        int n = 1;
        while (at(cp + n) == '%') {
            n++;
        }
        if (at(cp + n) == '{') {
            while (at(cp) != 0) {
                if (at(cp + n) == '{') {
                    int closed = rawString(cp + n + 1, n);
                    if (closed == NOT_FOUND) {
                        return -TOKEN_STRING;
                    }
                    end = closed;
                    return TOKEN_STRING;
                }
                if (at(cp + n) != '%') {
                    break;
                }
                n++;
            }
            cp = end;
        } else if (lex.isDelimit(at(cp + n)) && at(cp + n) != '"' && at(cp + n) != '/') {
            return TOKEN_WORD;
        } else if (at(cp + n) == ':' && lex.isDelimit(at(cp + n + 1))) {
            return TOKEN_SET;
        } else {
            cp = end;
        }
        if (at(cp) == '"') {
            int closed = quoted(cp);
            if (closed == NOT_FOUND) {
                return -TOKEN_FILE;
            }
            end = closed;
            return TOKEN_FILE;
        }
        while (at(cp) == '/') {
            cp++;
            while (lex.isAtLeastSpecial(at(cp))) {
                cp++;
            }
        }
        end = cp;
        return TOKEN_FILE;
    }

    private int colonToken(int from, int flags) {
        int cp = from;
        if (lex.isNumber(at(cp + 1))) {
            return TOKEN_TIME;
        }
        if (onlyFlag(flags, SPECIAL_WORD)) {
            return TOKEN_GET;
        }
        if (at(cp + 1) == '_' && lex.isDelimit(at(cp + 2))) {
            return -TOKEN_GET;
        }
        if (at(cp + 1) == '\'' || at(cp + 1) == ':') {
            return -TOKEN_WORD;
        }
        if (at(cp + 1) == '<' || at(cp + 1) == '>') {
            return arrowWordToken(cp + 1, TOKEN_GET);
        }
        if (at(cp + 1) == '%') {
            do {
                ++cp;
            } while (at(cp) == '%');
            return lex.isDelimit(at(cp)) ? TOKEN_GET : -TOKEN_GET;
        }
        if (at(cp + 1) == '/') {
            do {
                ++cp;
            } while (at(cp) == '/');
            if (lex.isDelimit(at(cp))) {
                end = cp;
                return TOKEN_GET;
            }
            cp = begin;
        }
        return wordToken(cp + 1, flags, TOKEN_GET);
    }

    private int tickToken(int from, int flags) {
        int cp = from;
        if (lex.isNumber(at(cp + 1)) || at(cp + 1) == ':') {
            return -TOKEN_LIT;
        }
        if (at(cp + 1) == '_' && lex.isDelimit(at(cp + 2))) {
            return -TOKEN_LIT;
        }
        if (onlyFlag(flags, SPECIAL_WORD)) {
            return TOKEN_LIT;
        }
        cp++;
        if (at(cp) == '<' || at(cp) == '>') {
            return arrowWordToken(cp, TOKEN_LIT);
        }
        if (!lex.isWord(at(cp + 1))) {
            if ((at(cp) == '-' || at(cp) == '+') && lex.isNumber(at(cp + 1))) {
                return -TOKEN_WORD;
            }
            if (at(cp) == '%') {
                do {
                    ++cp;
                } while (at(cp) == '%');
                return lex.isDelimit(at(cp)) ? TOKEN_LIT : -TOKEN_LIT;
            }
        }
        if (at(cp) == '\'') {
            return -TOKEN_LIT;
        }
        if (at(cp) == '/') {
            cp++;
            while (at(cp) == '/') {
                cp++;
            }
            if (lex.isDelimit(at(cp))) {
                end = cp;
                return TOKEN_LIT;
            }
            while (!lex.isDelimit(at(cp))) {
                cp++;
            }
            end = cp;
            return -TOKEN_LIT;
        }
        return wordToken(cp, flags, TOKEN_LIT);
    }

    private int lesserToken(int cp) {
        int following = at(cp + 1);
        if (lex.isAnySpace(following) || following == ']' || following == ')' || following == 0) {
            return TOKEN_WORD;
        }
        if (lex.isDelimit(at(cp + 2)) && (following == '>' || following == '=' || following == '<')) {
            return TOKEN_WORD;
        }
        if (following == '<' || following == '>' || following == '=' || following == '-' || following == '~') {
            int np = skipLeftArrow(cp);
            if (np != NOT_FOUND) {
                end = np;
                return at(np - 1) == ':' ? TOKEN_SET : TOKEN_WORD;
            }
        }
        if (lex.valueOf(at(cp)) == SPECIAL_GREATER) {
            return -TOKEN_WORD;
        }
        int tagEnd = skipTag(cp);
        if (tagEnd == NOT_FOUND) {
            return -TOKEN_TAG;
        }
        end = tagEnd;
        return TOKEN_TAG;
    }

    private int signToken(int from, int flags) {
        int cp = from;
        if (hasFlag(flags, SPECIAL_AT)) {
            return TOKEN_EMAIL;
        }
        if (hasFlag(flags, SPECIAL_DOLLAR)) {
            return TOKEN_MONEY;
        }
        if (hasFlag(flags, SPECIAL_COLON)) {
            int colon = skipToChar(cp, end, ':');
            if (colon != NOT_FOUND && colon + 1 != end) {
                return TOKEN_TIME;
            }
            cp = begin;
            if (at(cp + 1) == ':') {
                return wordToken(cp, flags, TOKEN_WORD);
            }
        }
        if (hasFlag(flags, SPECIAL_LESSER)) {
            end = skipToChar(cp, end, '<');
            if (end - begin == 1) {
                return TOKEN_WORD;
            }
        }
        cp++;
        if (lex.isAtLeastNumber(at(cp))) {
            return numberToken(cp, flags);
        }
        if (lex.isSpecial(at(cp))) {
            if (at(cp) == '#') {
                end = cp;
                return TOKEN_WORD;
            }
            if (lex.valueOf(at(cp)) >= SPECIAL_PERIOD) {
                return specialKindToken(cp, flags);
            }
            if (at(cp) == '+' || at(cp) == '-' || at(cp) == '>') {
                return wordToken(cp, flags, TOKEN_WORD);
            }
            return -TOKEN_WORD;
        }
        return wordToken(cp, flags, TOKEN_WORD);
    }

    private int poundToken(int from) {
        int cp = from + 1;
        if (at(cp) == '(') {
            end = ++cp;
            return TOKEN_CONSTRUCT;
        }
        if (at(cp) == '"') {
            cp++;
            int[] position = {cp};
            int codepoint = scannedChar(position);
            cp = position[0];
            if (codepoint >= 0 && at(cp) == '"') {
                end = cp + 1;
                if (codepoint > MAX_UNI || (codepoint >= 0xD800 && codepoint <= 0xDFFF)) {
                    return -TOKEN_CHAR;
                }
                return TOKEN_CHAR;
            }
            cp = begin + 1;
            while (notNewline(at(cp))) {
                cp++;
            }
            end = cp;
            return -TOKEN_CHAR;
        }
        if (at(cp) == '{') {
            return binaryToken(cp);
        }
        if (at(cp) == '[') {
            end = ++cp;
            return TOKEN_MAP;
        }
        if (cp - 1 == begin) {
            return TOKEN_ISSUE;
        }
        return -TOKEN_INTEGER;
    }

    private int binaryToken(int brace) {
        int closed = quotedBinary(brace);
        if (closed != NOT_FOUND) {
            end = closed;
            return TOKEN_BINARY;
        }
        int cp = begin + 1;
        while (notNewline(at(cp))) {
            cp++;
        }
        end = cp;
        return -TOKEN_BINARY;
    }

    private int numberToken(int from, int knownFlags) {
        int cp = from;
        int flags = knownFlags;
        if (hasFlag(flags, SPECIAL_LESSER)) {
            end = skipToChar(cp, end, '<');
            flags = prescanPart(end - cp);
        }
        if (flags == 0) {
            return TOKEN_INTEGER;
        }
        if (hasFlag(flags, SPECIAL_AT)) {
            return TOKEN_EMAIL;
        }
        if (hasFlag(flags, SPECIAL_POUND)) {
            return poundNumberToken(cp);
        }
        if (hasFlag(flags, SPECIAL_COLON)) {
            return hasFlag(flags, SPECIAL_WORD) ? TOKEN_DATE : TOKEN_TIME;
        }
        if (hasFlag(flags, SPECIAL_PERIOD)) {
            if (skipToChar(cp, end, 'x') != NOT_FOUND) {
                return TOKEN_PAIR;
            }
            int period = skipToChar(cp, end, '.');
            if (!hasFlag(flags, SPECIAL_COMMA) && skipToChar(period + 1, end, '.') != NOT_FOUND) {
                return TOKEN_TUPLE;
            }
            return TOKEN_DECIMAL;
        }
        if (hasFlag(flags, SPECIAL_COMMA)) {
            if (skipToChar(cp, end, 'x') != NOT_FOUND) {
                return TOKEN_PAIR;
            }
            return TOKEN_DECIMAL;
        }
        for (; cp != end; cp++) {
            int octet = at(cp);
            if (octet == '-') {
                return TOKEN_DATE;
            }
            if (octet == 'x' || octet == 'X') {
                return TOKEN_PAIR;
            }
            if (octet == 'E' || octet == 'e') {
                if (skipToChar(cp, end, 'x') != NOT_FOUND) {
                    return TOKEN_PAIR;
                }
                return TOKEN_DECIMAL;
            }
            if (octet == '%') {
                return TOKEN_PERCENT;
            }
        }
        if (hasFlag(flags, SPECIAL_TICK)) {
            return TOKEN_INTEGER;
        }
        return -TOKEN_INTEGER;
    }

    private int poundNumberToken(int from) {
        int cp = from;
        if (cp == begin) {
            int base = 0;
            int first = at(cp);
            if ((first == '6' && at(cp + 1) == '4' && at(cp + 2) == '#')
                    || (first == '1' && at(cp + 1) == '6' && at(cp + 2) == '#')) {
                cp += 3;
                if (at(cp) == '{') {
                    return binaryToken(cp);
                }
                if (first == '1') {
                    base = 16;
                }
            } else if (at(cp + 1) == '#') {
                cp += 2;
                if (first == '2') {
                    if (at(cp) == '{') {
                        return binaryToken(cp);
                    }
                    base = 2;
                } else if (first == '0') {
                    base = 16;
                } else if (first == '8') {
                    base = 8;
                }
            } else if (first == '1' && at(cp + 1) == '0' && at(cp + 2) == '#') {
                cp += 3;
                base = 10;
            }
            if (base != 0) {
                int np = prescanSpecInteger(cp, base);
                if (np != NOT_FOUND) {
                    end = np;
                    return TOKEN_INTEGER_SPEC;
                }
            }
        }
        if (skipToChar(cp, end, 'x') != NOT_FOUND) {
            return TOKEN_PAIR;
        }
        if (at(cp) == '1' && at(cp + 1) == '.' && at(cp + 2) == '#'
                && (scanned.spells(cp + 3, "INF") || scanned.spells(cp + 3, "NAN"))) {
            return TOKEN_DECIMAL;
        }
        return -TOKEN_INTEGER;
    }

    private int wordToken(int from, int flags, int type) {
        int cp = from;
        if (hasFlag(flags, SPECIAL_COLON)) {
            if (type != TOKEN_WORD) {
                return type;
            }
            cp = skipToChar(cp, end, ':');
            if (at(cp + 1) != '/' && lex.entry(at(cp + 1)) < SPECIAL) {
                if ((flags & ~flag(SPECIAL_COLON) & WORD_FLAGS) != 0) {
                    return -TOKEN_WORD;
                }
                return TOKEN_SET;
            }
            cp = end;
            while (at(cp) == '/') {
                cp++;
                while (lex.isAtLeastSpecial(at(cp)) || at(cp) == '/' || at(cp) == 0x7F) {
                    cp++;
                }
            }
            end = cp;
            return TOKEN_URL;
        }
        if (hasFlag(flags, SPECIAL_AT)) {
            return TOKEN_EMAIL;
        }
        if (hasFlag(flags, SPECIAL_DOLLAR)) {
            return TOKEN_MONEY;
        }
        if ((flags & WORD_FLAGS) != 0) {
            return -type;
        }
        if (hasFlag(flags, SPECIAL_LESSER)) {
            cp = skipToChar(cp, end, '<');
            int following = at(cp + 1);
            if (following == '<' || following == '>' || following == '=' || lex.isSpace(following)
                    || (following != '/' && lex.isDelimit(following))) {
                return -type;
            }
            end = cp;
        } else if (hasFlag(flags, SPECIAL_GREATER)) {
            if (at(cp) == '=' || at(cp) == '-' || at(cp) == '~' || at(cp) == '>') {
                int np = skipRightArrow(cp);
                if (np != NOT_FOUND) {
                    end = np;
                    return at(np - 1) == ':' ? TOKEN_SET : type;
                }
            }
            return -type;
        }
        return type;
    }

    private int arrowWordToken(int cp, int type) {
        int np = at(cp) == '<' ? skipLeftArrow(cp) : skipRightArrow(cp);
        if (np == NOT_FOUND) {
            return -type;
        }
        end = np;
        if (type == TOKEN_REFINE && at(end - 1) == ':') {
            return -type;
        }
        return type;
    }

    private int skipTag(int from) {
        int cp = from;
        if (at(cp) == '<') {
            cp++;
        }
        while (at(cp) != 0 && at(cp) != '>') {
            if (at(cp) == '"') {
                cp++;
                while (at(cp) != 0 && at(cp) != '"') {
                    cp++;
                }
                if (at(cp) == 0) {
                    return NOT_FOUND;
                }
            } else if (at(cp) == '\'') {
                cp++;
                while (at(cp) != 0 && at(cp) != '\'') {
                    cp++;
                }
                if (at(cp) == 0) {
                    return NOT_FOUND;
                }
            }
            cp++;
        }
        return at(cp) != 0 ? cp + 1 : NOT_FOUND;
    }

    private int skipLeftArrow(int from) {
        int cp = from;
        while (at(cp) == '<') {
            cp++;
        }
        return skipRightArrow(cp);
    }

    private int skipRightArrow(int from) {
        int cp = from;
        while (at(cp) != 0) {
            int octet = at(cp);
            if (octet == '-' || octet == '=' || octet == '>' || octet == '~') {
                cp++;
                continue;
            }
            if (lex.isDelimit(octet)) {
                break;
            }
            if (octet == ':') {
                cp++;
                break;
            }
            return NOT_FOUND;
        }
        return cp;
    }

    private int prescanSpecInteger(int from, int base) {
        int cp = from;
        int n;
        int most;
        if (base == 16) {
            n = most = 16;
            while (n > 0 && lex.isHexDigit(at(cp))) {
                cp++;
                n--;
            }
        } else if (base == 2) {
            n = most = 64;
            while (n > 0 && (at(cp) == '0' || at(cp) == '1')) {
                cp++;
                n--;
            }
        } else if (base == 8) {
            n = most = 22;
            while (n > 0 && at(cp) >= '0' && at(cp) <= '7') {
                cp++;
                n--;
            }
        } else if (base == 10) {
            n = most = 18;
            while (n > 0 && lex.isNumber(at(cp))) {
                cp++;
                n--;
            }
        } else {
            return NOT_FOUND;
        }
        return n < most && lex.isDelimit(at(cp)) ? cp : NOT_FOUND;
    }

    private int scannedChar(int[] position) {
        int cp = position[0];
        int first = at(cp);
        if (first >= 0x80) {
            return utf8Codepoint(position);
        }
        cp++;
        if (first != '^') {
            position[0] = cp;
            return first;
        }
        int escaped = at(cp);
        cp++;
        int codepoint;
        switch (escaped) {
            case 0 -> codepoint = 0;
            case '/' -> codepoint = LF;
            case '^' -> codepoint = escaped;
            case '-' -> codepoint = '\t';
            case '!' -> codepoint = 0x1E;
            case '(' -> {
                position[0] = cp;
                return parenthesisedEscape(position);
            }
            default -> {
                int upper = Character.toUpperCase(escaped);
                if (escaped < 0x80 && upper >= '@' && upper <= '_') {
                    codepoint = upper - '@';
                } else if (upper == '~') {
                    codepoint = 0x7F;
                } else {
                    codepoint = escaped;
                }
            }
        }
        position[0] = cp;
        return codepoint;
    }

    private int parenthesisedEscape(int[] position) {
        int start = position[0];
        int cp = start;
        long accumulated = 0;
        int lexical;
        while ((lexical = lex.entry(at(cp))) > WORD) {
            int digit = lexical & VALUE_MASK;
            if (digit == 0 && lexical < NUMBER) {
                break;
            }
            accumulated = (accumulated << 4) + digit;
            cp++;
        }
        if (cp - start > MOST_HEX_ESCAPE_DIGITS) {
            return INVALID_CHAR;
        }
        if (at(cp) == ')') {
            position[0] = cp + 1;
            return accumulated > Integer.MAX_VALUE ? INVALID_CHAR : (int) accumulated;
        }
        for (int named = 0; named < ESCAPE_NAMES.length; named++) {
            int matched = matchedBytes(start, ESCAPE_NAMES[named]);
            if (matched != NOT_FOUND && at(matched) == ')') {
                position[0] = matched + 1;
                return ESCAPE_CODES[named];
            }
        }
        return INVALID_CHAR;
    }

    private int matchedBytes(int from, String lowerCase) {
        for (int offset = 0; offset < lowerCase.length(); offset++) {
            if (Character.toLowerCase(at(from + offset)) != lowerCase.charAt(offset)) {
                return NOT_FOUND;
            }
        }
        return from + lowerCase.length();
    }

    private int utf8Codepoint(int[] position) {
        int cp = position[0];
        int first = at(cp);
        int size;
        int codepoint;
        if ((first & 0xE0) == 0xC0) {
            size = 2;
            codepoint = first & 0x1F;
        } else if ((first & 0xF0) == 0xE0) {
            size = 3;
            codepoint = first & 0x0F;
        } else if ((first & 0xF8) == 0xF0) {
            size = 4;
            codepoint = first & 0x07;
        } else {
            position[0] = cp + 1;
            return INVALID_CHAR;
        }
        for (int continuing = 1; continuing < size; continuing++) {
            codepoint = (codepoint << 6) | (at(cp + continuing) & 0x3F);
        }
        position[0] = cp + size;
        return codepoint;
    }

    private boolean isInvalidChar(int codepoint) {
        return codepoint < 0 || codepoint > MAX_UNI || (codepoint >= 0xD800 && codepoint <= 0xDFFF);
    }

    private void appendCodepoint(int codepoint) {
        byte[] encoded = new String(Character.toChars(codepoint)).getBytes(StandardCharsets.UTF_8);
        scanBuffer.write(encoded, 0, encoded.length);
    }

    private int quoted(int from) {
        int src = from;
        int nest = 0;
        int lines = 0;
        scanBuffer.reset();
        int terminator = at(src++) == '{' ? '}' : '"';
        int start = src;
        int length = 0;
        while (at(src) != terminator || nest > 0) {
            int octet = at(src);
            if (octet == CR || octet == LF) {
                if (octet == CR) {
                    if (at(src + 1) == LF) {
                        src++;
                    } else {
                        length++;
                    }
                }
                if (terminator == '"') {
                    return NOT_FOUND;
                }
                lines++;
                scanBuffer.write(in, start, length);
                scanBuffer.write(LF);
                start = ++src;
                length = 0;
                continue;
            }
            if (octet == '^') {
                scanBuffer.write(in, start, length);
                int[] position = {src};
                int codepoint = scannedChar(position);
                src = position[0];
                if (isInvalidChar(codepoint)) {
                    return NOT_FOUND;
                }
                appendCodepoint(codepoint);
                start = src;
                length = 0;
                continue;
            }
            if (octet == '{' && terminator != '"') {
                nest++;
            } else if (octet == '}' && terminator != '"' && nest > 0) {
                nest--;
            } else if (octet == 0) {
                return NOT_FOUND;
            }
            length++;
            src++;
        }
        scanBuffer.write(in, start, length);
        lineCount += lines;
        return src + 1;
    }

    private int quotedBinary(int from) {
        int src = from;
        int lines = 0;
        scanBuffer.reset();
        if (at(src++) != '{') {
            return NOT_FOUND;
        }
        while (at(src) != '}') {
            int chr = at(src);
            boolean skipping = false;
            switch (chr) {
                case 0 -> {
                    return NOT_FOUND;
                }
                case '^' -> {
                    int[] position = {src};
                    chr = scannedChar(position);
                    if (isInvalidChar(chr)) {
                        return NOT_FOUND;
                    }
                    src = position[0] - 1;
                }
                case ';' -> {
                    boolean ended = false;
                    while (chr != 0) {
                        chr = at(++src);
                        if (chr == '^') {
                            int[] position = {src};
                            chr = scannedChar(position);
                            if (isInvalidChar(chr)) {
                                return NOT_FOUND;
                            }
                            src = position[0] - 1;
                        }
                        if (chr == LF || chr == CR) {
                            ended = true;
                            break;
                        }
                    }
                    if (!ended) {
                        return NOT_FOUND;
                    }
                    lines++;
                    skipping = true;
                }
                case CR, LF -> {
                    if (chr == CR && at(src + 1) == LF) {
                        src++;
                    }
                    lines++;
                    skipping = true;
                }
                case ' ', '\t' -> skipping = true;
                default -> {
                    if (chr >= 0x80) {
                        return NOT_FOUND;
                    }
                }
            }
            src++;
            if (!skipping) {
                scanBuffer.write(chr);
            }
        }
        lineCount += lines;
        return src + 1;
    }

    private int rawString(int from, int percents) {
        int src = from;
        int lines = 0;
        scanBuffer.reset();
        while (at(src) != 0) {
            int chr = at(src++);
            if (chr == LF) {
                lines++;
            } else if (chr == '}' && at(src) == '%') {
                int n = 1;
                while (at(src + n) == '%') {
                    n++;
                }
                if (percents == n) {
                    lineCount += lines;
                    return src + n;
                }
                if (n > percents) {
                    return NOT_FOUND;
                }
            }
            scanBuffer.write(chr);
        }
        return NOT_FOUND;
    }

    private String scannedText() {
        return scanBuffer.toString(StandardCharsets.UTF_8);
    }

    private String textOf(int from, int length) {
        return new String(in, from, Math.max(0, length), StandardCharsets.UTF_8);
    }

    private ScanFailure scanError(SyntaxFailure failure, int token, String argument) {
        int cp = headLine;
        while (lex.isSpace(at(cp)) && cp < sourceLength) {
            cp++;
        }
        int lineStart = cp;
        while (notNewline(at(cp))) {
            cp++;
        }
        String near = "(line " + lineCount + ") " + textOf(lineStart, cp - lineStart);
        return new ScanFailure(failure,
                List.of(StringValue.of(TOKEN_NAMES[token]), StringValue.of(argument)),
                Optional.of(near)).relaxable();
    }

    private ScanFailure invalid(int token, int from, int to) {
        return scanError(SyntaxFailure.INVALID_LEXEME, token, textOf(from, to - from));
    }

    private ScannedBlock scanBlock(int modeChar) {
        if (depth >= maximumNesting) {
            throw new ScanFailure(SyntaxFailure.NESTED_PAST_THE_STACK);
        }
        depth++;
        try {
            return scanBlockAtThisDepth(modeChar);
        } finally {
            depth--;
        }
    }

    private final class BlockInProgress {

        private final int modeChar;

        private final boolean outermost;

        private final boolean justOnce;

        private final int startLine;

        private final int startHead;

        private final List<Value> emitted = new ArrayList<>();

        private final Set<Integer> lineStarts;

        private boolean lineWaiting;

        BlockInProgress(int modeChar, boolean outermost, boolean justOnce) {
            this.modeChar = modeChar;
            this.outermost = outermost;
            this.justOnce = justOnce;
            this.startLine = lineCount;
            this.startHead = headLine;
            this.lineStarts = outermost ? topLineStarts : new LinkedHashSet<>();
        }

        void emit(Value value, int startedAt) {
            emitted.add(value);
            if (lineWaiting) {
                lineWaiting = false;
                lineStarts.add(emitted.size());
            }
            if (outermost) {
                topValues.add(value);
                topStarts.add(startedAt);
                topEnds.add(begin);
            }
        }

        ScannedBlock scanned() {
            return new ScannedBlock(emitted, lineStarts);
        }
    }

    private enum AfterAToken { CARRY_ON, END_THE_BLOCK }

    private int segmentEnd;

    private boolean exitingAfterThisValue;

    private int errors;

    private boolean relaxed;

    void relax() {
        relaxed = true;
    }

    private ScannedBlock scanBlockAtThisDepth(int modeChar) {
        boolean justOnce = next;
        if (justOnce) {
            next = false;
        }
        BlockInProgress block = new BlockInProgress(modeChar, modeChar == NO_MODE && depth == 1, justOnce);
        int token;
        while ((token = scanToken()) != TOKEN_EOF) {
            int startedAt = begin;
            try {
                if (took(token, block) == AfterAToken.END_THE_BLOCK) {
                    return block.scanned();
                }
            } catch (ScanFailure failure) {
                block.emit(relaxedOrThrown(failure), startedAt);
                return block.scanned();
            }
        }
        if (modeChar == ']' || modeChar == ')') {
            block.emit(relaxedOrThrown(missing(TOKEN_EOF, modeChar, block.startLine, block.startHead)), begin);
        }
        return block.scanned();
    }

    private Value relaxedOrThrown(ScanFailure failure) {
        if (!relaxed || !failure.isRelaxable()) {
            throw failure;
        }
        errors++;
        return failure.asAFailure().error().orElseThrow();
    }

    private AfterAToken took(int scannedToken, BlockInProgress block) {
        int token = scannedToken;
        int modeChar = block.modeChar;
        int bp = begin;
        int ep = end;
        if (token < 0) {
            begin = end;
            throw invalid(-token, bp, ep);
        }
        if (modeChar == '/' && at(bp) == '/') {
            block.emitted.add(NoneValue.none());
            begin = bp + 1;
            return AfterAToken.CARRY_ON;
        }
        Value value;
        boolean startsAPath = (token == TOKEN_WORD || token == TOKEN_LIT || token == TOKEN_GET) && at(ep) == '/';
        if ((token == TOKEN_PATH || startsAPath) && modeChar != '/') {
            value = pathOf(token, scanBlock('/').values(), bp);
        } else {
            begin = end;
            switch (token) {
                case TOKEN_LINE -> {
                    block.lineWaiting = true;
                    headLine = ep;
                    return AfterAToken.CARRY_ON;
                }
                case TOKEN_BLOCK_END, TOKEN_PAREN_END -> {
                    int opened = token == TOKEN_BLOCK_END ? '[' : '(';
                    int closing = token == TOKEN_BLOCK_END ? ']' : ')';
                    if (modeChar == NO_MODE) {
                        throw scanError(SyntaxFailure.MISSING_CLOSE, token, String.valueOf((char) opened));
                    }
                    if (modeChar != closing) {
                        throw missing(token, modeChar, block.startLine, block.startHead);
                    }
                    return AfterAToken.END_THE_BLOCK;
                }
                default -> value = valueOfToken(token, bp, ep, ep - bp, modeChar);
            }
        }
        block.emit(value, bp);
        if (exitingAfterThisValue) {
            exitingAfterThisValue = false;
            return AfterAToken.END_THE_BLOCK;
        }
        if (modeChar == '/') {
            int after = segmentEnd;
            if (at(after) != '/') {
                return AfterAToken.END_THE_BLOCK;
            }
            after++;
            begin = after;
            if (at(after) != '(' && lex.isDelimit(at(after))) {
                throw invalid(TOKEN_PATH, bp, after);
            }
        }
        return only || block.justOnce ? AfterAToken.END_THE_BLOCK : AfterAToken.CARRY_ON;
    }

    private ScanFailure missing(int token, int modeChar, int startLine, int startHead) {
        lineCount = startLine;
        headLine = startHead;
        return scanError(SyntaxFailure.MISSING_CLOSE, token, String.valueOf((char) modeChar));
    }

    private Value pathOf(int token, List<Value> segments, int bp) {
        List<Value> parts = new ArrayList<>(segments);
        Datatype kind;
        if (token == TOKEN_LIT) {
            kind = Datatype.LIT_PATH;
            parts.set(0, asPlainWord(parts.getFirst()));
        } else if (parts.getFirst() instanceof WordValue first && first.datatype() == Datatype.GET_WORD) {
            if (at(end) == ':') {
                throw invalid(TOKEN_PATH, bp, end);
            }
            kind = Datatype.GET_PATH;
            parts.set(0, asPlainWord(first));
        } else if (at(end) == ':') {
            kind = Datatype.SET_PATH;
            begin = ++end;
        } else {
            kind = Datatype.PATH;
        }
        return BlockValue.path(parts, kind);
    }

    private Value asPlainWord(Value segment) {
        return segment instanceof WordValue word ? WordValue.of(word.spelling()) : segment;
    }

    private Value dateAfterAnInteger(int bp, int to) {
        int ep = to;
        while (at(ep) == '/' || lex.isAtLeastSpecial(at(ep))) {
            ep++;
        }
        begin = ep;
        segmentEnd = ep;
        return dateEndingAt(TOKEN_DATE, bp, ep - bp, ep);
    }

    private Value dateToken(int bp, int to, int length, int modeChar) {
        int ep = to;
        int len = length;
        while (at(ep) == '/' && modeChar != '/') {
            ep++;
            while (lex.isAtLeastSpecial(at(ep))) {
                ep++;
            }
            len = ep - bp;
            if (len > LONGEST_DATE) {
                break;
            }
            begin = ep;
        }
        segmentEnd = ep;
        return dateEndingAt(TOKEN_DATE, bp, len, ep);
    }

    private Value dateEndingAt(int token, int bp, int length, int ep) {
        return scanned.date(bp, length)
                .filter(_ -> scanned.scannedTo() == ep)
                .orElseThrow(() -> invalid(token, bp, ep));
    }

    private Value valueOfToken(int token, int bp, int ep, int len, int modeChar) {
        segmentEnd = ep;
        switch (token) {
            case TOKEN_NONE -> {
                return NoneValue.none();
            }
            case TOKEN_LIT, TOKEN_GET, TOKEN_SET, TOKEN_WORD -> {
                return wordValue(token, bp, ep, len, modeChar);
            }
            case TOKEN_REFINE -> {
                return WordValue.of(textOf(bp + 1, len - 1), Datatype.REFINEMENT);
            }
            case TOKEN_ISSUE -> {
                if (!isAnIssue(bp + 1, len - 1)) {
                    throw invalid(token, bp, ep);
                }
                return WordValue.of(textOf(bp + 1, len - 1), Datatype.ISSUE);
            }
            case TOKEN_BLOCK, TOKEN_PAREN -> {
                ScannedBlock inner = scanBlock(token == TOKEN_BLOCK ? ']' : ')');
                segmentEnd = end;
                if (errors > 0) {
                    exitingAfterThisValue = true;
                    return inner.values().getLast();
                }
                BlockValue block = token == TOKEN_BLOCK
                        ? BlockValue.block(inner.values())
                        : BlockValue.paren(inner.values());
                return withItsLineStarts(block, inner.lineStarts());
            }
            case TOKEN_INTEGER -> {
                if (at(ep) != '/' || modeChar == '/') {
                    return scanned.integer(bp, len).orElseThrow(() -> invalid(TOKEN_INTEGER, bp, ep));
                }
                return dateAfterAnInteger(bp, ep);
            }
            case TOKEN_DECIMAL, TOKEN_PERCENT -> {
                if (at(ep) == '/') {
                    throw invalid(token, bp, ep);
                }
                Value read = scanned.decimal(bp, len).orElseThrow(() -> invalid(token, bp, ep));
                if (at(bp + len - 1) == '%') {
                    return DecimalValue.percent(((DecimalValue) read).quantity() / 100.0);
                }
                return read;
            }
            case TOKEN_MONEY -> {
                if (at(ep) == '/') {
                    throw invalid(token, bp, ep + 1);
                }
                return scanned.money(bp, len).orElseThrow(() -> invalid(token, bp, ep));
            }
            case TOKEN_TIME -> {
                if (at(bp + len - 1) == ':' && modeChar == '/') {
                    Value number = scanned.integer(bp, len - 1).orElseThrow(() -> invalid(token, bp, ep));
                    end--;
                    return number;
                }
                return scanned.scannedTimeOrNot(bp)
                        .filter(_ -> scanned.scannedTo() == ep)
                        .<Value>map(read -> read)
                        .orElseThrow(() -> invalid(token, bp, ep));
            }
            case TOKEN_DATE -> {
                return dateToken(bp, ep, len, modeChar);
            }
            case TOKEN_CHAR -> {
                int[] position = {bp + 2};
                return CharacterValue.of(scannedChar(position));
            }
            case TOKEN_STRING -> {
                return StringValue.of(scannedText());
            }
            case TOKEN_BINARY -> {
                return decodedBinary(scanned.binaryBase(bp, len)).orElseThrow(() -> invalid(token, bp, ep));
            }
            case TOKEN_PAIR -> {
                return scanned.pair(bp, len).orElseThrow(() -> invalid(token, bp, ep));
            }
            case TOKEN_TUPLE -> {
                return scanned.tuple(bp, len).orElseThrow(() -> invalid(token, bp, ep));
            }
            case TOKEN_FILE -> {
                return file(bp, len).orElseThrow(() -> invalid(token, bp, ep));
            }
            case TOKEN_EMAIL -> {
                return email(bp, len).orElseThrow(() -> invalid(token, bp, ep));
            }
            case TOKEN_REF -> {
                return StringValue.of(textOf(bp + 1, len - 1), Datatype.REF);
            }
            case TOKEN_URL -> {
                return StringValue.of(textOf(bp, len), Datatype.URL);
            }
            case TOKEN_TAG -> {
                return StringValue.of(textOf(bp + 1, len - 2), Datatype.TAG);
            }
            case TOKEN_CONSTRUCT -> {
                return constructed();
            }
            case TOKEN_MAP -> {
                ScannedBlock inner = scanBlock(']');
                segmentEnd = end;
                if (inner.values().size() % 2 != 0) {
                    throw new ScanFailure(SyntaxFailure.INVALID_ARG,
                            List.of(BlockValue.block(inner.values())), Optional.empty());
                }
                return MapValue.of(inner.values());
            }
            case TOKEN_INTEGER_SPEC -> {
                return scanned.specInteger(bp, len);
            }
            default -> {
                return NoneValue.none();
            }
        }
    }

    private Value wordValue(int token, int from, int to, int length, int modeChar) {
        int bp = from;
        int len = length;
        int kind = token;
        if (kind == TOKEN_LIT || kind == TOKEN_GET) {
            if (at(to - 1) == ':') {
                if (len == 1 || modeChar != '/') {
                    throw invalid(kind, bp, to);
                }
                len--;
                end--;
            }
            bp++;
        }
        if (kind != TOKEN_WORD) {
            len--;
            if (modeChar == '/' && kind == TOKEN_SET) {
                kind = TOKEN_WORD;
                end--;
            }
        }
        if (len == 0) {
            throw invalid(kind, bp - 1, to);
        }
        segmentEnd = to;
        Datatype type = switch (kind) {
            case TOKEN_SET -> Datatype.SET_WORD;
            case TOKEN_GET -> Datatype.GET_WORD;
            case TOKEN_LIT -> Datatype.LIT_WORD;
            default -> Datatype.WORD;
        };
        return WordValue.of(textOf(bp, len), type);
    }

    private boolean isAnIssue(int from, int length) {
        if (length == 0) {
            return false;
        }
        int bp = from;
        for (int remaining = length; remaining > 0; remaining--) {
            int octet = at(bp);
            switch (lex.classOf(octet)) {
                case CLASS_DELIMIT -> {
                    return false;
                }
                case CLASS_SPECIAL -> {
                    int special = lex.valueOf(octet);
                    if (!(special == SPECIAL_TICK || special == SPECIAL_COMMA || special == SPECIAL_PERIOD
                            || special == SPECIAL_PLUS || special == SPECIAL_MINUS || special == SPECIAL_TILDE
                            || special == SPECIAL_POUND || special == SPECIAL_UNDERSCORE)) {
                        return false;
                    }
                }
                default -> {
                }
            }
            bp++;
        }
        return true;
    }

    private Value constructed() {
        boolean wasOnly = only;
        only = false;
        ScannedBlock spec;
        try {
            spec = scanBlock(')');
        } finally {
            only = wasOnly;
        }
        if (relaxed && !spec.values().isEmpty() && spec.values().getFirst() instanceof ErrorValue error) {
            return error;
        }
        Optional<Value> built = constructions.built(spec.values());
        if (built.isPresent()) {
            return built.get();
        }
        ScanFailure malformed = new ScanFailure(SyntaxFailure.MALCONSTRUCT,
                List.of(BlockValue.block(spec.values())), Optional.empty());
        if (!relaxed) {
            throw malformed;
        }
        return malformed.asAFailure().error().orElseThrow();
    }

    private Optional<Value> decodedBinary(int base) {
        byte[] digits = scanBuffer.toByteArray();
        return switch (base) {
            case 16 -> hexadecimalOctets(digits);
            case 2 -> bitOctets(digits);
            case 64 -> base64Octets(digits);
            default -> Optional.empty();
        };
    }

    private Optional<Value> hexadecimalOctets(byte[] digits) {
        OctetBuffer octets = new OctetBuffer();
        int count = digits.length % 2 == 1 ? 1 : 0;
        int accumulated = 0;
        for (byte each : digits) {
            int lexical = lex.entry(each);
            if (lexical > WORD) {
                int value = lexical & VALUE_MASK;
                if (value == 0 && lexical < NUMBER) {
                    return Optional.empty();
                }
                accumulated = (accumulated << 4) + value;
                if ((count++ & 1) == 1) {
                    octets.write(accumulated & 0xFF);
                }
            } else if (each == 0 || lexical > DELIMIT_RETURN) {
                return Optional.empty();
            }
        }
        if ((count & 1) == 1) {
            return Optional.empty();
        }
        return Optional.of(BinaryValue.ofBytes(octets.toByteArray()));
    }

    private Optional<Value> bitOctets(byte[] digits) {
        OctetBuffer octets = new OctetBuffer();
        int count = digits.length & 7;
        if (count != 0) {
            count = 8 - count;
        }
        int accumulated = 0;
        for (byte each : digits) {
            int lexical = lex.entry(each);
            if (lexical >= NUMBER) {
                if (each == '0') {
                    accumulated *= 2;
                } else if (each == '1') {
                    accumulated = accumulated * 2 + 1;
                } else {
                    return Optional.empty();
                }
                if (count++ >= 7) {
                    octets.write(accumulated & 0xFF);
                    count = 0;
                    accumulated = 0;
                }
            } else if (each == 0 || lexical > DELIMIT_RETURN) {
                return Optional.empty();
            }
        }
        if (count != 0) {
            return Optional.empty();
        }
        return Optional.of(BinaryValue.ofBytes(octets.toByteArray()));
    }

    private Optional<Value> base64Octets(byte[] digits) {
        Optional<Value> standard = base64Octets(digits, false);
        return standard.isPresent() || !holdsAUrlSafeDigit(digits) ? standard : base64Octets(digits, true);
    }

    private boolean holdsAUrlSafeDigit(byte[] digits) {
        for (byte each : digits) {
            if (each == '-' || each == '_') {
                return true;
            }
        }
        return false;
    }

    private int base64Digit(int octet, boolean urlSafe) {
        if (octet >= 'A' && octet <= 'Z') {
            return octet - 'A';
        }
        if (octet >= 'a' && octet <= 'z') {
            return octet - 'a' + 26;
        }
        if (octet >= '0' && octet <= '9') {
            return octet - '0' + 52;
        }
        if (octet == (urlSafe ? '-' : '+')) {
            return 62;
        }
        if (octet == (urlSafe ? '_' : '/')) {
            return 63;
        }
        if (octet == '=') {
            return 0;
        }
        if (octet == ' ' || octet == '\t' || octet == '\n' || octet == '\r' || octet == '\f' || octet == '\'') {
            return BASE64_SPACE;
        }
        return BASE64_ERROR;
    }

    private static final int BASE64_SPACE = 0x55;

    private static final int BASE64_ERROR = 0x80;

    private Optional<Value> base64Octets(byte[] digits, boolean urlSafe) {
        OctetBuffer octets = new OctetBuffer();
        int accumulated = 0;
        int flip = 0;
        int position = 0;
        while (position < digits.length) {
            int octet = digits[position] & 0xFF;
            if (octet > 127) {
                if (octet == 0xA0) {
                    position++;
                    continue;
                }
                return Optional.empty();
            }
            int digit = base64Digit(octet, urlSafe);
            if (digit < BASE64_SPACE) {
                if (octet != '=') {
                    accumulated = (accumulated << 6) + digit;
                    if (flip++ == 3) {
                        octets.write((accumulated >> 16) & 0xFF);
                        octets.write((accumulated >> 8) & 0xFF);
                        octets.write(accumulated & 0xFF);
                        accumulated = 0;
                        flip = 0;
                    }
                } else {
                    position++;
                    if (flip == 3) {
                        octets.write((accumulated >> 10) & 0xFF);
                        octets.write((accumulated >> 2) & 0xFF);
                        flip = 0;
                    } else if (flip == 2) {
                        boolean another = false;
                        for (int seek = position; seek < digits.length; seek++) {
                            if (digits[seek] == '=') {
                                another = true;
                                break;
                            }
                        }
                        if (!another) {
                            return Optional.empty();
                        }
                        octets.write((accumulated >> 4) & 0xFF);
                        flip = 0;
                    } else {
                        return Optional.empty();
                    }
                    break;
                }
            } else if (digit == BASE64_ERROR) {
                return Optional.empty();
            }
            position++;
        }
        if (flip != 0) {
            if (!urlSafe) {
                return Optional.empty();
            }
            if (flip == 3) {
                octets.write((accumulated >> 10) & 0xFF);
                octets.write((accumulated >> 2) & 0xFF);
            } else if (flip == 2) {
                octets.write((accumulated >> 4) & 0xFF);
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(BinaryValue.ofBytes(octets.toByteArray()));
    }

    private Optional<Value> file(int from, int length) {
        int cp = from;
        int len = length;
        int terminator = 0;
        String refused = REFUSED_IN_A_FILE;
        if (at(cp) == '%') {
            cp++;
            len--;
        }
        if (at(cp) == '"') {
            cp++;
            len--;
            terminator = '"';
            refused = REFUSED_IN_A_QUOTED_FILE;
        }
        return item(cp, cp + len, terminator, refused)
                .map(text -> StringValue.of(text, Datatype.FILE));
    }

    private Optional<String> item(int from, int until, int terminator, String refused) {
        OctetBuffer written = new OctetBuffer();
        int src = from;
        while (src < until && at(src) != terminator) {
            int chr = at(src);
            if (chr < 0x80) {
                src++;
                if (chr == 0) {
                    break;
                }
                if (terminator == 0 && (chr == ' ' || chr == '\t')) {
                    break;
                }
                if (chr < ' ') {
                    return Optional.empty();
                }
                if (chr == '\\') {
                    chr = '/';
                } else if (chr == '%') {
                    if (!lex.isHexDigit(at(src)) || !lex.isHexDigit(at(src + 1))) {
                        return Optional.empty();
                    }
                    chr = (lex.valueOf(at(src)) << 4) + lex.valueOf(at(src + 1));
                    src += 2;
                } else if (chr == '^') {
                    if (src + 1 == until || refused.indexOf('^') >= 0) {
                        return Optional.empty();
                    }
                    int[] position = {src};
                    chr = scannedChar(position);
                    if (terminator == 0 && (chr == ' ' || chr == '\t')) {
                        break;
                    }
                    src = position[0] - 1;
                    if (chr < 0) {
                        return Optional.empty();
                    }
                } else if (refused.indexOf(chr) >= 0) {
                    return Optional.empty();
                }
                byte[] encoded = new String(Character.toChars(chr)).getBytes(StandardCharsets.UTF_8);
                written.write(encoded, 0, encoded.length);
            } else {
                int[] position = {src};
                int codepoint = utf8Codepoint(position);
                src = position[0];
                byte[] encoded = new String(Character.toChars(Math.max(codepoint, 0)))
                        .getBytes(StandardCharsets.UTF_8);
                written.write(encoded, 0, encoded.length);
            }
        }
        return Optional.of(written.toString(StandardCharsets.UTF_8));
    }

    private Optional<Value> email(int from, int length) {
        OctetBuffer written = new OctetBuffer();
        boolean seenAnAtSign = false;
        int cp = from;
        for (int remaining = length; remaining > 0; remaining--) {
            if (at(cp) == '@') {
                if (seenAnAtSign) {
                    return Optional.empty();
                }
                seenAnAtSign = true;
            }
            if (at(cp) == '%') {
                if (remaining <= 2 || !lex.isHexDigit(at(cp + 1)) || !lex.isHexDigit(at(cp + 2))) {
                    return Optional.empty();
                }
                written.write((lex.valueOf(at(cp + 1)) << 4) + lex.valueOf(at(cp + 2)));
                cp += 3;
                remaining -= 2;
            } else {
                written.write(at(cp++));
            }
        }
        if (!seenAnAtSign) {
            return Optional.empty();
        }
        return Optional.of(StringValue.of(written.toString(StandardCharsets.UTF_8), Datatype.EMAIL));
    }

    private BlockValue withItsLineStarts(BlockValue block, Set<Integer> starts) {
        for (int position : starts) {
            block.storage().setLineBreakAt(position, true);
        }
        return block;
    }

}

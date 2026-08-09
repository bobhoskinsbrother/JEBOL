package org.jebol.domain.read;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

/**
 * Turns REBOL source text into values.
 *
 * <p>Produces unbound words and series at their head, as
 * {@code spec/load.allium} requires. Binding is a separate step, because a
 * word's binding is not a property of how it was written and code that
 * rewrites blocks before evaluating them depends on getting unbound words
 * back.
 *
 * <p>Reports the first failure and stops.
 */
public final class Transcoder {

    private static final int END_OF_INPUT = -1;
    private static final int NO_TERMINATOR = 0;

    /**
     * How deeply blocks may nest.
     *
     * <p>Bounding this here bounds it everywhere. Reading keeps its own stack
     * so it could go deeper, but everything that walks a block afterwards --
     * binding, molding, comparing, evaluating -- would then have to keep its
     * own stack too, and each of those is a separate chance to get it wrong.
     * One limit at the door is cheaper than five, and no real source comes
     * anywhere near it.
     */
    public static final int MAXIMUM_NESTING = 1_000;

    private final int[] codepoints;
    private int position;
    private int line = 1;
    private int column = 1;

    private Transcoder(String source) {
        this.codepoints = source.codePoints().toArray();
    }

    /** Reads every value in the source, or reports the first failure. */
    public static TranscodeResult transcode(String source) {
        if (source == null) {
            throw new IllegalArgumentException("nothing to read: source was null");
        }
        Transcoder reader = new Transcoder(source);
        try {
            List<Value> values = reader.readSequence(NO_TERMINATOR);
            return new TranscodeResult.Success(BlockValue.block(values));
        } catch (MalformedSource malformed) {
            return new TranscodeResult.Failure(
                    malformed.failure, malformed.position, malformed.unclosed);
        }
    }

    // ---- the walk over the source ----------------------------------------

    /**
     * Reads values until the input ends, keeping open blocks on a stack of its
     * own rather than recursing.
     *
     * <p>Nesting comes from the source, so it is as deep as whoever wrote the
     * source made it. Recursing here would turn deeply nested input into a
     * {@code StackOverflowError}, which is not something a script could catch
     * and not something the reader promises. The evaluator keeps its state on
     * the heap for the same reason; so does this.
     */
    private List<Value> readSequence(int terminator) {
        Deque<OpenLevel> enclosing = new ArrayDeque<>();
        List<Value> values = new ArrayList<>();
        int closing = terminator;
        Datatype collecting = Datatype.BLOCK;

        while (true) {
            skipIgnorable();
            int next = peek();

            if (next == END_OF_INPUT) {
                if (closing != NO_TERMINATOR) {
                    throw failure(SyntaxFailure.MISSING_CLOSE, delimiterFor(closing));
                }
                return values;
            }

            if (next == '[' || next == '(') {
                if (enclosing.size() >= MAXIMUM_NESTING) {
                    throw failure(SyntaxFailure.NESTING_TOO_DEEP, delimiterFor(next));
                }
                advance();
                enclosing.push(new OpenLevel(values, closing, collecting));
                values = new ArrayList<>();
                closing = next == '[' ? ']' : ')';
                collecting = next == '[' ? Datatype.BLOCK : Datatype.PAREN;
                continue;
            }

            if (isClosingDelimiter(next)) {
                if (next != closing) {
                    throw failure(closing == NO_TERMINATOR
                            ? SyntaxFailure.EXTRA_CLOSE
                            : SyntaxFailure.MISMATCHED_CLOSE, delimiterFor(next));
                }
                advance();
                if (enclosing.isEmpty()) {
                    return values;
                }
                Value finished = collecting == Datatype.PAREN
                        ? BlockValue.paren(values)
                        : BlockValue.block(values);
                OpenLevel parent = enclosing.pop();
                values = parent.values();
                closing = parent.closing();
                collecting = parent.collecting();
                values.add(finished);
                continue;
            }

            values.add(readValue());
        }
    }

    /** A block left open while its contents are read. */
    private record OpenLevel(List<Value> values, int closing, Datatype collecting) {
    }

    private Value readValue() {
        int next = peek();
        return switch (next) {
            case '"' -> readQuotedString();
            case '{' -> readBracedString();
            case '#' -> readHashPrefixed();
            case '<' -> readAngled();
            case '%' -> readFile();
            case '$' -> readMoney();
            default -> classify(readLexeme());
        };
    }

    private void skipIgnorable() {
        while (true) {
            int next = peek();
            if (next == END_OF_INPUT) {
                return;
            }
            if (next == ';') {
                while (peek() != END_OF_INPUT && peek() != '\n') {
                    advance();
                }
                continue;
            }
            if (Character.isWhitespace(next) || next == ',') {
                advance();
                continue;
            }
            return;
        }
    }

    // ---- strings ---------------------------------------------------------

    private StringValue readQuotedString() {
        advance();
        StringBuilder text = new StringBuilder();
        while (true) {
            int next = peek();
            if (next == END_OF_INPUT || next == '\n') {
                throw failure(SyntaxFailure.UNTERMINATED_STRING, OpenDelimiter.QUOTE);
            }
            advance();
            if (next == '"') {
                return StringValue.of(text.toString());
            }
            if (next == '^') {
                text.appendCodePoint(readEscape());
            } else {
                text.appendCodePoint(next);
            }
        }
    }

    /** Braced strings nest and may span lines, which is what they are for. */
    private StringValue readBracedString() {
        advance();
        StringBuilder text = new StringBuilder();
        int depth = 1;
        while (true) {
            int next = peek();
            if (next == END_OF_INPUT) {
                throw failure(SyntaxFailure.UNTERMINATED_STRING, OpenDelimiter.BRACE);
            }
            advance();
            if (next == '^') {
                text.appendCodePoint(readEscape());
                continue;
            }
            if (next == '{') {
                depth++;
            } else if (next == '}') {
                depth--;
                if (depth == 0) {
                    return StringValue.of(text.toString());
                }
            }
            text.appendCodePoint(next);
        }
    }

    private int readEscape() {
        int escaped = peek();
        if (escaped == END_OF_INPUT) {
            throw failure(SyntaxFailure.INVALID_ESCAPE, null);
        }
        advance();
        return switch (escaped) {
            case '/' -> '\n';
            case '-' -> '\t';
            case '"' -> '"';
            case '^' -> '^';
            case '{' -> '{';
            case '}' -> '}';
            case '@' -> 0;
            case '(' -> readParenthesisedEscape();
            default -> {
                if (escaped >= 'A' && escaped <= 'Z') {
                    yield escaped - 'A' + 1;
                }
                if (escaped >= 'a' && escaped <= 'z') {
                    yield escaped - 'a' + 1;
                }
                throw failure(SyntaxFailure.INVALID_ESCAPE, null);
            }
        };
    }

    private int readParenthesisedEscape() {
        StringBuilder digits = new StringBuilder();
        while (peek() != ')' && peek() != END_OF_INPUT) {
            digits.appendCodePoint(peek());
            advance();
        }
        if (peek() == END_OF_INPUT) {
            throw failure(SyntaxFailure.INVALID_ESCAPE, null);
        }
        advance();
        String name = digits.toString();
        // The named forms REBOL documents, then a hexadecimal codepoint.
        // Uppercase and lowercase are equivalent.
        try {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "null" -> 0;
                case "line" -> '\n';
                case "tab" -> '\t';
                case "page" -> 12;
                case "esc" -> 27;
                case "back" -> 8;
                case "del" -> 127;
                default -> Integer.parseInt(name, 16);
            };
        } catch (NumberFormatException notHexadecimal) {
            throw failure(SyntaxFailure.INVALID_ESCAPE, null);
        }
    }

    // ---- the forms with their own opening character ----------------------

    private Value readHashPrefixed() {
        int following = peekAt(1);
        if (following == '"') {
            advance();
            return readCharacter();
        }
        if (following == '{') {
            advance();
            return readBinary();
        }
        if (following == '[') {
            return readConstruct();
        }
        String lexeme = readLexeme();
        if (lexeme.length() < 2) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        return WordValue.of(lexeme.substring(1), Datatype.ISSUE);
    }

    /**
     * Construction syntax: {@code #[unset!]}, {@code #[none]}, {@code #[true]}.
     *
     * <p>The forms MOLD produces for values with no literal spelling of their
     * own. Only the self-contained ones can be read back: a value that refers
     * to something live, such as a native or a host object, cannot be
     * reconstructed by a reader that has no context to resolve it against, and
     * having no context is deliberate.
     */
    private Value readConstruct() {
        advance();
        advance();
        List<Value> contents = readSequence(']');
        if (contents.isEmpty()) {
            throw failure(SyntaxFailure.INVALID_DATATYPE, null);
        }
        Value first = contents.get(0);
        if (contents.size() == 1 && first instanceof WordValue word) {
            return switch (word.canonical()) {
                case "true" -> LogicValue.yes();
                case "false" -> LogicValue.no();
                case "none" -> NoneValue.none();
                default -> throw failure(SyntaxFailure.INVALID_DATATYPE, null);
            };
        }
        if (contents.size() == 1 && first instanceof DatatypeValue named) {
            return switch (named.represents()) {
                case UNSET -> UnsetValue.unset();
                case NONE -> NoneValue.none();
                default -> throw failure(SyntaxFailure.INVALID_DATATYPE, null);
            };
        }
        throw failure(SyntaxFailure.INVALID_DATATYPE, null);
    }

    private CharacterValue readCharacter() {
        advance();
        int next = peek();
        if (next == END_OF_INPUT) {
            throw failure(SyntaxFailure.UNTERMINATED_STRING, OpenDelimiter.QUOTE);
        }
        advance();
        int codepoint = next == '^' ? readEscape() : next;
        if (peek() != '"') {
            throw failure(SyntaxFailure.UNTERMINATED_STRING, OpenDelimiter.QUOTE);
        }
        advance();
        return CharacterValue.of(codepoint);
    }

    private BinaryValue readBinary() {
        advance();
        List<Integer> octets = new ArrayList<>();
        int pendingDigit = -1;
        while (true) {
            int next = peek();
            if (next == END_OF_INPUT) {
                throw failure(SyntaxFailure.MISSING_CLOSE, OpenDelimiter.BINARY_BRACE);
            }
            advance();
            if (next == '}') {
                if (pendingDigit >= 0) {
                    throw failure(SyntaxFailure.INVALID_BINARY, OpenDelimiter.BINARY_BRACE);
                }
                return BinaryValue.of(octets.stream().mapToInt(Integer::intValue).toArray());
            }
            if (Character.isWhitespace(next)) {
                continue;
            }
            int digit = Character.digit(next, 16);
            if (digit < 0) {
                throw failure(SyntaxFailure.INVALID_BINARY, OpenDelimiter.BINARY_BRACE);
            }
            if (pendingDigit < 0) {
                pendingDigit = digit;
            } else {
                octets.add(pendingDigit * 16 + digit);
                pendingDigit = -1;
            }
        }
    }

    /** A tag if a closing angle follows, otherwise one of the comparison words. */
    private Value readAngled() {
        int following = peekAt(1);
        if (following == '=' || following == '>') {
            return classify(readLexeme());
        }
        int scout = position + 1;
        while (scout < codepoints.length && codepoints[scout] != '>') {
            scout++;
        }
        if (scout >= codepoints.length) {
            return classify(readLexeme());
        }
        advance();
        StringBuilder text = new StringBuilder();
        while (peek() != '>') {
            text.appendCodePoint(peek());
            advance();
        }
        advance();
        return StringValue.of(text.toString(), Datatype.TAG);
    }

    private StringValue readFile() {
        advance();
        if (peek() == '"') {
            return readQuotedString().as(Datatype.FILE);
        }
        StringBuilder text = new StringBuilder();
        while (peek() != END_OF_INPUT && !endsLexeme(peek())) {
            text.appendCodePoint(peek());
            advance();
        }
        return StringValue.of(text.toString(), Datatype.FILE);
    }

    private MoneyValue readMoney() {
        String lexeme = readLexeme();
        String digits = lexeme.substring(1);
        try {
            return MoneyValue.of(new BigDecimal(digits));
        } catch (NumberFormatException notANumber) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
    }

    // ---- lexemes and their classification --------------------------------

    private String readLexeme() {
        StringBuilder lexeme = new StringBuilder();
        while (peek() != END_OF_INPUT && !endsLexeme(peek())) {
            lexeme.appendCodePoint(peek());
            advance();
        }
        if (lexeme.isEmpty()) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        return lexeme.toString();
    }

    private static boolean endsLexeme(int codepoint) {
        return Character.isWhitespace(codepoint)
                || codepoint == '['
                || codepoint == ']'
                || codepoint == '('
                || codepoint == ')'
                || codepoint == '"'
                || codepoint == '{'
                || codepoint == '}'
                || codepoint == ';'
                || codepoint == ',';
    }

    private static final Pattern URL = Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]*:.+");
    private static final Pattern SLASHED_DATE =
            Pattern.compile("\\d{1,4}/[A-Za-z0-9]+/\\d{1,4}");
    private static final Pattern HYPHENATED_DATE =
            Pattern.compile("(\\d{1,4})-([A-Za-z]{3,}|\\d{1,2})-(\\d{1,4})");
    private static final Pattern PAIR = Pattern.compile("([-+]?\\d+)[xX]([-+]?\\d+)");
    private static final Pattern TIME =
            Pattern.compile("([-+]?\\d+):(\\d{1,2})(?::(\\d{1,2}(?:\\.\\d+)?))?");
    private static final Pattern TUPLE = Pattern.compile("\\d+(?:\\.\\d+){2,}");
    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d+");
    private static final Pattern DECIMAL =
            Pattern.compile("[-+]?(?:\\d+\\.\\d*|\\.\\d+|\\d+)(?:[eE][-+]?\\d+)?");
    private static final Pattern PERCENT =
            Pattern.compile("([-+]?(?:\\d+\\.\\d*|\\.\\d+|\\d+))%");
    private static final Pattern DATATYPE = Pattern.compile("([a-zA-Z][a-zA-Z0-9-]*)!");
    private static final String[] MONTH_NAMES = {
        "jan", "feb", "mar", "apr", "may", "jun",
        "jul", "aug", "sep", "oct", "nov", "dec"
    };

    private Value classify(String lexeme) {
        // Slashes alone are ordinary words: / divides and // takes a
        // remainder. Only a slash with a name after it is a refinement, and
        // only a slash with something before it makes a path.
        if (lexeme.chars().allMatch(character -> character == '/')) {
            return WordValue.of(lexeme);
        }
        if (lexeme.startsWith("/") && lexeme.indexOf('/', 1) < 0) {
            return WordValue.of(lexeme.substring(1), Datatype.REFINEMENT);
        }
        if (URL.matcher(lexeme).matches() && !lexeme.endsWith(":")) {
            return StringValue.of(lexeme, Datatype.URL);
        }
        if (SLASHED_DATE.matcher(lexeme).matches()) {
            return readDate(lexeme, "/");
        }
        if (lexeme.indexOf('/') >= 0) {
            return readPath(lexeme);
        }
        if (lexeme.endsWith(":") && lexeme.length() > 1) {
            return WordValue.of(lexeme.substring(0, lexeme.length() - 1), Datatype.SET_WORD);
        }
        if (lexeme.startsWith(":") && lexeme.length() > 1) {
            return WordValue.of(lexeme.substring(1), Datatype.GET_WORD);
        }
        if (lexeme.startsWith("'") && lexeme.length() > 1) {
            return WordValue.of(lexeme.substring(1), Datatype.LIT_WORD);
        }
        if (lexeme.indexOf('@') > 0) {
            return StringValue.of(lexeme, Datatype.EMAIL);
        }
        return classifyScalarOrWord(lexeme);
    }

    private Value classifyScalarOrWord(String lexeme) {
        var datatypeMatch = DATATYPE.matcher(lexeme);
        if (datatypeMatch.matches()) {
            return readDatatype(datatypeMatch.group(1));
        }
        if (HYPHENATED_DATE.matcher(lexeme).matches()) {
            return readDate(lexeme, "-");
        }
        var pair = PAIR.matcher(lexeme);
        if (pair.matches()) {
            return PairValue.of(Long.parseLong(pair.group(1)), Long.parseLong(pair.group(2)));
        }
        var time = TIME.matcher(lexeme);
        if (time.matches()) {
            return readTime(time.group(1), time.group(2), time.group(3));
        }
        if (TUPLE.matcher(lexeme).matches()) {
            return readTuple(lexeme);
        }
        var percent = PERCENT.matcher(lexeme);
        if (percent.matches()) {
            return DecimalValue.percent(Double.parseDouble(percent.group(1)) / 100.0);
        }
        if (INTEGER.matcher(lexeme).matches()) {
            try {
                return IntegerValue.of(Long.parseLong(lexeme));
            } catch (NumberFormatException tooBig) {
                throw failure(SyntaxFailure.INTEGER_OUT_OF_RANGE, null);
            }
        }
        if (DECIMAL.matcher(lexeme).matches() && lexeme.matches(".*[\\d].*")) {
            return DecimalValue.of(Double.parseDouble(lexeme));
        }
        return WordValue.of(lexeme);
    }

    private Value readDatatype(String name) {
        for (Datatype candidate : Datatype.values()) {
            if (candidate.spelling().equalsIgnoreCase(name)) {
                return DatatypeValue.of(candidate);
            }
        }
        // number! and series! name several datatypes rather than one, and
        // function specs use them wherever a datatype would do.
        return Typeset.named(name)
                .map(typeset -> (Value) TypesetValue.of(typeset))
                .orElseThrow(() -> failure(SyntaxFailure.INVALID_DATATYPE, null));
    }

    private Value readPath(String lexeme) {
        Datatype pathType = Datatype.PATH;
        String body = lexeme;
        if (body.endsWith(":")) {
            pathType = Datatype.SET_PATH;
            body = body.substring(0, body.length() - 1);
        } else if (body.startsWith(":")) {
            pathType = Datatype.GET_PATH;
            body = body.substring(1);
        } else if (body.startsWith("'")) {
            pathType = Datatype.LIT_PATH;
            body = body.substring(1);
        }
        List<Value> segments = new ArrayList<>();
        for (String segment : body.split("/", -1)) {
            if (segment.isEmpty()) {
                throw failure(SyntaxFailure.INVALID_LEXEME, null);
            }
            segments.add(readPathSegment(segment));
        }
        return BlockValue.path(segments, pathType);
    }

    private Value readPathSegment(String segment) {
        if (segment.startsWith(":") && segment.length() > 1) {
            return WordValue.of(segment.substring(1), Datatype.GET_WORD);
        }
        if (segment.startsWith("'") && segment.length() > 1) {
            return WordValue.of(segment.substring(1), Datatype.LIT_WORD);
        }
        if (INTEGER.matcher(segment).matches()) {
            return IntegerValue.of(Long.parseLong(segment));
        }
        return WordValue.of(segment);
    }

    private Value readTuple(String lexeme) {
        String[] parts = lexeme.split("\\.");
        int[] segments = new int[parts.length];
        for (int index = 0; index < parts.length; index++) {
            int segment = Integer.parseInt(parts[index]);
            if (segment < 0 || segment > 255) {
                throw failure(SyntaxFailure.INVALID_LEXEME, null);
            }
            segments[index] = segment;
        }
        if (segments.length < TupleValue.MINIMUM_SEGMENTS
                || segments.length > TupleValue.MAXIMUM_SEGMENTS) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        return TupleValue.of(segments);
    }

    private Value readTime(String hours, String minutes, String seconds) {
        long wholeSeconds = 0;
        long nanoseconds = 0;
        if (seconds != null) {
            double asDouble = Double.parseDouble(seconds);
            wholeSeconds = (long) asDouble;
            nanoseconds = Math.round((asDouble - wholeSeconds) * 1_000_000_000L);
        }
        boolean negative = hours.startsWith("-");
        long magnitude = Math.abs(Long.parseLong(hours));
        TimeValue positive = TimeValue.of(
                magnitude, Long.parseLong(minutes), wholeSeconds, nanoseconds);
        return negative ? TimeValue.ofNanoseconds(-positive.nanoseconds()) : positive;
    }

    private Value readDate(String lexeme, String separator) {
        String[] parts = lexeme.split(Pattern.quote(separator));
        int day = Integer.parseInt(parts[0]);
        int month = monthNumber(parts[1]);
        int year = Integer.parseInt(parts[2]);
        if (year < 100) {
            year += year < 50 ? 2000 : 1900;
        }
        return DateValue.of(year, month, day);
    }

    private int monthNumber(String name) {
        if (name.matches("\\d{1,2}")) {
            int numeric = Integer.parseInt(name);
            if (numeric < 1 || numeric > 12) {
                throw failure(SyntaxFailure.INVALID_LEXEME, null);
            }
            return numeric;
        }
        String lowered = name.toLowerCase(Locale.ROOT);
        for (int index = 0; index < MONTH_NAMES.length; index++) {
            if (lowered.startsWith(MONTH_NAMES[index])) {
                return index + 1;
            }
        }
        throw failure(SyntaxFailure.INVALID_LEXEME, null);
    }

    // ---- cursor ----------------------------------------------------------

    private int peek() {
        return position < codepoints.length ? codepoints[position] : END_OF_INPUT;
    }

    private int peekAt(int ahead) {
        int at = position + ahead;
        return at < codepoints.length ? codepoints[at] : END_OF_INPUT;
    }

    private void advance() {
        if (position >= codepoints.length) {
            return;
        }
        if (codepoints[position] == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        position++;
    }

    private static boolean isClosingDelimiter(int codepoint) {
        return codepoint == ']' || codepoint == ')';
    }

    private static OpenDelimiter delimiterFor(int codepoint) {
        return codepoint == ']' || codepoint == '['
                ? OpenDelimiter.BRACKET
                : OpenDelimiter.PARENTHESIS;
    }

    private MalformedSource failure(SyntaxFailure failure, OpenDelimiter unclosed) {
        return new MalformedSource(
                failure,
                new SourcePosition(line, column, position),
                Optional.ofNullable(unclosed));
    }

    /** Internal control flow. Never escapes {@link #transcode(String)}. */
    private static final class MalformedSource extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final transient SyntaxFailure failure;
        private final transient SourcePosition position;
        private final transient Optional<OpenDelimiter> unclosed;

        MalformedSource(
                SyntaxFailure failure,
                SourcePosition position,
                Optional<OpenDelimiter> unclosed) {
            super(failure.description(), null, false, false);
            this.failure = failure;
            this.position = position;
            this.unclosed = unclosed;
        }
    }
}

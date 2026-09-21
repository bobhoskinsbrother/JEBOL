package org.jebol.domain.read;

import org.jebol.domain.value.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

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

    private final List<Integer> topLevelStarts = new ArrayList<>();
    private final List<Integer> topLevelEnds = new ArrayList<>();
    private int column = 1;

    private Transcoder(String source) {
        this.codepoints = source.codePoints().toArray();
    }

    /**
     * Each top-level expression in the source, as the text it was written
     * as rather than as a re-rendering of what it means.
     *
     * <p>Reading into values and molding back is lossy, in JEBOL and in
     * R3 alike: {@code 1.7976931348623157e308} molds to fifteen digits and
     * reads back as {@code 1.#INF}, and {@code 0:0:1} comes back as
     * {@code 0:00:01}. Anything that needs one expression's source has to
     * take the text, and this is how it gets it.
     *
     * <p>Empty when the source does not read at all, rather than holding
     * the part that did. Half a script's expressions would let a caller
     * run half a script, which is the same reason {@link TranscodeResult}
     * has no partial case.
     */
    public record SourceSpan(String text, int from, int to) {
    }

    /**
     * The text of a run of consecutive top-level expressions, taken from
     * the source in one cut.
     *
     * <p>Joining the spans with a space instead would drop whatever sat
     * between them, and what sits between two expressions in a test file
     * is usually a comment. Cutting once keeps the run exactly as
     * written.
     */
    public static String textOf(String source, List<SourceSpan> spans, int from, int count) {
        if (count <= 0 || from >= spans.size()) {
            return "";
        }
        int last = Math.min(from + count, spans.size()) - 1;
        int[] codepoints = source.codePoints().toArray();
        int begins = spans.get(from).from();
        return new String(codepoints, begins, spans.get(last).to() - begins);
    }

    public static List<SourceSpan> topLevelSpans(String source) {
        if (source == null) {
            throw new IllegalArgumentException("nothing to read: source was null");
        }
        Transcoder reader = new Transcoder(source);
        try {
            reader.readSequence(NO_TERMINATOR);
        } catch (MalformedSource unreadable) {
            return List.of();
        }
        List<SourceSpan> spans = new ArrayList<>();
        for (int at = 0; at < reader.topLevelStarts.size(); at++) {
            int from = reader.topLevelStarts.get(at);
            int to = reader.topLevelEnds.get(at);
            spans.add(new SourceSpan(new String(reader.codepoints, from, to - from), from, to));
        }
        return List.copyOf(spans);
    }

    /**
     * Reads one value and stops, without looking at a character past it.
     *
     * <p>What {@code Scan_Token} does and what reading the whole source and taking
     * the first value only approximates: {@code transcode/one "1]"} is 1, and the
     * bracket closing nothing is never reached because reading stopped before it.
     *
     * <p>Answers a success holding a one-value block, or the failure if it could not
     * read even one -- which is not the same as reading none. None is a value a
     * source can genuinely hold, so `#(` has to fail rather than answer it.
     *
     * <p>The obvious substitute is a walk over successively longer prefixes, keeping
     * the longest that parses as a single value. That is a different question, and
     * it answers `'%` for `'%/` where this fails: the token boundaries are the
     * reader's, not the longest thing that happens to parse.
     */
    public static Reading read(String source, long firstLine, Extent extent) {
        if (source == null) {
            throw new IllegalArgumentException("nothing to read: source was null");
        }
        Transcoder reader = new Transcoder(source);
        reader.line = (int) firstLine;
        reader.stopAfterOneValue = extent != Extent.THE_WHOLE_SOURCE;
        reader.stopAtEveryDepth = extent == Extent.THE_FIRST_VALUE_AT_EVERY_DEPTH;
        try {
            List<Value> values = reader.readSequence(NO_TERMINATOR);
            return new Reading(
                    values,
                    Optional.empty(),
                    reader.position,
                    reader.line,
                    reader.lineStarts);
        } catch (MalformedSource malformed) {
            return new Reading(
                    List.copyOf(reader.valuesTakenAtTheTopLevel),
                    Optional.of(new TranscodeResult.Failure(
                            malformed.failure, malformed.position, malformed.unclosed,
                            malformed.tokenKind, malformed.fragment,
                            malformed.offendingText)),
                    reader.position,
                    reader.line,
                    reader.lineStarts);
        }
    }

    public enum Extent {
        THE_WHOLE_SOURCE,
        THE_FIRST_VALUE,
        THE_FIRST_VALUE_AT_EVERY_DEPTH
    }

    public record Reading(
            List<Value> valuesReadBeforeStopping,
            Optional<TranscodeResult.Failure> whyItStopped,
            int endedAtCodePoint,
            int lineEndedOn,
            Set<Integer> positionsThatBeginALine) {

        /** The block those values make, laid out the way the source was. */
        public BlockValue asABlock() {
            return withItsLineStarts(
                    BlockValue.block(valuesReadBeforeStopping), positionsThatBeginALine);
        }
    }

    /** Reads every value in the source, or reports the first failure. */
    public static TranscodeResult transcode(String source) {
        return transcode(source, 1);
    }

    /** Lines counted from somewhere other than one, for a caller reading a fragment. */
    public static TranscodeResult transcode(String source, long firstLine) {
        Reading reading = read(source, firstLine, Extent.THE_WHOLE_SOURCE);
        return reading.whyItStopped()
                .<TranscodeResult>map(failure -> failure)
                .orElseGet(() -> new TranscodeResult.Success(reading.asABlock()));
    }

    private List<Value> readSequence(int terminator) {
        Deque<OpenLevel> enclosing = new ArrayDeque<>();
        List<Value> values = new ArrayList<>();
        int closing = terminator;
        Datatype collecting = Datatype.BLOCK;

        while (true) {
            skipIgnorable();
            int next = peek();
            boolean outermost = terminator == NO_TERMINATOR
                    && closing == NO_TERMINATOR
                    && enclosing.isEmpty();
            int began = position;

            if (next == END_OF_INPUT) {
                if (closing != NO_TERMINATOR) {
                    throw failureReading(SyntaxFailure.MISSING_CLOSE,
                            "end-of-script", String.valueOf((char) closing));
                }
                return values;
            }

            if (next == '[' || next == '(') {
                if (enclosing.size() >= MAXIMUM_NESTING) {
                    throw failure(SyntaxFailure.NESTED_PAST_THE_STACK, delimiterFor(next));
                }
                advance();
                if (outermost) {
                    topLevelStarts.add(began);
                }
                recordWhetherTheValueBeginsALine(values.size() + 1);
                enclosing.push(new OpenLevel(values, closing, collecting, lineStarts));
                values = new ArrayList<>();
                lineStarts = new LinkedHashSet<>();
                closing = next == '[' ? ']' : ')';
                collecting = next == '[' ? Datatype.BLOCK : Datatype.PAREN;
                continue;
            }

            if (isClosingDelimiter(next)) {
                if (next != closing) {
                    throw failureReading(closing == NO_TERMINATOR
                                    ? SyntaxFailure.EXTRA_CLOSE
                                    : SyntaxFailure.MISMATCHED_CLOSE,
                            next == ']' ? "end-of-block" : "end-of-paren",
                            String.valueOf((char) (closing == NO_TERMINATOR
                                    ? (next == ']' ? '[' : '(')
                                    : closing)));
                }
                advance();
                crossedALine = false;
                if (enclosing.isEmpty()) {
                    return values;
                }
                Value finished = withItsLineStarts(collecting == Datatype.PAREN
                        ? BlockValue.paren(values)
                        : BlockValue.block(values), lineStarts);
                OpenLevel parent = enclosing.pop();
                values = parent.values();
                closing = parent.closing();
                collecting = parent.collecting();
                lineStarts = parent.lineStarts();
                values.add(finished);
                if (terminator == NO_TERMINATOR && closing == NO_TERMINATOR
                        && enclosing.isEmpty()) {
                    topLevelEnds.add(position);
                    valuesTakenAtTheTopLevel.add(finished);
                    if (stopAfterOneValue) {
                        return values;
                    }
                }
                continue;
            }

            Value read = readValue();
            if (read == null) {
                throw new IllegalStateException(
                        "the reader answered null at " + line + ":" + column
                                + " rather than a value or an error. Absence is not a "
                                + "value here: it travels as far as whatever copies "
                                + "the block and surfaces there instead");
            }
            values.add(read);
            recordWhetherTheValueBeginsALine(values.size());
            if (outermost) {
                topLevelStarts.add(began);
                topLevelEnds.add(position);
                valuesTakenAtTheTopLevel.add(read);
            }
            if (stopAtEveryDepth) {
                return closingEveryLevelLeftOpen(enclosing, values, collecting);
            }
            if (outermost && stopAfterOneValue) {
                return values;
            }
        }
    }

    private List<Value> closingEveryLevelLeftOpen(
            Deque<OpenLevel> enclosing, List<Value> innermost, Datatype innermostKind) {

        List<Value> values = innermost;
        Datatype collecting = innermostKind;
        while (!enclosing.isEmpty()) {
            Value finished = withItsLineStarts(collecting == Datatype.PAREN
                    ? BlockValue.paren(values)
                    : BlockValue.block(values), lineStarts);
            OpenLevel parent = enclosing.pop();
            values = parent.values();
            collecting = parent.collecting();
            lineStarts = parent.lineStarts();
            values.add(finished);
        }
        return values;
    }

    private boolean stopAfterOneValue;

    private boolean stopAtEveryDepth;

    private final List<Value> valuesTakenAtTheTopLevel = new ArrayList<>();

    private record OpenLevel(List<Value> values, int closing, Datatype collecting,
            Set<Integer> lineStarts) {
    }

    private boolean crossedALine;

    private Set<Integer> lineStarts = new LinkedHashSet<>();

    private List<Value> theContentsOfANestedForm(int closing) {
        boolean aLineFeedWasWaiting = crossedALine;
        Set<Integer> theMarksAroundIt = lineStarts;
        lineStarts = new LinkedHashSet<>();
        try {
            return readSequence(closing);
        } finally {
            lineStarts = theMarksAroundIt;
            crossedALine = aLineFeedWasWaiting;
        }
    }

    private void recordWhetherTheValueBeginsALine(int oneBasedPosition) {
        if (crossedALine) {
            lineStarts.add(oneBasedPosition);
            crossedALine = false;
        }
    }

    private static BlockValue withItsLineStarts(BlockValue block, Set<Integer> starts) {
        for (int position : starts) {
            block.storage().setLineBreakAt(position, true);
        }
        return block;
    }

    private Value readValue() {
        int next = peek();
        return switch (next) {
            case '"' -> readQuotedString();
            case '{' -> readBracedString();
            case '#' -> readHashPrefixed();
            case '<' -> readAngled();
            case '%' -> readFileOrPercentWord();
            case '@' -> readRef();
            case '$' -> readMoney();
            default -> readSignedOrLexeme();
        };
    }

    private boolean beginsNoFilename(int following) {
        return following == END_OF_INPUT
                || Character.isWhitespace(following)
                || isClosingDelimiter(following)
                || following == ':';
    }

    private static boolean isHexDigit(int letter) {
        return (letter >= '0' && letter <= '9')
                || (letter >= 'a' && letter <= 'f')
                || (letter >= 'A' && letter <= 'F');
    }

    private Value readFileOrPercentWord() {
        int percents = 0;
        while (peekAt(percents) == '%') {
            percents++;
        }
        if (peekAt(percents) == '{') {
            return readRawString(percents);
        }
        if (beginsNoFilename(peekAt(1))) {
            advance();
            return percentWord("%");
        }
        if (peekAt(1) == '%') {
            if (isHexDigit(peekAt(2)) && isHexDigit(peekAt(3))) {
                return readFile();
            }
            if (!beginsNoFilename(peekAt(2))) {
                throw failure(SyntaxFailure.INVALID_LEXEME, null);
            }
            advance();
            advance();
            return percentWord("%%");
        }
        return readFile();
    }

    private Value readRawString(int percents) {
        for (int skipped = 0; skipped <= percents; skipped++) {
            advance();
        }
        StringBuilder held = new StringBuilder();
        while (peek() != END_OF_INPUT) {
            if (peek() == '}') {
                int following = 0;
                while (peekAt(1 + following) == '%') {
                    following++;
                }
                if (following == percents) {
                    for (int skipped = 0; skipped <= percents; skipped++) {
                        advance();
                    }
                    return StringValue.of(held.toString());
                }
                if (following > percents) {
                    throw failure(SyntaxFailure.INVALID_LEXEME, null);
                }
            }
            held.appendCodePoint(peek());
            advance();
        }
        throw failure(SyntaxFailure.UNTERMINATED_STRING, null);
    }

    private Value percentWord(String spelling) {
        if (peek() == ':') {
            advance();
            return WordValue.of(spelling, Datatype.SET_WORD);
        }
        return WordValue.of(spelling);
    }

    private StringValue readRef() {
        advance();
        boolean hasName = peek() != END_OF_INPUT
                && !Character.isWhitespace(peek())
                && !isClosingDelimiter(peek());
        return StringValue.of(hasName ? readLexeme() : "", Datatype.REF);
    }

    private boolean isDelimiterOrSpace(int character) {
        return Character.isWhitespace(character)
                || isClosingDelimiter(character)
                || character == '[' || character == '(' || character == ';';
    }

    private void skipIgnorable() {
        while (true) {
            int next = peek();
            if (next == END_OF_INPUT) {
                return;
            }
            if (next == ';') {
                while (peek() != END_OF_INPUT && !endsAComment(peek())) {
                    advance();
                }
                continue;
            }
            if (Character.isWhitespace(next)) {
                if (next == '\n') {
                    crossedALine = true;
                }
                advance();
                continue;
            }
            return;
        }
    }

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
                text.appendCodePoint(readEscapeRefusingInvalidCodePoints());
            } else {
                text.appendCodePoint(next);
            }
        }
    }

    private int readEscapeRefusingInvalidCodePoints() {
        int codepoint = readEscape();
        if (codepoint > LAST_UNICODE_CODEPOINT || isSurrogate(codepoint)) {
            throw failureReading(SyntaxFailure.INVALID_LEXEME, "string");
        }
        return codepoint;
    }

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
                text.appendCodePoint(readEscapeRefusingInvalidCodePoints());
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
            case ' ' -> ' ';
            case '(' -> readParenthesisedEscape();
            case '~' -> 127;
            default -> {
                if (escaped >= '@' && escaped <= '_') {
                    yield escaped - '@';
                }
                if (escaped >= 'a' && escaped <= 'z') {
                    yield escaped - 'a' + 1;
                }
                yield escaped;
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
        if (following == '(') {
            return readConstruct();
        }
        if (following == '[') {
            return readMap();
        }
        String lexeme = readLexeme();
        if (lexeme.length() < 2) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        return WordValue.of(lexeme.substring(1), Datatype.ISSUE);
    }

    private Value readConstruct() {
        advance();
        advance();
        List<Value> contents;
        try {
            contents = theContentsOfANestedForm(')');
        } catch (MalformedSource unreadable) {
            if (unreadable.failure == SyntaxFailure.MISSING_CLOSE) {
                throw unreadable;
            }
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        if (contents.isEmpty()) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        Value first = contents.getFirst();

        if (!(first instanceof WordValue leading)) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        if (contents.size() == 1) {
            Value simple = switch (leading.canonical()) {
                case "true" -> LogicValue.yes();
                case "false" -> LogicValue.no();
                case "none" -> NoneValue.none();
                case "unset" -> UnsetValue.unset();
                default -> null;
            };
            if (simple != null) {
                return simple;
            }
        }
        if (namesAVector(leading, contents.size())) {
            return org.jebol.domain.value.VectorSpec.readConstruction(contents)
                    .orElseThrow(() -> failure(SyntaxFailure.MALCONSTRUCT, null));
        }
        Value resolved = datatypeNamed(leading);
        if (contents.size() == 1) {
            return resolved;
        }
        if (!(resolved instanceof DatatypeValue built)) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        return builtFrom(built.represents(), contents.subList(1, contents.size()));
    }

    private Value readMap() {
        advance();
        advance();
        List<Value> pairs = theContentsOfANestedForm(']');
        if (pairs.size() % 2 != 0) {
            throw failure(SyntaxFailure.INVALID_ARG, null);
        }
        return MapValue.of(pairs);
    }

    private static boolean namesAVector(WordValue leading, int howManyParts) {
        if ("vector!".equals(leading.canonical())) {
            return howManyParts > 1;
        }
        return org.jebol.domain.value.VectorKind.named(leading.spelling()).isPresent();
    }

    private Value datatypeNamed(WordValue word) {
        if (!word.spelling().endsWith("!")) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        return readDatatype(word.spelling().substring(0, word.spelling().length() - 1));
    }

    private static final java.util.Set<Datatype> HAVE_NO_MAKER =
            Typeset.ANY_WORD.membersAnd(
                    Datatype.INTEGER, Datatype.MONEY, Datatype.CHAR,
                    Datatype.FRAME, Datatype.PORT, Datatype.HANDLE,
                    Datatype.LIBRARY, Datatype.UTYPE);

    private static final java.util.Set<Datatype> READ_AS_TEXT_OR_BYTES =
            everyStringAndTheBinary();

    private static java.util.Set<Datatype> everyStringAndTheBinary() {
        java.util.Set<Datatype> accepted =
                java.util.EnumSet.copyOf(Typeset.ANY_STRING.members());
        accepted.add(Datatype.BINARY);
        return java.util.Set.copyOf(accepted);
    }

    private static final java.util.Set<Datatype> READ_AS_A_BLOCK =
            Typeset.ANY_BLOCK.members();

    private Value textOrBytesStandingWhereItWasTold(
            Datatype datatype, List<Value> contents) {

        boolean shapeTheCAccepts = contents.size() == 1
                || (contents.size() == 2 && contents.get(1) instanceof IntegerValue);
        if (!shapeTheCAccepts) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        Value whole = builtFrom(datatype, List.of(contents.getFirst()));
        return contents.size() == 1
                ? whole
                : standingWhereItWasTold(whole, contents.get(1));
    }

    private Value standingWhereItWasTold(Value whole, Value position) {
        if (!(whole instanceof SeriesValue series)) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        if (!(position instanceof IntegerValue at)) {
            return series;
        }
        long tail = series.storageLength() + 1L;
        long counted = at.magnitude() - 1;
        return series.atIndex(
                (int) (counted < 0 || counted > tail - 1 ? tail : counted + 1));
    }

    private Value builtFrom(Datatype datatype, List<Value> contents) {
        if (HAVE_NO_MAKER.contains(datatype)) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        if (datatype == Datatype.BITSET && contents.size() == 2
                && contents.getFirst() instanceof WordValue complementing
                && complementing.canonical().equals("not")
                && contents.get(1) instanceof BinaryValue octets) {
            return BitsetValue.of(bytesOf(octets)).complemented();
        }
        if (READ_AS_TEXT_OR_BYTES.contains(datatype) && contents.size() != 1) {
            return textOrBytesStandingWhereItWasTold(datatype, contents);
        }
        if (READ_AS_A_BLOCK.contains(datatype) && contents.size() > 1) {
            return standingWhereItWasTold(
                    builtFrom(datatype, List.of(contents.getFirst())), contents.get(1));
        }
        if (contents.size() == 2 && contents.get(1) instanceof IntegerValue
                && datatype.isSeries() && !alwaysReadsABlock(datatype)) {
            return standingWhereItWasTold(
                    builtFrom(datatype, List.of(contents.getFirst())), contents.get(1));
        }
        if (datatype == Datatype.BITSET && contents.size() != 1) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        if (contents.size() != 1) {
            return madeByTheEvaluator(datatype, contents);
        }
        Value only = contents.getFirst();
        return switch (datatype) {
            case DECIMAL -> only instanceof IntegerValue whole
                    ? DecimalValue.of(whole.magnitude())
                    : requireDatatype(only, Datatype.DECIMAL);
            case OBJECT -> objectFrom(only);
            case BITSET -> only instanceof BinaryValue octets
                    ? BitsetValue.of(bytesOf(octets))
                    : requireDatatype(only, Datatype.BITSET);
            case STRING, FILE, URL, EMAIL, TAG, REF -> switch (only) {
                case StringValue text -> text.as(datatype);
                case BinaryValue bytes -> madeByTheEvaluator(datatype, contents);
                default -> requireDatatype(only, datatype);
            };
            case BLOCK, PAREN, PATH, SET_PATH, GET_PATH, LIT_PATH, HASH ->
                    only instanceof BlockValue items
                            ? items.as(datatype)
                            : requireDatatype(only, datatype);
            case FUNCTION, CLOSURE -> {
                if (functionBuilder == null
                        || !(only instanceof BlockValue definition)
                        || definition.remaining().size() != 2
                        || !(definition.remaining().get(0) instanceof BlockValue spec)
                        || !(definition.remaining().get(1) instanceof BlockValue body)
                        || spec.datatype() != Datatype.BLOCK
                        || body.datatype() != Datatype.BLOCK) {
                    throw failure(SyntaxFailure.MALCONSTRUCT, null);
                }
                try {
                    yield functionBuilder.apply(spec, body);
                } catch (RuntimeException badSpec) {
                    throw failure(SyntaxFailure.MALCONSTRUCT, null);
                }
            }
            default -> madeByTheEvaluator(datatype, contents);
        };
    }

    private Value madeByTheEvaluator(Datatype datatype, List<Value> contents) {
        if (maker == null) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        Value specification = !alwaysReadsABlock(datatype)
                && (contents.size() == 1 || readsOneLooseValue(datatype))
                ? contents.getFirst()
                : BlockValue.block(contents);
        Value made;
        try {
            made = maker.apply(datatype, specification);
        } catch (RuntimeException refused) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        if (made == null) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        return made;
    }

    private static boolean readsOneLooseValue(Datatype datatype) {
        return datatype == Datatype.TIME;
    }

    private static boolean alwaysReadsABlock(Datatype datatype) {
        return datatype == Datatype.IMAGE;
    }

    private static volatile
            java.util.function.BiFunction<Datatype, Value, Value> maker;

    public static void makeValuesWith(
            java.util.function.BiFunction<Datatype, Value, Value> builder) {
        maker = builder;
    }

    private static volatile
            java.util.function.BiFunction<BlockValue, BlockValue, Value> functionBuilder;

    public static void buildFunctionsWith(
            java.util.function.BiFunction<BlockValue, BlockValue, Value> builder) {
        functionBuilder = builder;
    }

    private static byte[] bytesOf(BinaryValue binary) {
        byte[] octets = new byte[binary.storageLength() - binary.index() + 1];
        for (int at = 0; at < octets.length; at++) {
            octets[at] = (byte) binary.storage().at(binary.index() + at);
        }
        return octets;
    }

    private Value objectFrom(Value contents) {
        if (!(contents instanceof BlockValue fields)) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        Context built = Context.root();
        List<Value> items = fields.remaining();
        for (int at = 0; at < items.size(); at++) {
            if (!(items.get(at) instanceof WordValue name)
                    || name.datatype() != Datatype.SET_WORD) {
                throw failure(SyntaxFailure.MALCONSTRUCT, null);
            }
            at++;
            built.set(name.spelling(), at < items.size()
                    ? items.get(at)
                    : NoneValue.none());
        }
        return new ObjectValue(built);
    }

    private Value requireDatatype(Value value, Datatype wanted) {
        if (value.datatype() != wanted) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        return value;
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
        if (codepoint > LAST_UNICODE_CODEPOINT || isSurrogate(codepoint)) {
            throw failureReading(SyntaxFailure.INVALID_LEXEME, "char");
        }
        return CharacterValue.of(codepoint);
    }

    private static final int LAST_UNICODE_CODEPOINT = 0x10FFFF;

    private static boolean isSurrogate(int codepoint) {
        return codepoint >= 0xD800 && codepoint <= 0xDFFF;
    }

    private static final int BITS = 2;
    private static final int HEXADECIMAL = 16;
    private static final int BASE_64 = 64;

    private BinaryValue readBinary() {
        return readBinary(HEXADECIMAL);
    }

    private BinaryValue readBinary(int base) {
        advance();
        StringBuilder body = new StringBuilder();
        while (true) {
            int next = peek();
            if (next == END_OF_INPUT) {
                throw failureReading(SyntaxFailure.INVALID_BINARY, "binary");
            }
            advance();
            if (next == '}') {
                return base == BASE_64
                        ? decodedBase64(body.toString())
                        : gatheredDigits(body.toString(), base);
            }
            if (Character.isWhitespace(next)) {
                continue;
            }
            if (next == ';') {
                while (peek() != END_OF_INPUT && !endsAComment(peek())) {
                    advance();
                }
                continue;
            }
            body.appendCodePoint(next);
        }
    }

    private static boolean endsAComment(int codepoint) {
        return codepoint == '\n' || codepoint == '\r';
    }

    private BinaryValue gatheredDigits(String body, int base) {
        int digitsAByte = base == BITS ? 8 : 2;
        List<Integer> octets = new ArrayList<>();
        int building = 0;
        int gathered = 0;
        for (int at = 0; at < body.length(); at++) {
            int digit = Character.digit(body.charAt(at), base);
            if (digit < 0) {
                throw failure(SyntaxFailure.INVALID_BINARY, OpenDelimiter.BINARY_BRACE);
            }
            building = building * base + digit;
            if (++gathered == digitsAByte) {
                octets.add(building);
                building = 0;
                gathered = 0;
            }
        }
        if (gathered > 0) {
            for (int missing = gathered; missing < digitsAByte; missing++) {
                building *= base;
            }
            octets.add(building);
        }
        return BinaryValue.of(octets.stream().mapToInt(Integer::intValue).toArray());
    }

    private BinaryValue decodedBase64(String body) {
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(body);
            int[] octets = new int[decoded.length];
            for (int at = 0; at < decoded.length; at++) {
                octets[at] = decoded[at] & 0xFF;
            }
            return BinaryValue.of(octets);
        } catch (IllegalArgumentException notBase64) {
            throw failure(SyntaxFailure.INVALID_BINARY, OpenDelimiter.BINARY_BRACE);
        }
    }

    private Value readBasedBinary(String spelling) {
        if (!spelling.matches("[1-9][0-9]*")) {
            throw failureReading(SyntaxFailure.INVALID_LEXEME, "integer", spelling);
        }
        int base = Integer.parseInt(spelling);
        if (base != BITS && base != HEXADECIMAL && base != BASE_64) {
            throw failureReading(SyntaxFailure.INVALID_LEXEME, "integer", spelling);
        }
        return readBinary(base);
    }

    private static final String SYMBOL_CHARACTERS = "<>=+-|~&*";

    private Value readAngled() {
        int scout = position;
        while (scout < codepoints.length
                && SYMBOL_CHARACTERS.indexOf(codepoints[scout]) >= 0) {
            scout++;
        }
        if (scout > position && scout < codepoints.length && codepoints[scout] == ':'
                && (scout + 1 >= codepoints.length
                        || isDelimiterOrSpace(codepoints[scout + 1]))) {
            StringBuilder spelling = new StringBuilder();
            while (position < scout) {
                spelling.appendCodePoint(peek());
                advance();
            }
            advance();
            return WordValue.of(spelling.toString(), Datatype.SET_WORD);
        }
        if (scout >= codepoints.length || isDelimiterOrSpace(codepoints[scout])) {
            return classify(readLexeme());
        }
        scout = position + 1;
        while (scout < codepoints.length && codepoints[scout] != '>') {
            scout++;
        }
        if (scout >= codepoints.length) {
            if (position + 1 < codepoints.length && codepoints[position + 1] == '@') {
                throw failureReading(SyntaxFailure.INVALID_LEXEME, "tag", readLexeme());
            }
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

    private static final String REFUSED_IN_A_FILE = ":;()[]\"^";

    private static final String REFUSED_IN_A_QUOTED_FILE = ":;\"";

    private StringValue readFile() {
        advance();
        boolean quoted = peek() == '"';
        if (quoted) {
            advance();
        }
        String refused = quoted ? REFUSED_IN_A_QUOTED_FILE : REFUSED_IN_A_FILE;
        StringBuilder text = new StringBuilder();
        while (peek() != END_OF_INPUT) {
            int character = peek();
            if (quoted && character == '"') {
                advance();
                return StringValue.of(text.toString(), Datatype.FILE);
            }
            if (!quoted && endsLexeme(character)) {
                break;
            }
            text.appendCodePoint(nextFileCharacter(refused, quoted));
        }
        if (quoted) {
            throw failure(SyntaxFailure.UNTERMINATED_STRING, null);
        }
        return StringValue.of(text.toString(), Datatype.FILE);
    }

    private int nextFileCharacter(String refused, boolean quoted) {
        int character = peek();
        if (character < ' ') {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        if (character == '\\') {
            advance();
            return '/';
        }
        if (character == '%') {
            return escapedByPercent();
        }
        if (character == '^') {
            if (!quoted || peekAt(1) == END_OF_INPUT) {
                throw failure(SyntaxFailure.INVALID_LEXEME, null);
            }
            advance();
            return readEscape();
        }
        if (character < 0x80 && refused.indexOf(character) >= 0) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        advance();
        return character;
    }

    private int escapedByPercent() {
        int high = hexDigitValue(peekAt(1));
        int low = hexDigitValue(peekAt(2));
        if (high < 0 || low < 0) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        advance();
        advance();
        advance();
        return high * 16 + low;
    }

    private static int hexDigitValue(int character) {
        if (character >= '0' && character <= '9') {
            return character - '0';
        }
        if (character >= 'a' && character <= 'f') {
            return character - 'a' + 10;
        }
        if (character >= 'A' && character <= 'F') {
            return character - 'A' + 10;
        }
        return -1;
    }

    private Value readSignedOrLexeme() {
        if ((peek() == '-' || peek() == '+') && peekAt(1) == '$') {
            boolean negative = peek() == '-';
            advance();
            advance();
            return moneyOf(readLexeme(), negative);
        }
        if ((peek() == '-' || peek() == '+') && peekAt(1) == '#') {
            String sign = String.valueOf((char) peek());
            advance();
            return WordValue.of(sign);
        }
        return classify(readLexeme());
    }

    private MoneyValue readMoney() {
        String lexeme = readLexeme();
        String digits = lexeme.substring(1);
        return moneyOf(digits, false, lexeme);
    }

    private MoneyValue moneyOf(String digits, boolean negative) {
        return moneyOf(digits, negative, (negative ? "-$" : "$") + digits);
    }

    private MoneyValue moneyOf(String digits, boolean negative, String token) {
        if (digits.indexOf('/') >= 0) {
            throw failureReading(SyntaxFailure.INVALID_LEXEME, "money",
                    token.substring(0, token.indexOf('/') + 1));
        }
        try {
            BigDecimal amount = new BigDecimal(withTheOnePointWrittenAsADot(digits));
            return MoneyValue.of(negative ? amount.negate() : amount);
        } catch (NumberFormatException notANumber) {
            throw failureReading(SyntaxFailure.INVALID_LEXEME, "money", token);
        }
    }

    private String readLexeme() {
        StringBuilder lexeme = new StringBuilder();
        while (peek() != END_OF_INPUT) {
            if (peek() == '(' && lexeme.indexOf("/") >= 0) {
                takeParenthesisedGroup(lexeme);
                continue;
            }
            if (peek() == '#' && peekAt(1) == '"' && lexeme.indexOf("/") >= 0) {
                takeCharacterLiteral(lexeme);
                continue;
            }
            if (peek() == '%' && peekAt(1) == '"' && lexeme.indexOf("/") >= 0) {
                takeQuotedFile(lexeme);
                continue;
            }
            if (endsLexeme(peek())) {
                break;
            }
            lexeme.appendCodePoint(peek());
            advance();
        }
        if (lexeme.isEmpty()) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        return lexeme.toString();
    }

    private void takeCharacterLiteral(StringBuilder lexeme) {
        lexeme.appendCodePoint(peek());
        advance();
        lexeme.appendCodePoint(peek());
        advance();
        while (peek() != END_OF_INPUT) {
            boolean escaped = peek() == '^';
            lexeme.appendCodePoint(peek());
            advance();
            if (escaped && peek() != END_OF_INPUT) {
                lexeme.appendCodePoint(peek());
                advance();
                continue;
            }
            if (!escaped && peek() == '"') {
                lexeme.appendCodePoint(peek());
                advance();
                return;
            }
        }
        throw failure(SyntaxFailure.MISSING_CLOSE, OpenDelimiter.QUOTE);
    }

    private void takeQuotedFile(StringBuilder lexeme) {
        lexeme.appendCodePoint(peek());
        advance();
        lexeme.appendCodePoint(peek());
        advance();
        while (peek() != END_OF_INPUT) {
            boolean escaped = peek() == '^';
            lexeme.appendCodePoint(peek());
            advance();
            if (escaped && peek() != END_OF_INPUT) {
                lexeme.appendCodePoint(peek());
                advance();
                continue;
            }
            if (!escaped && peek() == '"') {
                lexeme.appendCodePoint(peek());
                advance();
                return;
            }
        }
        throw failure(SyntaxFailure.MISSING_CLOSE, OpenDelimiter.QUOTE);
    }

    private void takeParenthesisedGroup(StringBuilder lexeme) {
        int depth = 0;
        do {
            if (peek() == '(') {
                depth++;
            } else if (peek() == ')') {
                depth--;
            } else if (peek() == END_OF_INPUT) {
                throw failure(SyntaxFailure.MISSING_CLOSE, OpenDelimiter.PARENTHESIS);
            }
            lexeme.appendCodePoint(peek());
            advance();
        } while (depth > 0);
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
                || codepoint == ';';
    }

    private static final Pattern URL =
            Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]*:.+", Pattern.DOTALL);
    private static final Pattern SLASHED_DATE =
            Pattern.compile("\\d{1,4}/[A-Za-z0-9]+/\\d{1,4}");
    private static final Pattern HYPHENATED_DATE =
            Pattern.compile("(\\d{1,4})-([A-Za-z]{3,}|\\d{1,2})-(\\d{1,4})");
    private static final Pattern DATE_WITH_TIME = Pattern.compile(
            "(\\d{1,4}[-/](?:[A-Za-z]{3,}|\\d{1,2})[-/]\\d{1,4})"
                    + "(?:[/Tt](-?\\d{1,2}:\\d{1,2}(?::\\d{1,2}(?:\\.\\d+)?)?))?"
                    + "([-+]\\d{1,2}:\\d{1,2}|[-+]\\d{1,4}|[Zz])?");

    private static final Pattern DATE_WITH_A_NEGATIVE_YEAR = Pattern.compile(
            "\\d{1,4}[-/](?:[A-Za-z]{3,}|\\d{1,2})[-/]-\\d{1,4}.*");
    private static final Pattern PAIR = Pattern.compile(
            "([-+]?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)"
                    + "[xX]([-+]?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)");
    private static final Pattern TIME = Pattern.compile(
            "([-+]?\\d+):(\\d{1,2}(?:[.,]\\d+)?)(?::(\\d{1,2}(?:[.,]\\d+)?))?");
    private static final Pattern TUPLE = Pattern.compile("\\d+(?:\\.\\d+){2,}");
    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d+(?:'\\d+)*");
    private static final Pattern DECIMAL =
            Pattern.compile("[-+]?(?:\\d+[.,]\\d*|[.,]\\d+|\\d+)(?:[eE][-+]?\\d+)?");
    private static final Pattern PERCENT = Pattern.compile(
            "([-+]?(?:\\d+[.,]\\d*|[.,]\\d+|\\d+)(?:[eE][-+]?\\d+)?)%");
    private static final Pattern DATATYPE = Pattern.compile("([a-zA-Z][a-zA-Z0-9-]*)!");
    private static final String[] MONTH_NAMES = {
        "jan", "feb", "mar", "apr", "may", "jun",
        "jul", "aug", "sep", "oct", "nov", "dec"
    };

    private static final String NOT_IN_A_WORD = "<>%#$\\,";

    private static int firstAngleBracket(String lexeme) {
        int depth = 0;
        for (int at = 0; at < lexeme.length(); at++) {
            char letter = lexeme.charAt(at);
            if (letter == '(') {
                depth++;
            } else if (letter == ')') {
                depth--;
            } else if (depth == 0 && (letter == '<' || letter == '>')) {
                return at;
            }
        }
        return -1;
    }

    private static boolean allSymbols(String lexeme) {
        return !lexeme.isEmpty() && lexeme.chars().noneMatch(Character::isLetterOrDigit);
    }

    private static final Pattern SIGNED_DIGITS_MAYBE_HASHED =
            Pattern.compile("[+-][0-9]+#?");

    private static final Pattern DIGITS_THEN_HASH = Pattern.compile("[0-9]+#");

    private Value classify(String lexeme) {
        if (lexeme.startsWith("/_")
                && (lexeme.length() == 2 || lexeme.charAt(2) == '/')) {
            position -= lexeme.length() - 1;
            column -= lexeme.length() - 1;
            return WordValue.of("/");
        }
        if (lexeme.startsWith("_/")) {
            position -= lexeme.length() - 1;
            column -= lexeme.length() - 1;
            return NoneValue.none();
        }
        if (SIGNED_DIGITS_MAYBE_HASHED.matcher(lexeme).matches()
                && (peek() == '#' || peek() == '{')) {
            throw failureReading(SyntaxFailure.INVALID_LEXEME, "integer", lexeme);
        }
        if (DIGITS_THEN_HASH.matcher(lexeme).matches()) {
            if (peek() == '{') {
                return readBasedBinary(lexeme.substring(0, lexeme.length() - 1));
            }
            if (peek() == '(' || peek() == '"') {
                throw failureReading(SyntaxFailure.INVALID_LEXEME, "integer", lexeme);
            }
        }
        if (Character.isDigit(lexeme.charAt(0))) {
            int angle = firstAngleBracket(lexeme);
            if (angle > 0) {
                position -= lexeme.length() - angle;
                column -= lexeme.length() - angle;
                return classifyPlain(lexeme.substring(0, angle));
            }
        }
        Value read = classifyPlain(lexeme);
        if (read.datatype() == Datatype.URL) {
            return read;
        }
        int offending = firstOffendingCharacter(lexeme);
        int bracket = firstAngleBracket(lexeme);
        if (bracket > 0 && (offending < 0 || bracket < offending
                || !(classifyPlain(lexeme.substring(0, offending)) instanceof WordValue))) {
            offending = Math.min(bracket, offending < 0 ? bracket : offending);
            if (!(classifyPlain(lexeme.substring(0, bracket)) instanceof WordValue)) {
                offending = bracket;
            }
        }
        if (offending >= 0 && lexeme.charAt(offending) == ','
                && read instanceof WordValue) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        if (offending == 0 && !allSymbols(lexeme) && read instanceof WordValue) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        boolean angleToSettle = offending > 0 && !allSymbols(lexeme)
                && (lexeme.charAt(offending) == '<' || lexeme.charAt(offending) == '>');
        if (!angleToSettle
                && (offending <= 0 || allSymbols(lexeme) || !(read instanceof WordValue))) {
            return read;
        }
        String before = lexeme.substring(0, offending);
        String after = lexeme.substring(offending);
        boolean startsAnAngleBracket = after.charAt(0) == '<' || after.charAt(0) == '>';
        if ((allSymbols(after) || startsAnAngleBracket)
                && splitsHereRatherThanFailing(before, after)) {
            position -= after.length();
            column -= after.length();
            return classifyPlain(before);
        }
        if (startsAnAngleBracket) {
            throw failureReading(SyntaxFailure.INVALID_LEXEME, "word");
        }
        throw failure(SyntaxFailure.INVALID_LEXEME, null);
    }

    private boolean splitsHereRatherThanFailing(String before, String after) {
        String lastSegment = before.substring(before.lastIndexOf('/') + 1);
        if (!lastSegment.isEmpty() && !(classifyPlain(lastSegment) instanceof WordValue)) {
            return true;
        }
        if (after.length() < 2) {
            return false;
        }
        char following = after.charAt(1);
        if (following == '/') {
            return true;
        }
        return following != '<' && following != '>' && following != '='
                && !isDelimiterOrSpace(following);
    }

    private static int firstOffendingCharacter(String lexeme) {
        int depth = 0;
        for (int at = 0; at < lexeme.length(); at++) {
            char letter = lexeme.charAt(at);
            if (letter == '(') {
                depth++;
            } else if (letter == ')') {
                depth--;
            } else if (depth == 0 && NOT_IN_A_WORD.indexOf(letter) >= 0) {
                return at;
            }
        }
        return -1;
    }

    private Value classifyPlain(String lexeme) {
        refuseAMisplacedSigil(lexeme);
        if (lexeme.equals("_")) {
            return NoneValue.none();
        }
        if (lexeme.chars().allMatch(character -> character == '/')) {
            return WordValue.of(lexeme);
        }
        if (lexeme.length() > 1 && lexeme.endsWith(":")
                && lexeme.chars().limit(lexeme.length() - 1L)
                        .allMatch(character -> character == '/')) {
            return WordValue.of(
                    lexeme.substring(0, lexeme.length() - 1), Datatype.SET_WORD);
        }
        if (lexeme.length() > 1 && lexeme.charAt(0) == ':'
                && lexeme.chars().skip(1).allMatch(character -> character == '/')) {
            return WordValue.of(lexeme.substring(1), Datatype.GET_WORD);
        }
        if (lexeme.length() > 1 && lexeme.charAt(0) == '\''
                && lexeme.chars().skip(1).allMatch(character -> character == '/')) {
            return WordValue.of(lexeme.substring(1), Datatype.LIT_WORD);
        }
        if (lexeme.startsWith("/") && lexeme.indexOf('/', 1) < 0) {
            return WordValue.of(lexeme.substring(1), Datatype.REFINEMENT);
        }
        if (couldOpenAScheme(lexeme) && URL.matcher(lexeme).matches()) {
            return StringValue.of(lexeme, Datatype.URL);
        }
        if (opensWithAPlainDigit(lexeme)) {
            if (SLASHED_DATE.matcher(lexeme).matches()) {
                return readDate(lexeme, "/");
            }
            if (DATE_WITH_A_NEGATIVE_YEAR.matcher(lexeme).matches()) {
                throw failureReading(SyntaxFailure.INVALID_LEXEME, "date", lexeme);
            }
            var dated = DATE_WITH_TIME.matcher(lexeme);
            if (dated.matches() && (dated.group(2) != null || dated.group(3) != null)) {
                return readDateWithTime(dated.group(1), dated.group(2), dated.group(3));
            }
        }
        if (lexeme.indexOf('/') >= 0) {
            return readPath(lexeme);
        }
        if (lexeme.endsWith(":") && lexeme.length() > 1) {
            String spelling = lexeme.substring(0, lexeme.length() - 1);
            refuseTheNoneWordAsAName(spelling, "word-set", lexeme);
            return WordValue.of(spelling, Datatype.SET_WORD);
        }
        if (lexeme.startsWith(":") && lexeme.length() > 1) {
            if (Character.isDigit(lexeme.charAt(1))) {
                var time = TIME.matcher("0" + lexeme);
                if (time.matches()) {
                    return readTime(time.group(1), time.group(2), time.group(3));
                }
                throw failureReading(SyntaxFailure.INVALID_LEXEME, "time", lexeme);
            }
            refuseTheNoneWordAsAName(lexeme.substring(1), "word-get", lexeme);
            return WordValue.of(lexeme.substring(1), Datatype.GET_WORD);
        }
        if (lexeme.startsWith("'") && lexeme.length() > 1) {
            refuseTheNoneWordAsAName(lexeme.substring(1), "word-lit", lexeme);
            return WordValue.of(lexeme.substring(1), Datatype.LIT_WORD);
        }
        if (lexeme.indexOf('@') > 0) {
            return StringValue.of(emailBodyOf(lexeme), Datatype.EMAIL);
        }
        return classifyScalarOrWord(lexeme);
    }

    private String emailBodyOf(String lexeme) {
        byte[] source = lexeme.getBytes(UTF_8);
        byte[] octets = new byte[source.length];
        int written = 0;
        boolean seenAnAtSign = false;
        for (int at = 0; at < source.length; at++) {
            if (source[at] == '@') {
                if (seenAnAtSign) {
                    throw failure(SyntaxFailure.INVALID_LEXEME, null);
                }
                seenAnAtSign = true;
            }
            if (source[at] == '%') {
                if (at + 2 >= source.length) {
                    throw failure(SyntaxFailure.INVALID_LEXEME, null);
                }
                int high = hexDigitValue((char) source[at + 1]);
                int low = hexDigitValue((char) source[at + 2]);
                if (high < 0 || low < 0) {
                    throw failure(SyntaxFailure.INVALID_LEXEME, null);
                }
                octets[written++] = (byte) (high * 16 + low);
                at += 2;
                continue;
            }
            octets[written++] = source[at];
        }
        if (!seenAnAtSign) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        return new String(octets, 0, written, UTF_8);
    }

    private void refuseAMisplacedSigil(String lexeme) {
        if (lexeme.length() < 2) {
            return;
        }
        if (isDoublySignedTime(lexeme)) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        char sigil = lexeme.charAt(0);
        char following = lexeme.charAt(1);
        if (sigil == '\'' || sigil == ':') {
            if (sigil == '\'' && Character.isDigit(following)) {
                throw failure(SyntaxFailure.INVALID_LEXEME, null);
            }
            if (following == '\'' || following == ':') {
                throw failure(SyntaxFailure.INVALID_LEXEME, null);
            }
            if (lexeme.indexOf('@') > 0) {
                throw failure(SyntaxFailure.INVALID_LEXEME, null);
            }
            if ((following == '-' || following == '+') && lexeme.length() > 2
                    && Character.isDigit(lexeme.charAt(2))) {
                throw failure(SyntaxFailure.INVALID_LEXEME, null);
            }
        }
        if (sigil == '/' && lexeme.endsWith(":")
                && !lexeme.chars().limit(lexeme.length() - 1L)
                        .allMatch(character -> character == '/')) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
    }

    private static final Pattern BASED_INTEGER =
            Pattern.compile("(\\d{1,2})#([0-9A-Za-z]+)");

    private static boolean couldSpellANumber(String lexeme) {
        for (int at = 0; at < lexeme.length(); at++) {
            if (Character.isDigit(lexeme.charAt(at))) {
                return true;
            }
        }
        return false;
    }

    private static boolean couldOpenAScheme(String lexeme) {
        int colon = lexeme.indexOf(':');
        return colon > 0 && colon < lexeme.length() - 1;
    }

    private static boolean opensWithAPlainDigit(String lexeme) {
        return !lexeme.isEmpty() && lexeme.charAt(0) >= '0' && lexeme.charAt(0) <= '9';
    }

    private static boolean holdsAPlainDigit(String lexeme) {
        for (int at = 0; at < lexeme.length(); at++) {
            if (lexeme.charAt(at) >= '0' && lexeme.charAt(at) <= '9') {
                return true;
            }
        }
        return false;
    }

    private Value classifyScalarOrWord(String lexeme) {
        if (!couldSpellANumber(lexeme)) {
            return WordValue.of(lexeme);
        }
        var based = BASED_INTEGER.matcher(lexeme);
        if (based.matches()) {
            return basedInteger(Integer.parseInt(based.group(1)), based.group(2));
        }
        if (HYPHENATED_DATE.matcher(lexeme).matches()) {
            return readDate(lexeme, "-");
        }
        var pair = PAIR.matcher(lexeme);
        if (pair.matches()) {
            return PairValue.of(
                    Double.parseDouble(pair.group(1)), Double.parseDouble(pair.group(2)));
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
            return DecimalValue.percent(Double.parseDouble(
                    withTheOnePointWrittenAsADot(percent.group(1))) / 100.0);
        }
        if (INTEGER.matcher(lexeme).matches()) {
            try {
                return IntegerValue.of(Long.parseLong(lexeme.replace("'", "")));
            } catch (NumberFormatException tooBig) {
                throw failure(SyntaxFailure.INTEGER_OUT_OF_RANGE, null);
            }
        }
        Value special = specialDecimal(lexeme);
        if (special != null) {
            return special;
        }
        if (DECIMAL.matcher(lexeme).matches() && holdsAPlainDigit(lexeme)) {
            return DecimalValue.of(Double.parseDouble(withTheOnePointWrittenAsADot(lexeme)));
        }
        if (Character.isDigit(lexeme.charAt(0))) {
            throw failureReading(SyntaxFailure.INVALID_LEXEME, "integer", lexeme);
        }
        return WordValue.of(lexeme);
    }

    private static String withTheOnePointWrittenAsADot(String lexeme) {
        return lexeme.indexOf(',') < 0 ? lexeme : lexeme.replace(',', '.');
    }

    private static Value specialDecimal(String lexeme) {
        return switch (lexeme.toUpperCase(java.util.Locale.ROOT)) {
            case "1.#INF", "+1.#INF" -> DecimalValue.of(Double.POSITIVE_INFINITY);
            case "-1.#INF" -> DecimalValue.of(Double.NEGATIVE_INFINITY);
            case "1.#NAN", "-1.#NAN", "+1.#NAN" -> DecimalValue.of(Double.NaN);
            default -> null;
        };
    }

    private Value basedInteger(int written, String digits) {
        int base = written == 0 ? 16 : written;
        if (base < 2 || base > 16) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        if (base == 10) {
            try {
                return IntegerValue.of(Long.parseLong(digits));
            } catch (NumberFormatException doesNotFit) {
                throw failure(SyntaxFailure.INVALID_LEXEME, null);
            }
        }
        int bitsPerDigit = Integer.numberOfTrailingZeros(base);
        boolean isPowerOfTwo = Integer.bitCount(base) == 1;
        int mostDigits = isPowerOfTwo ? (Long.SIZE + bitsPerDigit - 1) / bitsPerDigit : 19;
        if (digits.length() > mostDigits) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        long value = 0;
        for (int at = 0; at < digits.length(); at++) {
            int digit = Character.digit(digits.charAt(at), base);
            if (digit < 0) {
                throw failure(SyntaxFailure.INVALID_LEXEME, null);
            }
            value = value * base + digit;
        }
        return IntegerValue.of(value);
    }

    private Value readDatatype(String name) {
        for (Datatype candidate : Datatype.values()) {
            if (candidate.spelling().equalsIgnoreCase(name)) {
                return DatatypeValue.of(candidate);
            }
        }
        return Typeset.named(name)
                .map(typeset -> (Value) TypesetValue.of(typeset))
                .orElseThrow(() -> failure(SyntaxFailure.MALCONSTRUCT, null));
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
        for (String segment : splitOutsideParens(body)) {
            if (segment.isEmpty()) {
                throw failureReading(SyntaxFailure.INVALID_LEXEME, "path");
            }
            segments.add(readPathSegment(segment));
        }
        return BlockValue.path(segments, pathType);
    }

    private static List<String> splitOutsideParens(String body) {
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        int depth = 0;
        for (int at = 0; at < body.length(); at++) {
            char character = body.charAt(at);
            if (character == '%' && part.isEmpty() && depth == 0
                    && startsAFileRatherThanTheWord(body, at)) {
                int ends = whereAFileSegmentEnds(body, at);
                part.append(body, at, ends);
                at = ends - 1;
                continue;
            }
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
            }
            if (character == '/' && depth == 0) {
                parts.add(part.toString());
                part.setLength(0);
                continue;
            }
            part.append(character);
        }
        parts.add(part.toString());
        return parts;
    }

    private static boolean startsAFileRatherThanTheWord(String body, int at) {
        int past = at;
        while (past < body.length() && body.charAt(past) == '%') {
            past++;
        }
        return past < body.length() && body.charAt(past) != '/';
    }

    private static int whereAFileSegmentEnds(String body, int startsAt) {
        if (startsAt + 1 >= body.length() || body.charAt(startsAt + 1) != '"') {
            return body.length();
        }
        int closing = body.indexOf('"', startsAt + 2);
        return closing < 0 ? body.length() : closing + 1;
    }

    private Value readPathSegment(String segment) {
        if (segment.startsWith("(") && segment.endsWith(")")) {
            TranscodeResult inside = transcode(segment.substring(1, segment.length() - 1));
            if (!inside.succeeded()) {
                throw failure(SyntaxFailure.INVALID_LEXEME, null);
            }
            return inside.values().orElseThrow().as(Datatype.PAREN);
        }
        if (segment.startsWith(":") && segment.length() > 1) {
            return WordValue.of(segment.substring(1), Datatype.GET_WORD);
        }
        if (segment.startsWith("'") && segment.length() > 1) {
            return WordValue.of(segment.substring(1), Datatype.LIT_WORD);
        }
        if (INTEGER.matcher(segment).matches()) {
            return IntegerValue.of(Long.parseLong(segment));
        }
        if (DECIMAL.matcher(segment).matches() && holdsAPlainDigit(segment)) {
            return DecimalValue.of(Double.parseDouble(segment));
        }
        TranscodeResult read = transcode(segment);
        if (read.succeeded()) {
            List<Value> values = read.values().orElseThrow().remaining();
            if (values.size() == 1 && !(values.getFirst() instanceof WordValue)) {
                return values.getFirst();
            }
            return WordValue.of(segment);
        }
        refuseASegmentThatCannotBeAWord(segment);
        return WordValue.of(segment);
    }

    private void refuseASegmentThatCannotBeAWord(String segment) {
        if (!segment.isEmpty() && Character.isDigit(segment.charAt(0))) {
            throw failureReading(SyntaxFailure.INVALID_LEXEME, "path", segment);
        }
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
        if (segments.length < TupleValue.MINIMUM_SHOWN_SEGMENTS
                || segments.length > TupleValue.MAXIMUM_SEGMENTS) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        return TupleValue.of(segments);
    }

    private Value readTime(String written, String secondPart, String thirdPart) {
        String first = withTheOnePointWrittenAsADot(written);
        String second = withTheOnePointWrittenAsADot(secondPart);
        String third = thirdPart == null ? null : withTheOnePointWrittenAsADot(thirdPart);
        if (!isMinutesAndSeconds(second, third)) {
            return timeOf(first, second, third);
        }
        boolean negative = first.startsWith("-");
        String minutes = first.startsWith("-") || first.startsWith("+")
                ? first.substring(1)
                : first;
        return timeOf(negative ? "-0" : "0", minutes, second);
    }

    private static boolean isMinutesAndSeconds(String second, String third) {
        if (third != null || !second.contains(".")) {
            return false;
        }
        return Double.parseDouble("0" + second.substring(second.indexOf('.'))) > 0;
    }

    private TimeValue timeOf(String hours, String minutes, String seconds) {
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
                magnitude, (long) Double.parseDouble(minutes), wholeSeconds, nanoseconds);
        return negative ? TimeValue.ofNanoseconds(-positive.nanoseconds()) : positive;
    }

    private Value readDate(String lexeme, String separator) {
        String[] parts = lexeme.split(Pattern.quote(separator));
        boolean yearIsFirst = parts[0].length() >= 4;
        int month = monthNumber(parts[1]);
        int year = yearIsFirst
                ? Integer.parseInt(parts[0])
                : yearFrom(parts[2]);
        int day = Integer.parseInt(yearIsFirst ? parts[2] : parts[0]);
        try {
            return DateValue.of(year, month, day);
        } catch (IllegalArgumentException outOfRange) {
            throw failureReading(SyntaxFailure.INVALID_LEXEME, "date", lexeme);
        }
    }

    private static int yearFrom(String written) {
        int num = Integer.parseInt(written);
        if (written.length() >= 3) {
            return num;
        }
        int thisYear = java.time.Year.now().getValue();
        int year = thisYear / 100 * 100 + num;
        if (year - thisYear > 50) {
            return year - 100;
        }
        return year - thisYear < -50 ? year + 100 : year;
    }

    private Value readDateWithTime(String day, String time, String offset) {
        DateValue date = (DateValue) readDate(day, day.indexOf('-') >= 0 ? "-" : "/");
        TimeValue timeOfDay = time == null
                ? TimeValue.ofNanoseconds(0)
                : aClockOfTheDay(timeFromText(time));
        return new DateValue(date.year(), date.month(), date.day(),
                Optional.of(timeOfDay),
                Optional.of(time == null ? 0 : offsetMinutesFrom(offset)));
    }

    private TimeValue aClockOfTheDay(TimeValue written) {
        if (written.nanoseconds() < 0 || written.nanoseconds() >= NANOSECONDS_IN_A_DAY) {
            throw failureReading(SyntaxFailure.INVALID_LEXEME, "date");
        }
        return written;
    }

    private static final long NANOSECONDS_IN_A_DAY = 24L * 60L * 60L * 1_000_000_000L;

    private TimeValue timeFromText(String written) {
        var parts = TIME.matcher(written);
        if (!parts.matches()) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        return (TimeValue) readTime(parts.group(1), parts.group(2), parts.group(3));
    }

    private int offsetMinutesFrom(String written) {
        if (written == null || written.length() == 1) {
            return 0;
        }
        int colon = written.indexOf(':');
        int size = colon < 0
                ? quarterHoursRoundedDownIn(
                        Integer.parseInt(written.substring(1)))
                : exactQuarterIn(Integer.parseInt(written.substring(1, colon)),
                        Integer.parseInt(written.substring(colon + 1)));
        return written.charAt(0) == '-' ? -size : size;
    }

    private int quarterHoursRoundedDownIn(int written) {
        if (written > MOST_A_COLONLESS_ZONE_MAY_SAY) {
            throw failureReading(SyntaxFailure.INVALID_LEXEME, "date");
        }
        return roundedDownToAQuarter(written / 100 * 60 + written % 100);
    }

    private static final int MOST_A_COLONLESS_ZONE_MAY_SAY = 1500;

    private static int roundedDownToAQuarter(int minutes) {
        return minutes / MINUTES_IN_A_QUARTER * MINUTES_IN_A_QUARTER;
    }

    private static final int MINUTES_IN_A_QUARTER = 15;

    private int exactQuarterIn(int hours, int minutes) {
        int size = hours * 60 + minutes;
        if (minutes % MINUTES_IN_A_QUARTER != 0 || size > MOST_A_ZONE_MAY_BE) {
            throw failureReading(SyntaxFailure.INVALID_LEXEME, "date");
        }
        return size;
    }

    private static final int MOST_A_ZONE_MAY_BE = 15 * 60 + 45;

    private void refuseTheNoneWordAsAName(String spelling, String tokenKind, String lexeme) {
        if (spelling.equals("_")) {
            throw failureReading(SyntaxFailure.INVALID_LEXEME, tokenKind);
        }
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
        if (endsALine(position)) {
            line++;
            column = 1;
        } else {
            column++;
        }
        position++;
    }

    private boolean endsALine(int at) {
        int here = codepoints[at];
        if (here == '\n') {
            return true;
        }
        boolean followedByLineFeed =
                at + 1 < codepoints.length && codepoints[at + 1] == '\n';
        return here == '\r' && !followedByLineFeed;
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
                Optional.ofNullable(unclosed),
                Optional.empty(),
                Optional.of(theLineBeingRead()));
    }

    private String theLineBeingRead() {
        int from = Math.min(position, codepoints.length);
        while (from > 0 && codepoints[from - 1] != '\n') {
            from--;
        }
        int to = from;
        while (to < codepoints.length && codepoints[to] != '\n') {
            to++;
        }
        return new String(codepoints, from, to - from).trim();
    }

    private static boolean isDoublySignedTime(String lexeme) {
        if (!isSign(lexeme.charAt(0)) || !isSign(lexeme.charAt(1))) {
            return false;
        }
        int colon = lexeme.indexOf(':');
        return colon > 0 && colon < lexeme.length() - 1;
    }

    private static boolean isSign(char character) {
        return character == '-' || character == '+';
    }

    private MalformedSource failureReading(SyntaxFailure failure, String tokenKind) {
        return failureReading(failure, tokenKind, null);
    }

    private MalformedSource failureReading(
            SyntaxFailure failure, String tokenKind, String offendingText) {

        return new MalformedSource(
                failure,
                new SourcePosition(line, column, position),
                Optional.empty(),
                Optional.of(tokenKind),
                Optional.of(theLineBeingRead()),
                Optional.ofNullable(offendingText));
    }

    private static final class MalformedSource extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final transient SyntaxFailure failure;
        private final transient SourcePosition position;
        private final transient Optional<OpenDelimiter> unclosed;
        private final transient Optional<String> tokenKind;
        private final transient Optional<String> fragment;
        private final transient Optional<String> offendingText;

        MalformedSource(
                SyntaxFailure failure,
                SourcePosition position,
                Optional<OpenDelimiter> unclosed,
                Optional<String> tokenKind,
                Optional<String> fragment) {
            this(failure, position, unclosed, tokenKind, fragment, Optional.empty());
        }

        MalformedSource(
                SyntaxFailure failure,
                SourcePosition position,
                Optional<OpenDelimiter> unclosed,
                Optional<String> tokenKind,
                Optional<String> fragment,
                Optional<String> offendingText) {
            super(failure.description(), null, false, false);
            this.failure = failure;
            this.position = position;
            this.unclosed = unclosed;
            this.tokenKind = tokenKind;
            this.fragment = fragment;
            this.offendingText = offendingText;
        }
    }
}

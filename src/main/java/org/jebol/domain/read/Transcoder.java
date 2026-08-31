package org.jebol.domain.read;

import org.jebol.domain.value.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

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

    /**
     * Where each top-level expression begins and ends in the source.
     *
     * <p>Only the outermost level is recorded. A block's contents are part
     * of the block's own span, so nesting adds nothing here.
     */
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
            return new Reading(
                    reader.readSequence(NO_TERMINATOR),
                    Optional.empty(),
                    reader.position,
                    reader.line);
        } catch (MalformedSource malformed) {
            return new Reading(
                    List.copyOf(reader.valuesTakenAtTheTopLevel),
                    Optional.of(new TranscodeResult.Failure(
                            malformed.failure, malformed.position, malformed.unclosed,
                            malformed.tokenKind, malformed.fragment,
                            malformed.offendingText)),
                    reader.position,
                    reader.line);
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
            int lineEndedOn) {}

    /** Reads every value in the source, or reports the first failure. */
    public static TranscodeResult transcode(String source) {
        return transcode(source, 1);
    }

    /** Lines counted from somewhere other than one, for a caller reading a fragment. */
    public static TranscodeResult transcode(String source, long firstLine) {
        Reading reading = read(source, firstLine, Extent.THE_WHOLE_SOURCE);
        return reading.whyItStopped()
                .<TranscodeResult>map(failure -> failure)
                .orElseGet(() -> new TranscodeResult.Success(
                        BlockValue.block(reading.valuesReadBeforeStopping())));
    }

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
                    throw failure(SyntaxFailure.NESTING_TOO_DEEP, delimiterFor(next));
                }
                advance();
                if (outermost) {
                    topLevelStarts.add(began);
                }
                enclosing.push(new OpenLevel(values, closing, collecting));
                values = new ArrayList<>();
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
            Value finished = collecting == Datatype.PAREN
                    ? BlockValue.paren(values)
                    : BlockValue.block(values);
            OpenLevel parent = enclosing.pop();
            values = parent.values();
            collecting = parent.collecting();
            values.add(finished);
        }
        return values;
    }

    /**
     * Whether to stop as soon as one whole top-level value has been read.
     *
     * <p>What {@code Scan_Token} gives the C for free and this had no way to be
     * asked: read one value and stop, without looking at a character past it. Set
     * for the whole life of a reader rather than passed down, because the walk
     * hands its own state around a stack and one more parameter would have to be
     * threaded through every level to be ignored by all of them.
     */
    private boolean stopAfterOneValue;

    private boolean stopAtEveryDepth;

    private final List<Value> valuesTakenAtTheTopLevel = new ArrayList<>();

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
            case '%' -> readFileOrPercentWord();
            case '@' -> readRef();
            case '$' -> readMoney();
            default -> readSignedOrLexeme();
        };
    }

    /**
     * Whether what follows cannot be part of a filename.
     *
     * <p>Not the same question as "does the word end here". A percent
     * word may be followed by a colon, making it a set-word, or by a
     * slash, making it a path segment -- `o/%%` and `%%: 1` are both
     * legal. Reading those as the end of the input cost lexer-test.r3
     * three hundred and forty of its assertions.
     */
    private boolean beginsNoFilename(int following) {
        return following == END_OF_INPUT
                || Character.isWhitespace(following)
                || isClosingDelimiter(following)
                || following == ':';
    }

    /**
     * A file, or one of the two words spelled out of percent signs.
     *
     * <p>{@code %} is the file sigil, so a filename has to follow it.
     * With nothing after it there is no filename and it is the word the
     * modulo operator is bound to. Reading that as an empty file made
     * {@code 7 % 0} answer 0 rather than dividing by zero, because the
     * operator never got a chance to be one.
     *
     * <p>A second percent is the same story one character along:
     * {@code %%} is the word Euclidean modulo is bound to, and reading it
     * as a file named "%" made {@code -7 %% 3} answer 3 -- the operator
     * dropped out and the last value in the expression stood.
     *
     * <p>Past that the two part company. A name after a lone percent is a
     * file, and a name after two is neither: R3 refuses {@code %%a} as a
     * malformed file rather than reading a word.
     *
     * <p>Unless the two characters after are hex, because then the second
     * percent opens an escape rather than a second sign: {@code %%40b} is the
     * file {@code @b}. Refusing it cost url-test.r3 twenty-eight assertions.
     * A slash does not get the same allowance -- {@code %%/x} is invalid on a
     * real Rebol, and was being read here as the operator.
     */
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

    /**
     * A raw string, written {@code %{...}%} or with a longer run of percents.
     *
     * <p>{@code Scan_Raw_String}, whose summary is the whole point: "Scan a raw
     * string (without any modifications). Eliminates need of double escaping and
     * allowes unmatched braces." So a caret is a caret, a lone brace is a brace,
     * and a line ending is whatever the source had -- where a braced string
     * would have read every one of those as an instruction.
     *
     * <p>The run of percent signs is what closes it, which is what lets a raw
     * string hold the closing sequence of a shorter one: {@code %%{ %{^}% }%%}
     * is one string holding another. A closing brace followed by a longer run
     * than the one that opened is a mistake rather than content --
     * {@code if (n > num) return 0;} -- so the reader refuses it rather than
     * reading to the end of the file looking for its own terminator.
     */
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

    /** The word, or the set-word when a colon follows it. */
    private Value percentWord(String spelling) {
        if (peek() == ':') {
            advance();
            return WordValue.of(spelling, Datatype.SET_WORD);
        }
        return WordValue.of(spelling);
    }

    /**
     * A ref!, written {@code @bob}.
     *
     * <p>A datatype Rebol 3.x added, string-like as file! and email! are.
     * JEBOL read {@code @bob} as a word, which is the quiet kind of reader
     * bug: it parses into the wrong thing rather than failing, so nothing
     * notices until something compares a ref against a word. An {@code @}
     * on its own is an empty ref rather than an error.
     */
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
            if (Character.isWhitespace(next) || next == ',') {
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

    /**
     * An escape whose code point no string may hold is a syntax failure,
     * not a host exception: {@code if (IS_INVALID_CHAR(chr)) return 0;} in
     * {@code Scan_Quote}, the same range {@code readCharacter} refuses.
     */
    private int readEscapeRefusingInvalidCodePoints() {
        int codepoint = readEscape();
        if (codepoint > LAST_UNICODE_CODEPOINT || isSurrogate(codepoint)) {
            throw failureReading(SyntaxFailure.INVALID_LEXEME, "string");
        }
        return codepoint;
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

    /**
     * Construction syntax: {@code #(none)}, {@code #(true)}, {@code #(unset)},
     * {@code #(integer!)}, {@code #(decimal! 1)}.
     *
     * <p>The forms MOLD produces for values with no literal spelling of their
     * own, which is what makes those values round-trip. Three shapes: a word
     * naming a value, a datatype on its own producing the datatype value, and
     * a datatype followed by contents to build from.
     *
     * <p>R3-Alpha wrote all of this with square brackets, as {@code #[none]}.
     * Rebol 3.x replaced the form rather than adding to it and now refuses
     * the bracket spelling, so this reads only parentheses. Reading both
     * would accept source a real Rebol rejects, and the bracket form was the
     * reason seventeen of the twenty-two vendored test files would not parse.
     *
     * <p>Only self-contained values can be read back. Something that refers
     * to a live thing, such as a native or a host object, cannot be
     * reconstructed by a reader with no context to resolve it against, and
     * having no context is deliberate.
     */
    private Value readConstruct() {
        advance();
        advance();
        List<Value> contents;
        try {
            contents = readSequence(')');
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

        if (!(first instanceof WordValue named)) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        if (contents.size() == 1) {
            Value simple = switch (named.canonical()) {
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
        if (namesAVector(named, contents.size())) {
            return org.jebol.domain.value.VectorSpec.readConstruction(contents)
                    .orElseThrow(() -> failure(SyntaxFailure.MALCONSTRUCT, null));
        }
        Value resolved = datatypeNamed(named);
        if (contents.size() == 1) {
            return resolved;
        }
        if (!(resolved instanceof DatatypeValue built)) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        return builtFrom(built.represents(), contents.subList(1, contents.size()));
    }

    /**
     * A map literal, {@code #[key: value ...]}.
     *
     * <p>R3-Alpha spelled construction syntax this way. The brackets were
     * reused rather than freed up when constructs moved to parentheses.
     */
    private Value readMap() {
        advance();
        advance();
        List<Value> pairs = readSequence(']');
        if (pairs.size() % 2 != 0) {
            throw failure(SyntaxFailure.INVALID_ARG, null);
        }
        return MapValue.of(pairs);
    }

    /**
     * Whether a construct's first word starts a vector.
     *
     * <p>{@code #(int32! ...)} names a kind of element rather than a datatype,
     * so nothing that looks the word up in the datatype table can read one, and
     * a kind name alone is an empty vector of that kind.
     *
     * <p>{@code vector!} is different in both halves. It is a real datatype
     * name, so {@code #(vector!)} alone stays the datatype value the way
     * {@code #(integer!)} does, and it only starts a vector when a kind name
     * follows it.
     */
    private static boolean namesAVector(WordValue named, int howManyParts) {
        if ("vector!".equals(named.canonical())) {
            return howManyParts > 1;
        }
        return org.jebol.domain.value.VectorKind.named(named.spelling()).isPresent();
    }

    private Value datatypeNamed(WordValue word) {
        if (!word.spelling().endsWith("!")) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        return readDatatype(word.spelling().substring(0, word.spelling().length() - 1));
    }

    /**
     * The datatypes that cannot be written as a construction at all.
     *
     * <p>Straight off the Make column of {@code types.reb}, whose own header
     * says what it is for: "Make -- It can be made with #(datatype) method".
     * Fifteen rows carry a dash, and handing those to MAKE instead would read
     * things Rebol refuses -- {@code #(char! 97)}, {@code #(money! 1)} and
     * {@code #(integer! 5)} all became values here while a real Rebol answered
     * malconstruct.
     */
    private static final java.util.Set<Datatype> HAVE_NO_MAKER = java.util.Set.of(
            Datatype.INTEGER, Datatype.MONEY, Datatype.CHAR, Datatype.WORD,
            Datatype.SET_WORD, Datatype.GET_WORD, Datatype.LIT_WORD,
            Datatype.REFINEMENT, Datatype.ISSUE, Datatype.FRAME, Datatype.PORT,
            Datatype.HANDLE, Datatype.LIBRARY, Datatype.UTYPE);

    /**
     * A construct that carries contents, such as {@code #(decimal! 1)}.
     *
     * <p>What the reader can build itself is below; everything else goes to
     * MAKE, which is what {@code Construct_Value} does with
     * {@code Make_Dispatch}. The list of what it must not try is the one
     * thing kept here, and it is copied from the table rather than judged.
     */
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
        if (contents.size() == 2 && contents.get(1) instanceof IntegerValue at
                && datatype.isSeries()) {
            Value whole = builtFrom(datatype, List.of(contents.getFirst()));
            if (!(whole instanceof SeriesValue series)) {
                throw failure(SyntaxFailure.MALCONSTRUCT, null);
            }
            long wanted = Math.max(1, Math.min(at.magnitude(),
                    series.storageLength() + 1L));
            return series.atIndex((int) wanted);
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
            case STRING, FILE, URL, EMAIL, TAG, REF -> only instanceof StringValue text
                    ? text.as(datatype)
                    : requireDatatype(only, datatype);
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

    /**
     * Everything the reader does not build itself, handed to MAKE.
     *
     * <p>Rebol keeps no list of which datatypes have construction syntax.
     * {@code Construct_Value} skips the datatype word and calls
     * {@code Make_Dispatch[type]} on what is left, so a type has the syntax
     * exactly when it has a maker. The switch above was a list, and its
     * {@code default} refused fourteen types a real Rebol reads -- which
     * stopped ten of Rebol's own test files dead, make-test.r3 at 216 of its
     * 1,029 assertions.
     *
     * <p>The maker arrives the same way the function builder does, because
     * MAKE belongs to the evaluator and the reader must not reach upward for
     * it. Until one is handed over, the answer is what it was.
     */
    private Value madeByTheEvaluator(Datatype datatype, List<Value> contents) {
        if (maker == null) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        Value specification = contents.size() == 1 || readsOneLooseValue(datatype)
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

    /**
     * A datatype whose maker reads the first value where the others read the
     * whole block.
     *
     * <p>The C hands a maker a pointer into the block and lets it decide how
     * far to read, so a difference like this one does not need saying there.
     * {@code Make_Time} takes a bare integer as a count of seconds and only
     * reads hours, minutes and seconds when it is handed a block, which makes
     * {@code #(time! 1 2 3)} one second where {@code make time! [1 2 3]} is
     * an hour, two minutes and three seconds. Both were checked against a
     * real Rebol.
     */
    private static boolean readsOneLooseValue(Datatype datatype) {
        return datatype == Datatype.TIME;
    }

    /**
     * How a construct is made, for every datatype the reader does not build
     * itself. {@code Make_Dispatch} in the C.
     */
    private static volatile
            java.util.function.BiFunction<Datatype, Value, Value> maker;

    public static void makeValuesWith(
            java.util.function.BiFunction<Datatype, Value, Value> builder) {
        maker = builder;
    }

    /**
     * How a function construct is built. The evaluator owns spec parsing,
     * so it hands the reader a builder at boot rather than the reader
     * reaching upward for one.
     */
    private static volatile
            java.util.function.BiFunction<BlockValue, BlockValue, Value> functionBuilder;

    public static void buildFunctionsWith(
            java.util.function.BiFunction<BlockValue, BlockValue, Value> builder) {
        functionBuilder = builder;
    }

    /**
     * An object built from a block of set-words and values.
     *
     * <p>The block is taken exactly as written and never evaluated,
     * because the reader has no context to evaluate in. That is what lets
     * an object round-trip through MOLD, and it is the construct Rebol's
     * own series-test.r3 and object-test.r3 both stop at.
     */
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

    /** The UTF-16 range no character may hold: `IS_SURROGATE` in the C. */
    private static boolean isSurrogate(int codepoint) {
        return codepoint >= 0xD800 && codepoint <= 0xDFFF;
    }

    /** The only three bases a binary may be written in. */
    private static final int BITS = 2;
    private static final int HEXADECIMAL = 16;
    private static final int BASE_64 = 64;

    private BinaryValue readBinary() {
        return readBinary(HEXADECIMAL);
    }

    /**
     * The body of a binary literal, in whichever base was named.
     *
     * <p>Whitespace is ignored wherever it falls and a semicolon starts a
     * comment to the end of the line, so a long binary can be broken
     * across lines with a note beside it.
     *
     * <p>A body that does not fill its last byte is padded rather than
     * refused: the digits gathered so far are shifted up to the width of
     * a byte, which makes `2#{000}` and `16#{0}` both one zero byte.
     */
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

    /** A carriage return ends a comment as a line feed does: NOT_NEWLINE. */
    private static boolean endsAComment(int codepoint) {
        return codepoint == '\n' || codepoint == '\r';
    }

    /** Digits of the given base packed into bytes, the last one padded. */
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

    /** A base 64 body decoded, or a failure if it is not one. */
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

    /**
     * A binary written in a named base.
     *
     * <p>Only 2, 16 and 64 exist, and the base has to be written plainly:
     * a sign or a leading zero is refused. A real R3 complains about the
     * integer rather than about the binary for all of these, which is the
     * clue that it reads the base as a number before it looks at the
     * braces at all.
     */
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

    /** The characters a word may be built from without any letters. */
    private static final String SYMBOL_CHARACTERS = "<>=+-|~&*";

    /**
     * A tag, or one of the words that look like one.
     *
     * <p>A run starting with {@code <} that holds nothing but symbol
     * characters is a word however it ends, so {@code <>}, {@code <=} and
     * {@code <-->} are all words. Anything else that closes with {@code >}
     * is a tag.
     *
     * <p>Closing with {@code >} is not the test, which is what this used,
     * and it made {@code <-->} a tag. Rebol's own lexer-test.r3 asserts
     * that case on line 338, and getting it wrong cost the 444 assertions
     * in that file.
     */
    private Value readAngled() {
        int scout = position;
        while (scout < codepoints.length
                && SYMBOL_CHARACTERS.indexOf(codepoints[scout]) >= 0) {
            scout++;
        }
        if (scout > position && scout < codepoints.length && codepoints[scout] == ':'
                && (scout + 1 >= codepoints.length
                        || isDelimiterOrSpace(codepoints[scout + 1]))) {
            StringBuilder named = new StringBuilder();
            while (position < scout) {
                named.appendCodePoint(peek());
                advance();
            }
            advance();
            return WordValue.of(named.toString(), Datatype.SET_WORD);
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

    /**
     * What an unquoted file may not hold. {@code Scan_File}'s first line.
     *
     * <p>{@code const REBYTE *invalid = cb_cast(":;()[]\"^");} -- eight
     * characters, and the caret is the surprising one, because it is an escape
     * everywhere else in the language.
     */
    private static final String REFUSED_IN_A_FILE = ":;()[]\"^";

    /**
     * And what a quoted one may not. {@code invalid = cb_cast(":;\"");}
     *
     * <p>Five of the eight come off the list, which is the point of the form: a
     * name holding a space or a bracket has to be spellable somehow. The caret
     * comes off with them and becomes an escape again.
     */
    private static final String REFUSED_IN_A_QUOTED_FILE = ":;\"";

    /**
     * A file literal, checked character by character.
     *
     * <p>{@code Scan_File} chooses the refused set and hands the rest to
     * {@code Scan_Item}, which is where every rule lives: a control character is
     * refused, a backslash quietly becomes a forward slash, a percent sign wants
     * two hex digits after it, and anything in the refused set ends the read with
     * a failure rather than with a file.
     *
     * <p>This used to take everything up to the next space. Which read
     * {@code %a^b} and {@code %a%2h} as files and let a typo become a filename --
     * five of Rebol's own lexer assertions, and the reader is the wrong place to
     * be generous.
     */
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

    /**
     * One character of a file name, with the escapes read and the rest checked.
     *
     * <p>In the C's order, because the order decides the answer: the control
     * check comes first and catches a raw tab before the refused set is consulted,
     * and the backslash is rewritten before the escapes are looked for.
     */
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

    /**
     * The byte two hex digits name, which is how a file name holds a space.
     *
     * <p>{@code Scan_Hex2} wants exactly two, and anything else is a failure
     * rather than a literal percent sign. So {@code %a%2h} is not a file.
     */
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

    /** A hex digit's value, or -1 for anything that is not one. */
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

    /**
     * A lexeme, unless it is a signed money literal.
     *
     * <p>{@code -$1} is one value rather than the word {@code -} followed
     * by money, and the sign has to be noticed before the lexeme reader
     * runs, because the dollar sign ends a lexeme.
     */
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

    /** The digits after the dollar sign, with the sign applied. */
    private MoneyValue moneyOf(String digits, boolean negative) {
        return moneyOf(digits, negative, (negative ? "-$" : "$") + digits);
    }

    /**
     * The same, told what the whole token was so the failure can report it.
     *
     * <p>{@code Scan_Error} names the token kind in ARG1 and its text in ARG2, and
     * Rebol's money group compares the second: `e/arg2 = "$1*$2"`. An amount that is
     * not a number is the whole of what can go wrong here, and the four spellings it
     * asserts are all a money literal run into an operator with no space --
     * {@code $1*$2}, {@code $1+$2}, {@code $1-$2}, {@code $1/$2}. Each is one token
     * as far as the reader is concerned, and none of them is a number.
     */
    private MoneyValue moneyOf(String digits, boolean negative, String token) {
        if (digits.indexOf('/') >= 0) {
            throw failureReading(SyntaxFailure.INVALID_LEXEME, "money",
                    token.substring(0, token.indexOf('/') + 1));
        }
        try {
            BigDecimal amount = new BigDecimal(digits);
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

    /**
     * Copies a {@code #"c"} into the lexeme.
     *
     * <p>A quote ends a lexeme everywhere else, so a character literal used
     * as a path segment was cut in two: {@code b/#"a"} read as the path
     * {@code b/#} and a string beside it, where Rebol reads one path. Same
     * number of assertions either way, which is why counting them could
     * never have found it.
     */
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

    /**
     * Copies a balanced {@code (...)} into the lexeme.
     *
     * <p>Counts depth rather than stopping at the first close, because a
     * segment may hold a paren of its own. Anything inside is copied
     * verbatim and read later, when the segment is turned into a value.
     */
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
                || codepoint == ';'
                || codepoint == ',';
    }

    private static final Pattern URL = Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]*:.+");
    private static final Pattern SLASHED_DATE =
            Pattern.compile("\\d{1,4}/[A-Za-z0-9]+/\\d{1,4}");
    private static final Pattern HYPHENATED_DATE =
            Pattern.compile("(\\d{1,4})-([A-Za-z]{3,}|\\d{1,2})-(\\d{1,4})");
    /**
     * A date carrying a time, and perhaps an offset:
     * {@code 1-Jan-2000/12:30:15+2:00}.
     *
     * <p>One value rather than three, and it has to be matched before the path
     * reader gets a look at it: the separator between the day and the time is a
     * slash, so {@code 1-Jan-2000/12:00} otherwise reads as a path of a date
     * and a time. That path molds identically to the date, which is how it went
     * unnoticed -- the answer looked right and was of the wrong datatype, so
     * every date field read off it was none.
     *
     * <p>The offset needs its colon. A real R3 reads {@code +2} and {@code Z} as
     * no offset at all rather than as two hours or as Zulu, so both fall into
     * the group and are read as zero.
     *
     * <p>ISO 8601 is the same thing spelled differently, and it is a date
     * literal here rather than a string a codec parses: a T stands where the
     * slash does, so {@code 2000-01-01T10:00+02:00} is the value
     * {@code 1-Jan-2000/10:00+2:00} is. Its offset has no colon and does
     * count, because four digits are an hour and a minute run together --
     * only the two-digit {@code +01} means nothing.
     */
    private static final Pattern DATE_WITH_TIME = Pattern.compile(
            "(\\d{1,4}[-/](?:[A-Za-z]{3,}|\\d{1,2})[-/]\\d{1,4})"
                    + "(?:[/Tt](\\d{1,2}:\\d{1,2}(?::\\d{1,2}(?:\\.\\d+)?)?))?"
                    + "([-+]\\d{1,2}:\\d{1,2}|[-+]\\d{4}|[-+]\\d{1,2}|[Zz])?");
    private static final Pattern PAIR = Pattern.compile(
            "([-+]?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)"
                    + "[xX]([-+]?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)");
    /**
     * The four shapes {@code Scan_Time} lists, and the fraction that changes the
     * meaning of the first two.
     *
     * <pre>
     * //    HH:MM       as part1:part2
     * //    HH:MM:SS    as part1:part2:part3
     * //    HH:MM:SS.DD as part1:part2:part3.part4
     * //    MM:SS.DD    as part1:part2.part4
     * </pre>
     *
     * <p>A two-part time with a fraction is the last of those: {@code 12:34.5} is
     * twelve <em>minutes</em> and 34.5 seconds, where {@code 12:34} is twelve hours
     * and thirty-four minutes. Which is why the pattern has to allow a fraction on
     * the second component and {@link #readTime} has to look for it.
     */
    private static final Pattern TIME = Pattern.compile(
            "([-+]?\\d+):(\\d{1,2}(?:\\.\\d+)?)(?::(\\d{1,2}(?:\\.\\d+)?))?");
    private static final Pattern TUPLE = Pattern.compile("\\d+(?:\\.\\d+){2,}");
    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d+(?:'\\d+)*");
    private static final Pattern DECIMAL =
            Pattern.compile("[-+]?(?:\\d+\\.\\d*|\\.\\d+|\\d+)(?:[eE][-+]?\\d+)?");
    /**
     * A percent, which is a decimal with a {@code %} after it -- exponent
     * included.
     *
     * <p>Rebol scans the number and then looks at what follows, so anything
     * that reads as a decimal reads as a percent with a {@code %} on the end.
     * Spelling the number out here instead left the exponent off, and
     * {@code 1e18%} was refused while {@code 1e18} and {@code 50%} were both
     * fine. It is line 18 of Rebol's own percent-test.r3 and it hid the other
     * thirty-four assertions in the file.
     */
    private static final Pattern PERCENT = Pattern.compile(
            "([-+]?(?:\\d+\\.\\d*|\\.\\d+|\\d+)(?:[eE][-+]?\\d+)?)%");
    private static final Pattern DATATYPE = Pattern.compile("([a-zA-Z][a-zA-Z0-9-]*)!");
    private static final String[] MONTH_NAMES = {
        "jan", "feb", "mar", "apr", "may", "jun",
        "jul", "aug", "sep", "oct", "nov", "dec"
    };

    /**
     * Characters a word may not contain, however it is spelled.
     *
     * <p>A real R3 also refuses % # $ \ and a comma inside a word, and
     * those are deliberately left out here. A hash is how a based number
     * and a based binary are written -- `2#01`, `64#{...}` -- so a rule
     * that refuses one in a word has to run after those forms have been
     * recognised, not before. Refusing it here turned `64#{` into the
     * integer 64 and broke a source file that reads perfectly well.
     */
    private static final String NOT_IN_A_WORD = "<>%#$\\";

    /** Where the first angle bracket falls, or -1 if there is none. */
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

    /** Whether every character is one a symbol-only word may be made of. */
    private static boolean allSymbols(String lexeme) {
        return !lexeme.isEmpty() && lexeme.chars().noneMatch(Character::isLetterOrDigit);
    }

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
        if (lexeme.matches("[+-][0-9]+#?") && (peek() == '#' || peek() == '{')) {
            throw failureReading(SyntaxFailure.INVALID_LEXEME, "integer", lexeme);
        }
        if (lexeme.matches("[0-9]+#")) {
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
        int offending = firstOffendingCharacter(lexeme);
        int bracket = firstAngleBracket(lexeme);
        if (bracket > 0 && (offending < 0 || bracket < offending
                || !(classifyPlain(lexeme.substring(0, offending)) instanceof WordValue))) {
            offending = Math.min(bracket, offending < 0 ? bracket : offending);
            if (!(classifyPlain(lexeme.substring(0, bracket)) instanceof WordValue)) {
                offending = bracket;
            }
        }
        if (offending == 0 && !allSymbols(lexeme)) {
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

    /**
     * Whether the value ends here, or the angle bracket has spoiled it.
     *
     * <p>Two rules meet, and which applies depends on what the reader had before
     * the bracket.
     *
     * <p><b>A number simply ends.</b> A path is assembled from separate tokens, so
     * the last segment of {@code a/3<} is scanned as a number and a number stops at
     * any character that is not a digit. The word rule is never consulted, which is
     * why {@code a/3<} loads as {@code [a/3 <]}. The same is true without a path:
     * {@code 1<}, {@code 1.0<a>} and {@code 1.#INF<} all end at the bracket.
     *
     * <p><b>A word obeys {@code scanword}</b>, whose comment states it outright:
     * "Allow word&lt;tag&gt; and word&lt;/tag&gt; but not word&lt; word&lt;=
     * word&lt;&gt; etc."
     *
     * <pre>
     * if (cp[1] == '&lt;' || cp[1] == '&gt;' || cp[1] == '=' ||
     *     IS_LEX_SPACE(cp[1]) || (cp[1] != '/' &amp;&amp; IS_LEX_DELIMIT(cp[1])))
     *     return -type;
     * </pre>
     *
     * <p>So the character after the bracket decides. A name or a slash means a tag
     * or an arrow word is beginning and the word is finished. Another bracket, an
     * equals, a space or the end of input means somebody wrote an operator hard
     * against a name, and that is a mistake rather than two values.
     *
     * <p>Which is what separates {@code a/3<} from {@code a/b<}: the same path
     * shape, the same bracket, and the last segment is the whole difference. Rebol's
     * own lexer test asserts the pair side by side.
     */
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

    /**
     * Where the first character a word may not hold sits, or -1.
     *
     * <p>Skips whatever is inside a parenthesised group, because a path may
     * carry one and its contents are a value in their own right rather than
     * part of the word. Judging them by the word's rules truncated the lexeme
     * at the offending character and re-read from there, so {@code m/(<A>)}
     * became the path {@code m/(}, then a tag, then a stray close bracket.
     * A char literal in a path went the same way: {@code b/#"a"} was read as
     * {@code b/#} and a separate string.
     */
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
        if (URL.matcher(lexeme).matches()) {
            return StringValue.of(lexeme, Datatype.URL);
        }
        if (SLASHED_DATE.matcher(lexeme).matches()) {
            return readDate(lexeme, "/");
        }
        var dated = DATE_WITH_TIME.matcher(lexeme);
        if (dated.matches() && (dated.group(2) != null || dated.group(3) != null)) {
            return readDateWithTime(dated.group(1), dated.group(2), dated.group(3));
        }
        if (lexeme.indexOf('/') >= 0) {
            return readPath(lexeme);
        }
        if (lexeme.endsWith(":") && lexeme.length() > 1) {
            String named = lexeme.substring(0, lexeme.length() - 1);
            refuseTheNoneWordAsAName(named, "word-set", lexeme);
            return WordValue.of(named, Datatype.SET_WORD);
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

    /**
     * An email's text, with its escapes read and its at-signs counted.
     *
     * <p>{@code Scan_Email} writes out the percent rule rather than sharing
     * {@code Scan_Item}, and adds one of its own: exactly one at-sign.
     * {@code if (*cp == '@') { if (at) return 0; at = TRUE; }} on the way through
     * and {@code if (!at) return 0;} at the end, so two is as wrong as none.
     *
     * <p>Nothing else is refused. An email is not a file and shares none of the
     * eight characters a file turns away, which is why this is a second function
     * rather than another call to the first.
     */
    private String emailBodyOf(String lexeme) {
        StringBuilder text = new StringBuilder();
        boolean seenAnAtSign = false;
        for (int at = 0; at < lexeme.length(); at++) {
            char character = lexeme.charAt(at);
            if (character == '@') {
                if (seenAnAtSign) {
                    throw failure(SyntaxFailure.INVALID_LEXEME, null);
                }
                seenAnAtSign = true;
            }
            if (character == '%') {
                if (at + 2 >= lexeme.length()) {
                    throw failure(SyntaxFailure.INVALID_LEXEME, null);
                }
                int high = hexDigitValue(lexeme.charAt(at + 1));
                int low = hexDigitValue(lexeme.charAt(at + 2));
                if (high < 0 || low < 0) {
                    throw failure(SyntaxFailure.INVALID_LEXEME, null);
                }
                text.append((char) (high * 16 + low));
                at += 2;
                continue;
            }
            text.append(character);
        }
        if (!seenAnAtSign) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        return text.toString();
    }

    /**
     * A sigil with something after it that cannot follow one.
     *
     * <p>{@code Scan_Token} answers a *negative* token for each of these, and a
     * negative token is a syntax failure. Nine cases across three sigils, and each
     * carries the C's own comment:
     *
     * <pre>
     * case LEX_SPECIAL_TICK:
     *     if (IS_LEX_NUMBER(cp[1])) return -TOKEN_LIT;   // no '2nd
     *     if (cp[1] == ':') return -TOKEN_LIT;           // no ':X
     *     if (cp[1] == '_' && IS_LEX_DELIMIT(cp[2])) return -TOKEN_LIT;   // no '_
     *     ...
     *     if ((*cp == '-' || *cp == '+') && IS_LEX_NUMBER(cp[1])) return -TOKEN_WORD;
     *     if (*cp == '\'') return -TOKEN_LIT;            // no ''foo
     *
     * case LEX_SPECIAL_COLON:
     *     if (cp[1] == '_' &amp;&amp; IS_LEX_DELIMIT(cp[2])) return -TOKEN_GET;   // no :_
     *     if (cp[1] == '\'' || cp[1] == ':') return -TOKEN_WORD; // no :'foo ::foo
     *
     * case LEX_DELIMIT_SLASH:
     *     if (*(scan_state->end - 1) == ':') return -type;   // no /a:
     * </pre>
     *
     * <p>None of them is arbitrary. A sigil names a word and each of these asks
     * for a word that cannot exist: one starting with a digit, one that is itself
     * a sigil, one that is the none literal, one already carrying a sigil at the
     * other end. JEBOL read every one as a perfectly good lit-word or get-word.
     */
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

    /** {@code 2#01} and {@code 16#FF}: digits, a hash, then the number. */
    private static final Pattern BASED_INTEGER =
            Pattern.compile("(\\d{1,2})#([0-9A-Za-z]+)");

    private Value classifyScalarOrWord(String lexeme) {
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
            return DecimalValue.percent(Double.parseDouble(percent.group(1)) / 100.0);
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
        if (DECIMAL.matcher(lexeme).matches() && lexeme.matches(".*[\\d].*")) {
            return DecimalValue.of(Double.parseDouble(lexeme));
        }
        if (Character.isDigit(lexeme.charAt(0))) {
            throw failureReading(SyntaxFailure.INVALID_LEXEME, "integer", lexeme);
        }
        return WordValue.of(lexeme);
    }

    /** 1.#INF, -1.#INF or 1.#NaN, or null when the lexeme is none of them. */
    private static Value specialDecimal(String lexeme) {
        return switch (lexeme.toUpperCase(java.util.Locale.ROOT)) {
            case "1.#INF", "+1.#INF" -> DecimalValue.of(Double.POSITIVE_INFINITY);
            case "-1.#INF" -> DecimalValue.of(Double.NEGATIVE_INFINITY);
            case "1.#NAN", "-1.#NAN", "+1.#NAN" -> DecimalValue.of(Double.NaN);
            default -> null;
        };
    }

    /**
     * A number written in another base, such as {@code 2#01} or
     * {@code 16#FF}.
     *
     * <p>Read unsigned and then taken as signed, so sixty-four ones in
     * base two is minus one rather than an overflow. Past that width there
     * is nowhere to put it, and a digit the base does not have is refused
     * rather than quietly ending the number early.
     */
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

    /**
     * The value a datatype name stands for, used by construction syntax.
     *
     * <p>Only reached from {@code #(integer!)}. A bare {@code integer!} in
     * source is a word, and what it names is decided when it is evaluated.
     */
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

    /** Splits on slashes that are not inside a paren segment. */
    private static List<String> splitOutsideParens(String body) {
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        int depth = 0;
        for (int at = 0; at < body.length(); at++) {
            char character = body.charAt(at);
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
        if (DECIMAL.matcher(segment).matches() && segment.matches(".*[\\d].*")) {
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

    /**
     * Refuses a path segment that reads as nothing and cannot be a word
     * either.
     *
     * <p>Falling back to a word is right for a name and wrong for anything
     * starting with a digit, because no word may: {@code 2013/11/08T17:01Z0100}
     * was becoming the path {@code [2013 11 08T17:01Z0100]} with a word on the
     * end, where a real 3.22.1 refuses the whole lexeme. The same text with
     * hyphens was already refused, and only the slash sent it down this road.
     *
     * <p>Only where the segment reads as nothing at all. A segment that reads
     * as several values is a different thing entirely -- {@code a/3<} is the
     * path {@code a/3} and then a word, and the lexer sorts that out further
     * up rather than here.
     */
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

    private Value readTime(String first, String second, String third) {
        if (!isMinutesAndSeconds(second, third)) {
            return timeOf(first, second, third);
        }
        boolean negative = first.startsWith("-");
        String minutes = first.startsWith("-") || first.startsWith("+")
                ? first.substring(1)
                : first;
        return timeOf(negative ? "-0" : "0", minutes, second);
    }

    /**
     * Whether a two-part time means minutes and seconds rather than hours and
     * minutes.
     *
     * <p>{@code if (part3 >= 0 || part4 < 0)} chooses HH:MM mode and the else is
     * MM:SS, so it takes both an absent third part and a fraction on the second.
     * {@code 12:34.5} is twelve minutes; {@code 12:34} and {@code 12:34:56.7} are
     * twelve hours.
     *
     * <p>A fraction of zero does not count, because {@code Grab_Int_Scale} is
     * followed by {@code if (part4 == 0) part4 = -1;} -- so {@code 12:34.0} is
     * twelve hours and thirty-four minutes.
     */
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

    /**
     * A date, in either of the two orders {@code Scan_Date} accepts.
     *
     * <p>The order is decided by how many digits the first part has, not by
     * what the numbers could plausibly mean: {@code if (size >= 4) year = num;
     * else if (size) day = num;}. So {@code 2000-01-01} is the first of
     * January and {@code 1-1-2000} is as well, and reading the first as a day
     * of 2000 threw an {@code IllegalArgumentException} out of the reader --
     * which took a whole test run with it, because make-test.r3 has one on
     * line 30.
     *
     * <p>The last part is read by digit count too. Three or more digits is
     * the year as written, which is what makes {@code 1-Feb-0003} the year
     * three rather than 2003. Two digits or fewer is a shorthand the C
     * resolves against the year it is running in, keeping inside fifty years
     * either way, so the century a bare {@code 96} means is not a constant
     * and cannot be written as one.
     */
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

    /**
     * A date with a time of day and perhaps an offset.
     *
     * <p>An offset written without a time gives a time of 0:00 and an offset of
     * zero, which is what a real R3 answers: {@code 1-Jan-2000+2:00} molds as
     * {@code 1-Jan-2000/0:00} and reads its zone as 0:00. The offset is
     * consumed and not kept, because there is nothing yet to offset when it
     * arrives.
     */
    private Value readDateWithTime(String day, String time, String offset) {
        DateValue date = (DateValue) readDate(day, day.indexOf('-') >= 0 ? "-" : "/");
        TimeValue timeOfDay = time == null
                ? TimeValue.ofNanoseconds(0)
                : timeFromText(time);
        return new DateValue(date.year(), date.month(), date.day(),
                Optional.of(timeOfDay),
                Optional.of(time == null ? 0 : offsetMinutesFrom(offset)));
    }

    /** {@code 12:30:15.25} as a time, the same three fields the lexer reads. */
    private TimeValue timeFromText(String written) {
        var parts = TIME.matcher(written);
        if (!parts.matches()) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        return (TimeValue) readTime(parts.group(1), parts.group(2), parts.group(3));
    }

    /**
     * An offset in minutes, or zero where none was written.
     *
     * <p>Zero for {@code Z} and for a bare {@code +2} as well, because that is
     * what a real R3 makes of both: an offset written the REBOL way needs its
     * colon to count.
     *
     * <p>The ISO spelling has no colon and does count. Four digits are an hour
     * and a minute run together, so {@code +0100} is the hour that
     * {@code +1:00} is, and it is only the two-digit {@code +01} that means
     * nothing.
     */
    private static int offsetMinutesFrom(String written) {
        if (written == null) {
            return 0;
        }
        int colon = written.indexOf(':');
        if (colon < 0) {
            return written.length() == 5 ? isoOffsetMinutesFrom(written) : 0;
        }
        return signedMinutes(written.charAt(0),
                Integer.parseInt(written.substring(1, colon)),
                Integer.parseInt(written.substring(colon + 1)));
    }

    private static int isoOffsetMinutesFrom(String written) {
        return signedMinutes(written.charAt(0),
                Integer.parseInt(written.substring(1, 3)),
                Integer.parseInt(written.substring(3)));
    }

    private static int signedMinutes(char sign, int hours, int minutes) {
        int size = hours * 60 + minutes;
        return sign == '-' ? -size : size;
    }

    /**
     * Refuses a lone underscore where a name belongs.
     *
     * <p>{@code _} is how none is written, so it is not a word and cannot take
     * a sigil: {@code \'_}, {@code :_} and {@code _:} are each a mistake rather
     * than a quoted, read or assigned none. A real R3 reports them as invalid
     * and names which of the three was being read, which is what a script
     * catching the error looks at.
     */
    private void refuseTheNoneWordAsAName(String named, String tokenKind, String lexeme) {
        if (named.equals("_")) {
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

    /**
     * Whether the character here is the one that ends a line.
     *
     * <p>Three spellings and two of them share a character. A line feed ends a line, a
     * carriage return ends a line, and a carriage return followed by a line feed ends
     * one line rather than two -- so the return of such a pair is not the character
     * that ends it, the line feed after it is. From {@code LEX_DELIMIT_RETURN} in
     * {@code Scan_Token}, which steps over the line feed with {@code if (cp[1] == LF)
     * cp++} before letting the count rise once.
     *
     * <p>The order matters and only one way round: a line feed followed by a carriage
     * return is two lines, because only the return looks ahead for a partner.
     */
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

    /**
     * The source line the reader is on, as R3 puts in a syntax error's NEAR.
     *
     * <p>The whole line rather than the offending token, because that is what a
     * person reading the error needs: `(line 2) 1d` says where to look, and the
     * token on its own would not. Written as R3 writes it, so a script comparing
     * the two agrees.
     */
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

    /**
     * A sign and a colon make a token a time, so this is a malformed one rather than
     * the word it looks like. {@code Scan_Time} calls it a hole in its own comment:
     * {@code if (*cp == '-' || *cp == '+') return 0; // small hole: --1:23}
     */
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

    /**
     * The same failure, naming the token the reader was building and the text
     * it was reading.
     *
     * <p>R3 reports both: ARG1 is the kind -- "word-lit", "tag",
     * "end-of-script" -- and NEAR is the line and the fragment. A script
     * catching a syntax error reads those rather than the message, and Rebol's
     * own suite asserts on them.
     */
    private MalformedSource failureReading(SyntaxFailure failure, String tokenKind) {
        return failureReading(failure, tokenKind, null);
    }

    /**
     * The same, naming the text that offended as well as the token kind.
     *
     * <p>{@code Scan_Error} fills three fields from three places, and a script reads
     * each for something different: ARG1 is the kind, ARG2 is the token's own text,
     * and NEAR is the line it sat on. Rebol's money group compares ARG2 --
     * {@code e/arg2 = "$1*$2"} -- so the token has to be carried here rather than
     * recovered from the line.
     */
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

    /** Internal control flow. Never escapes {@link #transcode(String)}. */
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

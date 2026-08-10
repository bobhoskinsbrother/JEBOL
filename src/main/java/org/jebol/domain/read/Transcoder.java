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
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.SeriesValue;
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
            // Cut from the code points rather than from the string. The
            // walk counts code points and String.substring counts UTF-16
            // units, and the two part company at the first character above
            // the Basic Multilingual Plane -- which mold-test.r3 has, and
            // which sliced sixty-six assertions in half.
            spans.add(new SourceSpan(new String(reader.codepoints, from, to - from), from, to));
        }
        return List.copyOf(spans);
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
            boolean outermost = terminator == NO_TERMINATOR
                    && closing == NO_TERMINATOR
                    && enclosing.isEmpty();
            int began = position;

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
                if (terminator == NO_TERMINATOR && closing == NO_TERMINATOR
                        && enclosing.isEmpty()) {
                    topLevelEnds.add(position);
                }
                continue;
            }

            values.add(readValue());
            if (outermost) {
                topLevelStarts.add(began);
                topLevelEnds.add(position);
            }
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
     */
    private Value readFileOrPercentWord() {
        if (beginsNoFilename(peekAt(1))) {
            advance();
            return percentWord("%");
        }
        if (peekAt(1) == '%') {
            // A slash may follow the double word and may not follow the
            // single one: `o/%%` is a path ending in the word, while
            // `%/tmp/a` is an absolute file. The sigil means one thing
            // when a name follows it and another when nothing does.
            if (peekAt(2) != '/' && !beginsNoFilename(peekAt(2))) {
                throw failure(SyntaxFailure.INVALID_LEXEME, null);
            }
            advance();
            advance();
            return percentWord("%%");
        }
        return readFile();
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
        // An @ on its own is an empty ref rather than an error, so the
        // lexeme is only read when there is something to read.
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
            // A caret takes a space as well as the named characters, so
            // "a^ b" is three characters. Refusing it cost mold-test.r3
            // all 217 of its assertions.
            case ' ' -> ' ';
            case '(' -> readParenthesisedEscape();
            // The control codes: a caret before anything from @ to _
            // escapes to that character less sixty-four. Handling only the
            // letters left ^[ invalid, and that one omission is what
            // stopped most of Rebol's own library from loading.
            case '~' -> 127;
            default -> {
                if (escaped >= '@' && escaped <= '_') {
                    yield escaped - '@';
                }
                if (escaped >= 'a' && escaped <= 'z') {
                    yield escaped - 'a' + 1;
                }
                // Anything the table does not name is itself, so there is
                // no such thing as an unknown escape for a character that
                // is there. Refusing them is what stopped several of
                // Rebol's own library files from reading.
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
        // A datatype spelling that names nothing is an invalid-lexeme to the
        // lexer and a malconstruct inside a construct, because the construct
        // is the thing that is malformed rather than the word.
        List<Value> contents;
        try {
            contents = readSequence(')');
        } catch (MalformedSource unreadable) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        if (contents.isEmpty()) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        Value first = contents.getFirst();

        // Everything inside a construct arrives as a word now, including
        // the datatype names, so the name is resolved here rather than by
        // the lexer. Named values first, then datatypes: `#(none)` is the
        // value and `#(none!)` would be the datatype.
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
        try {
            return MapValue.of(readSequence(']'));
        } catch (IllegalArgumentException malformed) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
    }

    private Value datatypeNamed(WordValue word) {
        if (!word.spelling().endsWith("!")) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        return readDatatype(word.spelling().substring(0, word.spelling().length() - 1));
    }

    /**
     * A construct that carries contents, such as {@code #(decimal! 1)}.
     *
     * <p>Only the datatypes that can be built from a single read value are
     * here. A real Rebol answers malconstruct for the rest rather than
     * guessing, so {@code #(char! 65)} is refused even though the conversion
     * would be obvious.
     */
    private Value builtFrom(Datatype datatype, List<Value> contents) {
        // A series construct may carry its position as a second value, so
        // `#(string! "ab" 2)` is that string standing at its second
        // character rather than at its head. It is how MOLD/ALL writes a
        // series that was not at its head, and without it such a mold
        // does not read back.
        if (contents.size() == 2 && contents.get(1) instanceof IntegerValue at) {
            Value whole = builtFrom(datatype, List.of(contents.getFirst()));
            if (!(whole instanceof SeriesValue series)) {
                return whole;
            }
            long wanted = Math.max(1, Math.min(at.magnitude(),
                    series.storageLength() + 1L));
            return series.atIndex((int) wanted);
        }
        if (contents.size() != 1) {
            throw failure(SyntaxFailure.MALCONSTRUCT, null);
        }
        Value only = contents.getFirst();
        return switch (datatype) {
            case DECIMAL -> only instanceof IntegerValue whole
                    ? DecimalValue.of(whole.magnitude())
                    : requireDatatype(only, Datatype.DECIMAL);
            case INTEGER -> requireDatatype(only, Datatype.INTEGER);
            case OBJECT -> objectFrom(only);
            case BITSET -> only instanceof BinaryValue octets
                    ? BitsetValue.of(bytesOf(octets))
                    : requireDatatype(only, Datatype.BITSET);
            // A string-family construct takes text and answers it as the
            // datatype named, so #(file! "ab") is a file rather than the
            // string it was built from.
            case STRING, FILE, URL, EMAIL, TAG, REF -> only instanceof StringValue text
                    ? text.as(datatype)
                    : requireDatatype(only, datatype);
            // The block family converts within itself the same way, so
            // `#(paren! [1 2])` is a paren holding what the block held.
            // A construct naming one of these and holding anything else
            // is refused rather than guessed at: `#(block! 1)` is a
            // malconstruct, not a block of one.
            case BLOCK, PAREN, PATH, SET_PATH, GET_PATH, LIT_PATH ->
                    only instanceof BlockValue items
                            ? items.as(datatype)
                            : requireDatatype(only, datatype);
            default -> throw failure(SyntaxFailure.MALCONSTRUCT, null);
        };
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
        return CharacterValue.of(codepoint);
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
                throw failure(SyntaxFailure.MISSING_CLOSE, OpenDelimiter.BINARY_BRACE);
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
                while (peek() != END_OF_INPUT && peek() != '\n') {
                    advance();
                }
                continue;
            }
            body.appendCodePoint(next);
        }
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
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        int base = Integer.parseInt(spelling);
        if (base != BITS && base != HEXADECIMAL && base != BASE_64) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
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
        if (scout >= codepoints.length || isDelimiterOrSpace(codepoints[scout])) {
            return classify(readLexeme());
        }
        scout = position + 1;
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
        return classify(readLexeme());
    }

    private MoneyValue readMoney() {
        String lexeme = readLexeme();
        String digits = lexeme.substring(1);
        return moneyOf(digits, false);
    }

    /** The digits after the dollar sign, with the sign applied. */
    private MoneyValue moneyOf(String digits, boolean negative) {
        try {
            BigDecimal amount = new BigDecimal(digits);
            return MoneyValue.of(negative ? amount.negate() : amount);
        } catch (NumberFormatException notANumber) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
    }

    // ---- lexemes and their classification --------------------------------

    private String readLexeme() {
        StringBuilder lexeme = new StringBuilder();
        while (peek() != END_OF_INPUT) {
            // A parenthesised group belongs to the lexeme when it is a
            // path segment: `data/(k)` is one path, and stopping at the
            // bracket read it as the word DATA followed by a paren.
            // Five of Rebol's own files use the form and hold a hundred
            // and seventeen definitions between them.
            if (peek() == '(' && lexeme.indexOf("/") >= 0) {
                takeParenthesisedGroup(lexeme);
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
    // Either half may be fractional, because a pair holds two decimals
    // rather than two integers. 1.5x2 is a legal pair and was unreadable
    // while this pattern only took digits.
    //
    // Either half may also carry an exponent, so 3.4e38x1 reads. That form
    // matters more for a pair than for a decimal, because a pair's halves
    // are single precision and 3.4e38 is where they run out: writing the
    // boundary down is how the overflow to 1.#INF can be tested at all.
    // The exponent binds before the x, so 1e3x1 is a pair of 1000 and 1.
    private static final Pattern PAIR = Pattern.compile(
            "([-+]?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)"
                    + "[xX]([-+]?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)");
    private static final Pattern TIME =
            Pattern.compile("([-+]?\\d+):(\\d{1,2})(?::(\\d{1,2}(?:\\.\\d+)?))?");
    private static final Pattern TUPLE = Pattern.compile("\\d+(?:\\.\\d+){2,}");
    // A quote inside the digits is a separator, so 1'000 reads as 1000 and
    // 99'504'028'301'131 reads as one integer. Rebol's own suite writes large
    // numbers this way, and without it the whole lexeme reads as a word --
    // which fails later as an unset word rather than as a syntax error.
    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d+(?:'\\d+)*");
    private static final Pattern DECIMAL =
            Pattern.compile("[-+]?(?:\\d+\\.\\d*|\\.\\d+|\\d+)(?:[eE][-+]?\\d+)?");
    private static final Pattern PERCENT =
            Pattern.compile("([-+]?(?:\\d+\\.\\d*|\\.\\d+|\\d+))%");
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
        for (int at = 0; at < lexeme.length(); at++) {
            if (lexeme.charAt(at) == '<' || lexeme.charAt(at) == '>') {
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
        // A word may not hold < > % # $ \ or a comma, so a lexeme that
        // mixes one of those with letters is refused rather than becoming
        // a word with an impossible name. Confirmed against a real R3:
        // a<b, a>b, a%b, a#b, a$b and a,b all raise.
        //
        // A run made only of symbols is a word however it is spelled, so
        // <, <=, <> and --> are all words. That exception is why the test
        // is on the mixture rather than on the characters alone.
        //
        // And a number followed by such a run splits in two: `1<` is the
        // integer and the word <, while `1<2` raises -- what follows the
        // number has to be a symbol run of its own to be worth splitting
        // off. A word prefix never splits, which is the whole difference
        // between `1<` and `a<`.
        // A lexeme of digits ending in a hash, with a brace next, is a
        // binary saying which base it is written in. It has to be caught
        // here because the reader has already taken `2#` as a lexeme by
        // the time anything can look at it, and the brace is still to
        // come.
        // Digits then a hash is a base, and a base belongs to a binary.
        // `2#{01}` is the binary; `2#"a"` and `1#(logic! 1)` are a number
        // with a hash-form stuck to it, and a real R3 refuses those rather
        // than reading two values that happen to be adjacent.
        //
        // Only digits, because plenty of ordinary words end in a hash --
        // Rebol's own CSS codec has one -- and those are not bases.
        // A signed number with a hash after it is the same mistake seen
        // one character earlier: the reader stops the lexeme at the sign,
        // so `+2#{}` arrives here as `+2` with the hash still to come.
        if (peek() == '#' && lexeme.matches("[+-][0-9]+")) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        if (lexeme.matches("[0-9]+#")) {
            if (peek() == '{') {
                return readBasedBinary(lexeme.substring(0, lexeme.length() - 1));
            }
            if (peek() == '(' || peek() == '"') {
                throw failure(SyntaxFailure.INVALID_LEXEME, null);
            }
        }
        Value read = classifyPlain(lexeme);
        // Checked on the answer rather than on the text, because plenty
        // of legitimate literals hold these characters: 1.#INF and 1.#NaN
        // hold a hash, and so does every based number like 2#01. Only a
        // lexeme that came out as a plain WORD had no other reading, and
        // only then is an illegal character a mistake rather than part of
        // something else.
        int offending = firstOffendingCharacter(lexeme);
        // An angle bracket wins over anything earlier, because it is the
        // one that ends a value rather than spoiling it. `1.#INF<` holds
        // a hash at index two and a bracket at index six, and splitting
        // at the hash leaves "1." -- which is nothing at all.
        int bracket = firstAngleBracket(lexeme);
        if (bracket > 0 && (offending < 0 || bracket < offending
                || !(classifyPlain(lexeme.substring(0, offending)) instanceof WordValue))) {
            offending = Math.min(bracket, offending < 0 ? bracket : offending);
            if (!(classifyPlain(lexeme.substring(0, bracket)) instanceof WordValue)) {
                offending = bracket;
            }
        }
        // A lexeme that starts with one is a word only if it is symbols
        // all the way. `<2` is neither a word nor a tag, and reading it
        // as a word is what let `1<2` split into two values a real R3
        // refuses outright.
        if (offending == 0 && !allSymbols(lexeme)) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        if (offending <= 0 || allSymbols(lexeme) || !(read instanceof WordValue)) {
            return read;
        }
        String before = lexeme.substring(0, offending);
        String after = lexeme.substring(offending);
        // A number ends where an angle bracket begins, and what follows
        // is read afresh: `1.0<` gives the word < and `1.0<a>` gives a
        // tag. Requiring the remainder to be all symbols got the first
        // right and refused the second, because a tag has letters in it.
        boolean startsAnAngleBracket = after.charAt(0) == '<' || after.charAt(0) == '>';
        if ((allSymbols(after) || startsAnAngleBracket)
                && !(classifyPlain(before) instanceof WordValue)) {
            // Put the symbol run back for the next read rather than
            // holding it aside: the reader has one place it takes
            // characters from, and giving it a second would mean every
            // path checking both.
            position -= after.length();
            column -= after.length();
            return classifyPlain(before);
        }
        throw failure(SyntaxFailure.INVALID_LEXEME, null);
    }

    /** Where the first character a word may not hold sits, or -1. */
    private static int firstOffendingCharacter(String lexeme) {
        for (int at = 0; at < lexeme.length(); at++) {
            if (NOT_IN_A_WORD.indexOf(lexeme.charAt(at)) >= 0) {
                return at;
            }
        }
        return -1;
    }

    private Value classifyPlain(String lexeme) {
        // A lone underscore is NONE, and it is what MOLD writes for one.
        // Only on its own: _a and a_ are ordinary words. Without this the
        // round trip is broken in the direction nobody looks, because a
        // molded NONE reads back as a word nothing has bound.
        if (lexeme.equals("_")) {
            return NoneValue.none();
        }
        // Slashes alone are ordinary words: / divides and // takes a
        // remainder. Only a slash with a name after it is a refinement, and
        // only a slash with something before it makes a path.
        if (lexeme.chars().allMatch(character -> character == '/')) {
            return WordValue.of(lexeme);
        }
        if (lexeme.startsWith("/") && lexeme.indexOf('/', 1) < 0) {
            return WordValue.of(lexeme.substring(1), Datatype.REFINEMENT);
        }
        // No guard against a trailing colon: the pattern already needs
        // something after the colon, so `a:` cannot match it and does not
        // need excluding. The guard only ever excluded a url that ends in
        // one -- `tls://:` is how Rebol writes its TLS scheme, and that
        // single character stopped fifty-three of its definitions.
        if (URL.matcher(lexeme).matches()) {
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

    /** {@code 2#01} and {@code 16#FF}: digits, a hash, then the number. */
    private static final Pattern BASED_INTEGER =
            Pattern.compile("(\\d{1,2})#([0-9A-Za-z]+)");

    private Value classifyScalarOrWord(String lexeme) {
        var based = BASED_INTEGER.matcher(lexeme);
        if (based.matches()) {
            return basedInteger(Integer.parseInt(based.group(1)), based.group(2));
        }
        // A name ending in "!" is a word. integer! is a word the system
        // context binds to a datatype, which is why an unknown spelling is
        // a word rather than an error and why #(integer!) exists at all.
        // This used to produce a datatype value here and refuse anything it
        // did not recognise, which is the reader deciding what datatypes
        // exist. See the EveryDatatypeSpellingIsAWord invariant.
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
        // The three decimals that are not written as digits. They are
        // decimal! values, they mold back exactly as written, and nothing
        // else in the language spells them.
        Value special = specialDecimal(lexeme);
        if (special != null) {
            return special;
        }
        if (DECIMAL.matcher(lexeme).matches() && lexeme.matches(".*[\\d].*")) {
            return DecimalValue.of(Double.parseDouble(lexeme));
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
        // Zero is not a base, so it stands for the default one.
        int base = written == 0 ? 16 : written;
        if (base < 2 || base > 16) {
            throw failure(SyntaxFailure.INVALID_LEXEME, null);
        }
        // Base ten is a number and reads signed, so nineteen nines is too
        // big for the word. The others are bit patterns: they fill the word
        // and the top bit is the sign, so sixty-four ones in base two is
        // minus one. What bounds them is how many digits can address the
        // word at all -- twenty-two octal digits reach it, twenty-three
        // cannot mean anything more.
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
        // number! and series! name several datatypes rather than one.
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
                throw failure(SyntaxFailure.INVALID_LEXEME, null);
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
        // A paren segment is read now and evaluated when the path is
        // walked, which is what lets a path select something the source
        // never spelled out.
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
        // A decimal is a legal index too, and truncates towards zero when
        // the path is walked, so `b/1.6` is the first item. Read as a word
        // it selected nothing and raised instead.
        if (DECIMAL.matcher(segment).matches() && segment.matches(".*[\\d].*")) {
            return DecimalValue.of(Double.parseDouble(segment));
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
        // A written tuple always has at least two dots, so the floor here
        // is on the lexeme rather than on the datatype: a tuple keeping
        // fewer than three octets exists, and cannot be written down.
        if (segments.length < TupleValue.MINIMUM_SHOWN_SEGMENTS
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

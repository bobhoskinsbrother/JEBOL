package org.jebol.domain.value;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Renders values back to source text.
 *
 * <p>{@link #mold} produces text the reader reads back as an equal value:
 * strings are quoted, files keep their percent, blocks keep their brackets.
 * {@link #form} produces text for a person: the same values with the
 * punctuation that only a reader needs taken away.
 *
 * <p>The round trip is what keeps code-as-data honest. A block that has been
 * printed and read again must behave as the original did, which is asserted
 * against the fourteen real programs rather than against generated input.
 */
public final class Molder {

    /** The word every object holds for itself. Never molded; see below. */
    private static final String SELF = "self";

    private Molder() {
    }

    /**
     * As {@link #mold}, with an object written on one line.
     *
     * <p>What MOLD/FLAT asks for. It looked correct while the ordinary
     * mold was flat too, which is the way a wrong default hides a
     * missing refinement.
     */
    public static String moldFlat(Value value) {
        return flattened(() -> mold(value));
    }

    /**
     * Any mold at all, written on one line.
     *
     * <p>MOLD/FLAT is a flag rather than a way of molding, so it combines
     * with the others: {@code mold/flat/all} is the construct form on one
     * line, and choosing between the two -- which is what a chain of
     * conditionals picking one function did -- silently threw away whichever
     * refinement lost.
     */
    public static String flattened(Supplier<String> written) {
        return writingOnOneLine(() -> written.get().replaceAll("\\n\\s*", " ")
                .replace("[ ", "[").replace(" ]", "]"));
    }

    /**
     * Whether the mold in progress is forbidden to break a line.
     *
     * <p>{@code MOPT_INDENT}, which MOLD/FLAT sets and which a binary and an
     * image each read before deciding how to lay their digits out. A flag
     * rather than a pass over the finished text, because the breaks a binary
     * writes carry no indent to strip: flattening by replacing every newline
     * with a space turned {@code #\{AAAA\nBBBB\}} into a binary with spaces
     * through the middle of it, which is not a binary at all.
     */
    private static final ThreadLocal<Boolean> WRITING_ON_ONE_LINE =
            ThreadLocal.withInitial(() -> false);

    private static String writingOnOneLine(Supplier<String> written) {
        boolean was = WRITING_ON_ONE_LINE.get();
        WRITING_ON_ONE_LINE.set(true);
        try {
            return written.get();
        } finally {
            WRITING_ON_ONE_LINE.set(was);
        }
    }

    /**
     * How much of the mold the caller asked for, or nothing for all of it.
     *
     * <p>{@code CHECK_MOLD_LIMIT} cuts the count of bytes or pixels down to
     * what the limit could possibly need, and does it *before* the code that
     * decides whether to break lines. So {@code mold/part} of a huge binary
     * with a limit of eight is {@code #\{FFFFFF} and not
     * {@code #\{} followed by a newline: the limit made it a short binary,
     * and a short binary stays on one line.
     *
     * <p>Cutting the finished text instead left the newline in, which is the
     * one character a caller asking for eight is least likely to want.
     */
    private static final int NO_LIMIT = -1;

    private static final ThreadLocal<Integer> AS_MUCH_AS_WAS_ASKED_FOR =
            ThreadLocal.withInitial(() -> NO_LIMIT);

    /** Molds no more than a stated number of characters, which is MOLD/PART. */
    public static String moldWithin(Value value, int characters,
            Function<Value, String> how) {
        int was = AS_MUCH_AS_WAS_ASKED_FOR.get();
        AS_MUCH_AS_WAS_ASKED_FOR.set(characters);
        try {
            String written = how.apply(value);
            return written.length() <= characters
                    ? written
                    : written.substring(0, characters);
        } finally {
            AS_MUCH_AS_WAS_ASKED_FOR.set(was);
        }
    }

    /**
     * As many pixels as a limit could still reach, after what is already
     * written.
     *
     * <p>{@code if (MOLD_REST(mold) < len) len = MOLD_REST(mold)}, where the
     * rest is the characters left rather than the pixels left -- a generous
     * cut, since a pixel costs six, but the one the C makes.
     *
     * <p>What is already written has to come off, and that is what decides
     * the answer here. {@code mold/part/all img 30} has spent twenty-one
     * characters on {@code #(image! 3840x2160 #\{} before a pixel is reached,
     * so nine are left, so nine pixels at most, so fewer than ten and no line
     * break at all. Counting from the whole limit instead left a newline
     * where the C has none.
     */
    private static int asManyPixelsAsTheLimitCouldUse(int pixels, int alreadyWritten) {
        int limit = AS_MUCH_AS_WAS_ASKED_FOR.get();
        return limit == NO_LIMIT
                ? pixels
                : Math.min(pixels, Math.max(0, limit - alreadyWritten));
    }

    /** Source text that reads back as an equal value. */
    public static String mold(Value value) {
        return render(value, true);
    }

    /**
     * Where a series stands, never past the end of what it stands in.
     *
     * <p>{@code // Reset index if it is over series tail: (a: [1 2] b: tail a
     * clear a mold b)} -- the C's own comment, and its own example. Emptying
     * a series leaves every other name for it holding a position that is no
     * longer there, and molding that position would write a construct LOAD
     * could not read: {@code #(path! [] 4)} says the fourth of nothing.
     *
     * <p>Blocks and paths only, because the reset is a line in
     * {@code Mold_Block} and text goes nowhere near it. So the same emptying
     * done to a string really does mold as {@code #(string! "" 9)}, and the
     * two datatypes disagree in a real 3.22 exactly as they do here.
     */
    private static int standsWithin(SeriesValue series) {
        return series instanceof BlockValue
                ? Math.min(series.index(), series.storageLength() + 1)
                : series.index();
    }

    /**
     * Source text that reads back including the series position, which plain
     * MOLD drops.
     *
     * <p>A series not at its head molds as the positioned construct form:
     * {@code mold/all next "123"} is {@code #[string! "123" 2]}, which LOAD
     * reads back as the string at its second character. Rebol's own suite
     * round-trips exactly that. A series already at its head, and anything
     * that is not a series, molds as it always does.
     */
    public static String moldAll(Value value) {
        return writingEverythingOut(() -> {
            if (value instanceof VectorValue vector) {
                return writtenAsAVector(vector, 1, true, vector.index());
            }
            if (value instanceof ImageValue picture) {
                return render(picture, true);
            }
            if (value instanceof SeriesValue series && standsWithin(series) > 1) {
                return "#(" + value.datatype().literalSpelling() + " "
                        + constructBodyOf(series) + " " + standsWithin(series) + ")";
            }
            if (value instanceof StringValue tag && tag.datatype() == Datatype.TAG
                    && tag.storageLength() == 0) {
                return "#(tag! " + moldedText("") + ")";
            }
            return render(value, true);
        });
    }

    /**
     * Whether everything being written is being written the all way.
     *
     * <p>{@code MOPT_MOLD_ALL} is a flag on the mold state, so it is not a
     * choice made once at the top: every value below reads it and writes
     * itself differently for it. A date writes ISO, a typeset writes its
     * construct form, and a path that has to fall back to a construct sets the
     * flag for its own contents whether or not the caller asked for it --
     * {@code if (all) { SET_FLAG(mold->opts, MOPT_MOLD_ALL); ... }}.
     *
     * <p>A field on the molder is what the C has and JEBOL has no molder to
     * put one on, so it sits beside the two the file already keeps for depth
     * and for what is being written twice.
     */
    private static final ThreadLocal<Boolean> WRITING_EVERYTHING_OUT =
            ThreadLocal.withInitial(() -> false);

    private static String writingEverythingOut(Supplier<String> written) {
        boolean was = WRITING_EVERYTHING_OUT.get();
        WRITING_EVERYTHING_OUT.set(true);
        try {
            return written.get();
        } finally {
            WRITING_EVERYTHING_OUT.set(was);
        }
    }

    /**
     * The content of a positioned construct form, molded in the construct
     * body's own notation rather than the value's literal one.
     * {@code Mold_All_String} forces the type to a plain string and
     * {@code Mold_Block} brackets a path, so {@code mold/all next next 'p/p}
     * is {@code #(path! [p p] 3)} and a positioned url quotes its text.
     */
    private static String constructBodyOf(SeriesValue series) {
        return switch (series) {
            case StringValue text -> moldedText(text.head().text());
            case BlockValue block -> "[" + block.head().remaining().stream()
                    .map(Molder::mold).collect(Collectors.joining(" ")) + "]";
            default -> mold(series.head());
        };
    }

    /** Text for a person: strings unquoted, blocks without their brackets. */
    public static String form(Value value) {
        return render(value, false);
    }

    /**
     * A block's items as source text, without the enclosing brackets. REBOL's
     * {@code mold/only}.
     *
     * <p>This is what round-trips a whole script: the reader hands back a
     * block holding the source's values, and molding that block would add a
     * layer of brackets the source never had.
     */
    public static String moldOnly(BlockValue block) {
        return renderLined(block, true, WITH_NO_BRACKETS);
    }

    /**
     * Whether the block being written puts brackets round itself.
     *
     * <p>{@code sep[1]}, which MOLD/ONLY sets to nothing. It decides more
     * than the brackets: a flagged first value breaks the line only when
     * there is a bracket for the break to follow, so the same block writes
     * a leading newline with brackets and none without.
     */
    private static final boolean BETWEEN_BRACKETS = true;

    private static final boolean WITH_NO_BRACKETS = false;

    /**
     * What is already being molded further out, so a cycle stops.
     *
     * <p>A series may hold itself. Rebol writes {@code [1 [...]]} for a block
     * that does and {@code self: #[...]} for a map, and JEBOL recursed until
     * the stack ran out -- which only became reachable when construction
     * syntax started reading maps, and then took seven tests down with a
     * StackOverflowError rather than a wrong answer.
     *
     * <p>Held per thread, because molding is re-entrant and two threads
     * molding at once must not see each other's depth.
     */
    private static final ThreadLocal<Set<Object>> ALREADY_INSIDE =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(
                    new IdentityHashMap<>()));

    private static String render(Value value, boolean forReading) {
        Object nesting = nestingIdentityOf(value);
        if (nesting != null) {
            if (!ALREADY_INSIDE.get().add(nesting)) {
                return alreadyInsideItself(value);
            }
            try {
                return renderOne(value, forReading);
            } finally {
                ALREADY_INSIDE.get().remove(nesting);
            }
        }
        return renderOne(value, forReading);
    }

    /**
     * How a series that holds itself is written: its own delimiters, with
     * nothing but an ellipsis between them.
     *
     * <p>{@code [1 [...]]} rather than {@code [1 ...]}, so the shape of what
     * was there is still visible.
     */
    private static String alreadyInsideItself(Value value) {
        return switch (value) {
            case MapValue ignored -> "#[...]";
            case ObjectValue ignored -> "make object! [...]";
            case BlockValue block -> switch (block.datatype()) {
                case PAREN -> "(...)";
                case PATH, SET_PATH, GET_PATH, LIT_PATH -> "...";
                case HASH -> "make hash! [...]";
                default -> "[...]";
            };
            default -> "...";
        };
    }

    /** What a value nests through, or null when it cannot nest. */
    private static Object nestingIdentityOf(Value value) {
        return switch (value) {
            case BlockValue block -> block.storage();
            case MapValue map -> map;
            case ObjectValue object -> object.context();
            default -> null;
        };
    }

    private static String renderOne(Value value, boolean forReading) {
        return switch (value) {
            case UnsetValue ignored -> forReading ? "#(unset)" : "";
            case NoneValue ignored -> forReading ? "_" : "none";
            case LogicValue logic -> forReading
                    ? (logic.truth() ? "#(true)" : "#(false)")
                    : (logic.truth() ? "true" : "false");
            case IntegerValue integer -> Long.toString(integer.magnitude());
            case DecimalValue decimal -> renderDecimal(decimal);
            case MoneyValue money -> renderMoney(money);
            case CharacterValue character -> forReading
                    ? "#\"" + escape(character.toString()) + "\""
                    : character.toString();
            case PairValue pair -> moldHalf(pair.x()) + "x" + moldHalf(pair.y());
            case EventValue event -> renderEvent(event, forReading);
            case HandleValue handle -> "#(handle! " + handle.typeName() + ")";
            case TupleValue tuple -> tuple.toString();
            case TimeValue time -> time.toString();
            case DateValue date -> WRITING_EVERYTHING_OUT.get()
                    ? date.isoForm()
                    : date.toString();
            case StringValue string -> renderString(string, forReading);
            case BinaryValue binary -> renderBinary(binary, forReading);
            case ImageValue image -> renderImage(image, forReading);
            case GobValue gob -> renderGob(gob, forReading);
            case VectorValue vector -> writtenAsAVector(
                    vector, vector.index(), forReading, 1);
            case BlockValue block -> renderBlock(block, forReading);
            case WordValue word -> forReading ? word.toString() : word.spelling();
            case DatatypeValue datatype -> forReading
                    ? "#(" + datatype.represents().literalSpelling() + ")"
                    : datatype.represents().literalSpelling();
            case TypesetValue typeset -> !forReading
                    ? namesInTheTypeset(typeset)
                    : WRITING_EVERYTHING_OUT.get()
                            ? "#(typeset! [" + namesInTheTypeset(typeset) + "])"
                            : "make typeset! [" + namesInTheTypeset(typeset) + "]";
            case NativeValue native0 -> "#[native! " + native0.nativeName() + "]";
            case FunctionValue function -> renderFunction(function, forReading);
            case OperatorValue operator -> "#[op! " + operator.operatorName() + "]";
            case MapValue map -> renderMap(map, forReading);
            case BitsetValue bitset -> "#(bitset! "
                    + (bitset.isComplemented() ? "not " : "")
                    + moldedBytes(bitset.octets()) + ")";
            case ObjectValue object -> renderObject(object, Datatype.OBJECT, forReading);
            case PortValue port -> renderObject(
                    new ObjectValue(port.context()), Datatype.PORT, forReading);
            case ModuleValue module -> renderObject(
                    new ObjectValue(module.context()), Datatype.MODULE, forReading);
            case ErrorValue error -> renderError(error, forReading);
            case StructValue struct -> renderStruct(struct, forReading);
            case JavaObjectValue host -> "#[java-object! " + host.className() + "]";
        };
    }

    /**
     * A struct, which shows its whole layout only when asked to be readable.
     *
     * <p>Plainly, Rebol writes the identifier it filed the layout under:
     * {@code #(struct! 749277710 [a: 0.0])}. That number is a hash of the
     * layout block, and every struct built from the same layout shares it, so
     * a reader that has seen one can recognise the rest. Under MOLD/ALL the
     * layout itself is written instead, which is the form that reads back.
     */
    private static String renderStruct(StructValue struct, boolean forReading) {
        String layout = forReading
                ? render(struct.spec().declaration(), true)
                : Integer.toUnsignedString(struct.spec().declaration().hashCode());
        return "#(struct! " + layout + " "
                + render(BlockValue.block(struct.body()), forReading) + ")";
    }

    /** How many significant digits a decimal is printed to. */
    private static final int SIGNIFICANT_DIGITS = 15;

    /**
     * Below this exponent the exponent form is used. Above it, the threshold
     * is the digit count rather than a constant, because
     * {@code Emit_Decimal} compares against the digits it was asked for and a
     * pair asks for half as many as a decimal does.
     */
    private static final int SMALLEST_PLAIN_EXPONENT = -6;

    /**
     * Fifteen significant digits, always with a decimal point.
     *
     * <p>Confirmed against a real R3 rather than reasoned about, because the
     * reasoning was wrong: this used to print the shortest form that reads
     * back, on the assumption that R3 must differ from REBOL 2 here. It does
     * not. {@code 0.1 + 0.2} molds as {@code 0.3}, and {@code 10 / 3} as
     * {@code 3.33333333333333}.
     *
     * <p>So {@link Double#toString} is the wrong tool. It gives seventeen
     * digits where fifteen are wanted, and the extra two are exactly the ones
     * that make floating point look broken to whoever is reading the output.
     */
    private static String renderDecimal(DecimalValue decimal) {
        double quantity = decimal.quantity();
        return decimal.datatype() == Datatype.PERCENT && hasDigits(quantity)
                ? trimTrailingZero(renderDouble(quantity * 100.0)) + "%"
                : renderDouble(quantity);
    }

    /**
     * Whether a number is made of digits at all, which infinity and
     * not-a-number are not.
     *
     * <p>{@code Emit_Decimal} writes the four characters of {@code #INF} or
     * {@code #NaN} and then jumps past the end of the function, so the
     * per-cent sign at the bottom of it is never reached. An infinite percent
     * molds as {@code 1.#INF} with no sign after it -- a hundredth of
     * infinity is still infinity, and there is nothing for the sign to mean.
     */
    private static boolean hasDigits(double quantity) {
        return !Double.isNaN(quantity) && !Double.isInfinite(quantity);
    }

    /**
     * How many significant digits a pair's half prints with, and why it is
     * half as many as a decimal's.
     *
     * <p>{@code s-mold.c} molds a pair by calling {@code Emit_Decimal} on
     * each half with {@code mold->digits / 2}, and {@code mold->digits} is
     * fifteen. Seven digits is also about what a single precision half can
     * carry, so the two agree by design rather than by accident: printing
     * more would print digits the half never had.
     */
    private static final int PAIR_HALF_DIGITS = SIGNIFICANT_DIGITS / 2;

    /**
     * A pair's half prints as a whole number when it is one, so 1x2 reads
     * back as 1x2 rather than as 1.0x2.0. This is the only place where a
     * decimal drops its point, and it is why the halves being decimals at
     * all is invisible until you take one out.
     *
     * <p>{@code Emit_Decimal} is passed {@code DEC_MOLD_MINIMAL} for a pair,
     * which is what drops the point, and seven digits rather than fifteen.
     * So {@code 2147483647x1} molds as {@code 2.147484e9x1}: the half holds
     * 2147483648 and only seven of its digits are shown.
     *
     * <p>A negative zero keeps its sign, because the sign is written from the
     * value rather than from the digits. That is how
     * {@code -32767x-32767 % -32767} molds as {@code -0x-0} while still
     * being equal to {@code 0x0}.
     */
    public static String moldHalf(double half) {
        return renderDouble(half, PAIR_HALF_DIGITS, MINIMAL);
    }

    /**
     * {@code DEC_MOLD_MINIMAL}: drop the point rather than putting a zero
     * after it. A decimal keeps its point or it would read back as an
     * integer; a pair half has the {@code x} to say what it is, so it does
     * not need one.
     */
    private static final boolean MINIMAL = true;

    private static final boolean KEEPS_ITS_POINT = false;

    /**
     * The digits MOLD/ALL prints a decimal to, which is every one it has.
     *
     * <p>{@code if (GET_MOPT(mold, MOPT_MOLD_ALL)) len = MAX_DIGITS} at the
     * top of {@code Reset_Mold}. Seventeen is the count at which a double
     * reads back as itself, so {@code mold/all 0.1} is
     * {@code 0.10000000000000001} where {@code mold 0.1} is {@code 0.1}: the
     * first is the number and the second is what a person meant by it.
     */
    private static final int EVERY_DIGIT_A_DOUBLE_HAS = 17;

    private static String renderDouble(double quantity) {
        return renderDouble(quantity, WRITING_EVERYTHING_OUT.get()
                ? EVERY_DIGIT_A_DOUBLE_HAS
                : SIGNIFICANT_DIGITS, KEEPS_ITS_POINT);
    }

    private static String renderDouble(double quantity, int digits, boolean minimal) {
        if (Double.isNaN(quantity)) {
            return "1.#NaN";
        }
        if (Double.isInfinite(quantity)) {
            return quantity > 0 ? "1.#INF" : "-1.#INF";
        }
        if (quantity == 0.0) {
            String zero = 1 / quantity < 0 ? "-0" : "0";
            return minimal ? zero : zero + ".0";
        }

        BigDecimal rounded = new BigDecimal(quantity)
                .round(new MathContext(digits))
                .stripTrailingZeros();
        int exponent = rounded.precision() - rounded.scale() - 1;

        return exponent < SMALLEST_PLAIN_EXPONENT || exponent > digits - 1
                ? withExponent(rounded, exponent, minimal)
                : pointAsWanted(rounded.toPlainString(), minimal);
    }

    /** {@code 1.0e15}: a mantissa that always has a point, and a bare e. */
    private static String withExponent(
            BigDecimal rounded, int exponent, boolean minimal) {

        BigDecimal mantissa = rounded.movePointLeft(exponent).stripTrailingZeros();
        return pointAsWanted(mantissa.toPlainString(), minimal) + "e" + exponent;
    }

    private static String pointAsWanted(String rendered, boolean minimal) {
        return minimal ? trimTrailingZero(withPoint(rendered)) : withPoint(rendered);
    }

    /** A decimal never loses its point, or it would read back as an integer. */
    private static String withPoint(String rendered) {
        return rendered.indexOf('.') >= 0 ? rendered : rendered + ".0";
    }

    private static String trimTrailingZero(String rendered) {
        return rendered.endsWith(".0")
                ? rendered.substring(0, rendered.length() - 2)
                : rendered;
    }

    private static String renderMoney(MoneyValue money) {
        String sign = money.amount().signum() < 0 ? "-" : "";
        return sign + money.currency().orElse("$")
                + money.amount().abs().toPlainString();
    }

    /**
     * {@code #[]} when empty, and one pair per line otherwise.
     *
     * <p>A map is the one thing whose construct form is not the plain form
     * with a datatype name added: {@code #[a: 1]} plainly and
     * {@code #(map! [a: 1])} under MOLD/ALL, so the brackets change shape as
     * well as gaining a name.
     */
    private static String renderMap(MapValue map, boolean forReading) {
        boolean asAConstruct = forReading && WRITING_EVERYTHING_OUT.get();
        String opens = asAConstruct ? "#(map! [" : "#[";
        String shuts = asAConstruct ? "])" : "]";
        if (map.pairCount() == 0) {
            return opens + shuts;
        }
        boolean onSeparateLines = !WRITING_ON_ONE_LINE.get();
        List<Value> flat = map.flattened();
        String pairs = oneLevelIn(() -> {
            StringBuilder written = new StringBuilder();
            for (int at = 0; at < flat.size(); at += 2) {
                if (onSeparateLines) {
                    written.append(aLineIndentedAsDeepAsWeAre());
                } else if (at > 0) {
                    written.append(' ');
                }
                written.append(render(flat.get(at), forReading))
                        .append(' ')
                        .append(render(flat.get(at + 1), forReading));
            }
            return written.toString();
        });
        return opens + pairs
                + (onSeparateLines ? aLineIndentedAsDeepAsWeAre() : "") + shuts;
    }

    /**
     * Escaped for the braced form, which spares the quotes and nothing
     * else: a caret is still doubled or the text would not read back.
     */
    private static String escapeInBraces(String text, boolean bracesAreUnbalanced) {
        StringBuilder escaped = new StringBuilder();
        text.codePoints().forEach(codepoint -> {
            switch (codepoint) {
                case '\n', '"' -> escaped.appendCodePoint(codepoint);
                case '{', '}' -> escaped.append(
                        bracesAreUnbalanced ? "^" + (char) codepoint : (char) codepoint);
                default -> escaped.append(escapedCodepoint(codepoint));
            }
        });
        return escaped.toString();
    }

    /** Whether braces in the text would still pair up inside braces. */
    private static boolean balancedBraces(String text) {
        int open = 0;
        for (int at = 0; at < text.length(); at++) {
            if (text.charAt(at) == '{') {
                open++;
            } else if (text.charAt(at) == '}' && --open < 0) {
                return false;
            }
        }
        return open == 0;
    }

    private static String renderString(StringValue string, boolean forReading) {
        String text = string.text();
        if (!forReading) {
            return string.datatype() == Datatype.TAG ? "<" + text + ">" : text;
        }
        return switch (string.datatype()) {
            case FILE -> moldedFile(text);
            case URL, EMAIL -> wouldNotReadBackAsItself(string)
                    ? constructedString(string)
                    : text;
            case TAG -> "<" + text + ">";
            case REF -> spellsARefTheLexerWouldReadBack(text)
                    ? "@" + text
                    : constructedString(string);
            default -> moldedText(text);
        };
    }

    /**
     * Whether a url or an email needs construction syntax to survive a
     * round trip. {@code Mold_Url}: the text is emitted bare only when the
     * lexer would read it back as the same value, so an empty one, one
     * missing its colon or at-sign, one holding a delimiter, and the other
     * shapes the scanner refuses all fall back to {@code #(url! "...")}.
     */
    private static boolean wouldNotReadBackAsItself(StringValue string) {
        char required = string.datatype() == Datatype.EMAIL ? '@' : ':';
        String remaining = string.text();
        String whole = string.head().text();
        if (remaining.isEmpty() || whole.isEmpty() || remaining.charAt(0) == '%') {
            return true;
        }
        int found = -1;
        for (int at = 0; at < remaining.length(); at++) {
            char letter = remaining.charAt(at);
            if (letter <= 0x20 || letter == 0x7F
                    || "()[]{}\";".indexOf(letter) >= 0
                    || (letter == '/' && required == '@')) {
                return true;
            }
            if (letter == required) {
                if (at == 0) {
                    return true;
                }
                if (found >= 0 && (required == '@' || at == 1)) {
                    return true;
                }
                if (found < 0) {
                    found = at;
                }
            }
        }
        return found < 0 || found == remaining.length() - 1;
    }

    /**
     * The delimiters a ref may not carry, which are the lexer's own.
     *
     * <p>{@code IS_LEX_DELIMIT} in {@code Mold_Ref}. Each one ends a word
     * where it stands, so a ref holding it would read back as a shorter ref
     * followed by something else.
     */
    private static final String LEXER_DELIMITERS = "()[]{}\"/;";

    /**
     * The one character above the control range that a ref cannot hold.
     *
     * <p>{@code if (c == '@') goto mold_ref_all}, the very first test in
     * {@code Mold_Ref}. A second at-sign would make the whole thing an email
     * rather than a ref, so {@code @a@b} does not read back and
     * {@code #(ref! "a@b")} is what gets written.
     */
    private static final char OPENS_AN_EMAIL_INSTEAD = '@';

    /**
     * Whether a ref can be written with its at-sign and nothing else.
     *
     * <p>{@code Mold_Ref} walks the text and keeps only letters and digits.
     * Anything below decimal twenty-one, any space and any delimiter sends
     * the whole thing to the construct form, and so does a second at-sign.
     *
     * <p>Twenty-one is the C's own number and not a rounding of the control
     * range: {@code if (c < 21 || ...)}, which lets the four characters from
     * twenty-one to twenty-four through where the word class would not.
     * Nothing spells a ref with one, and JEBOL writes the same boundary
     * rather than a tidier one that would disagree.
     */
    private static boolean spellsARefTheLexerWouldReadBack(String text) {
        return text.codePoints().noneMatch(codepoint ->
                codepoint == OPENS_AN_EMAIL_INSTEAD
                        || !Character.isLetterOrDigit(codepoint)
                                && (codepoint < 21
                                        || Character.isWhitespace(codepoint)
                                        || codepoint < 0x80 && LEXER_DELIMITERS
                                                .indexOf(codepoint) >= 0));
    }

    private static String constructedString(StringValue string) {
        String whole = string.head().text();
        return "#(" + string.datatype().literalSpelling() + " " + moldedText(whole)
                + (string.index() > 1 ? " " + string.index() : "") + ")";
    }

    /** The longest string molded with quotes before braces are used. */
    private static final int LONGEST_QUOTED = 50;

    /**
     * The characters a file literal escapes as {@code %XX}: the lexer's own
     * delimiters, the control range and space, and the percent and colon.
     * The rest of {@code URL_Escapes}' file set.
     */
    private static final String FILE_DELIMITERS = ";\"()[]{}<>\\^%:";

    /**
     * A file molded, escaping the characters that would not read back as
     * part of one. Without this a space truncated the path and a control
     * character vanished, so {@code load mold} did not round-trip.
     *
     * <p>A file with no name at all is written {@code %""}, because a bare
     * percent sign is not a file: the lexer reads it as the modulo operator,
     * so molding the empty file as {@code %} produced something that read
     * back as a word.
     */
    private static String moldedFile(String text) {
        if (text.isEmpty()) {
            return "%\"\"";
        }
        StringBuilder written = new StringBuilder("%");
        text.codePoints().forEach(codepoint -> {
            if (codepoint <= 0x20 || codepoint == 0x7F
                    || FILE_DELIMITERS.indexOf(codepoint) >= 0) {
                written.append("%").append("%02X".formatted(codepoint));
            } else {
                written.appendCodePoint(codepoint);
            }
        });
        return written.toString();
    }

    /**
     * A plain string molded, choosing quotes or braces as the C does.
     *
     * <p>{@code Mold_String_Series} uses the quoted form only when the text
     * holds no quote, fewer than three newlines, and no more than fifty
     * characters. Otherwise it uses braces, where a quote and a newline
     * stand for themselves and only an unbalanced brace is escaped.
     */
    private static String moldedText(String text) {
        long newlines = text.chars().filter(each -> each == '\n').count();
        boolean quoted = text.indexOf('"') < 0
                && newlines < 3
                && text.codePointCount(0, text.length()) <= LONGEST_QUOTED;
        return quoted
                ? "\"" + escape(text) + "\""
                : "{" + escapeInBraces(text, !balancedBraces(text)) + "}";
    }

    private static String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        text.codePoints().forEach(
                codepoint -> escaped.append(escapedCodepoint(codepoint)));
        return escaped.toString();
    }

    /**
     * One code point as REBOL writes it inside a string or a character.
     *
     * <p>Three regions, and the middle one is the surprise. Below a
     * space, a code point is a caret and the letter sixty-four above it,
     * so 0 is {@code ^@}, 1 is {@code ^A} and 31 is {@code ^_}. That is
     * an escape a reader has to know, not a decoration: it is how REBOL
     * has always written control characters, and the hex form is only
     * for what has no letter. From a space to 126 the character stands
     * for itself, and from 127 up it is {@code ^(7F)} with upper-case
     * hex.
     *
     * <p>Tab and newline have their own spellings and are handled by the
     * caller before this is reached, because {@code ^-} and {@code ^/}
     * are what a person reading the output expects to see.
     */
    private static String escapedCodepoint(int codepoint) {
        if (codepoint == 0x1E || (codepoint >= 0x7F && codepoint <= 0x9F)) {
            return "^(" + "%02X".formatted(codepoint) + ")";
        }
        return switch (codepoint) {
            case '\t' -> "^-";
            case '\n' -> "^/";
            case '"' -> "^\"";
            case '^' -> "^^";
            default -> codepoint < 0x20
                    ? "^" + (char) (codepoint + 0x40)
                    : new String(Character.toChars(codepoint));
        };
    }

    /**
     * Bytes as hex: wrapped in {@code #{}} for MOLD, bare for FORM.
     *
     * <p>The bare form is what lets a binary be looked for inside a
     * string, since FIND forms its needle first.
     */
    private static String hexOf(byte[] octets) {
        StringBuilder hex = new StringBuilder();
        for (byte octet : octets) {
            hex.append("%02X".formatted(octet & 0xFF));
        }
        return hex.toString();
    }

    /**
     * How many pixels a molded image writes to a line, and the count below
     * which it writes them all to one.
     *
     * <p>{@code if (size < 10) indented = FALSE}, with a comment saying why:
     * "use `flat` result for images with less than 10 pixels (looks better in
     * console)". So the same number sets both the width of a line and the
     * point at which lines start.
     */
    private static final int PIXELS_TO_A_LINE = 10;

    /**
     * An image as its size and its pixels, from `Mold_Image_Data`.
     *
     * <p>`Pre_Mold` writes `make image! [` for an ordinary mold and `#(image! `
     * for MOLD/ALL, so the two forms differ in their brackets rather than in
     * their content. Six hex digits a pixel, from the position the image stands
     * at -- `size = VAL_IMAGE_LEN(value)` counts from the index -- except under
     * /ALL, which sets the index to zero first and molds the whole thing.
     *
     * <p>The alpha binary appears only when some pixel needs it, and that is
     * decided by walking the pixels rather than by reading a flag. One byte a
     * pixel, in the same order.
     *
     * <p>Ten pixels to a line once there are ten of them, with the break
     * written before each tenth pixel rather than after, so the digits start
     * on the line below the opening brace and the closing one stands alone.
     */
    private static String renderImage(ImageValue image, boolean forReading) {
        String size = image.storage().wide() + "x" + image.storage().high();
        boolean asAConstruct = WRITING_EVERYTHING_OUT.get();
        ImageValue shown = asAConstruct ? image.head() : image;
        String opens = asAConstruct ? "#(image! " : "make image! [";
        String shuts = asAConstruct ? positionOf(image) + ")" : "]";
        int pixels = asManyPixelsAsTheLimitCouldUse(
                shown.lengthFromHere(), opens.length() + size.length() + " #{".length());
        if (pixels == 0) {
            return opens + size + " #{}" + shuts;
        }
        boolean brokenIntoLines =
                pixels >= PIXELS_TO_A_LINE && !WRITING_ON_ONE_LINE.get();
        StringBuilder colours = new StringBuilder();
        StringBuilder alphas = new StringBuilder();
        for (int pixel = 1; pixel <= pixels; pixel++) {
            if (brokenIntoLines && (pixel - 1) % PIXELS_TO_A_LINE == 0) {
                colours.append("\n");
                alphas.append("\n");
            }
            int[] channels = shown.pixelAt(pixel);
            colours.append("%02X%02X%02X".formatted(channels[0], channels[1], channels[2]));
            alphas.append("%02X".formatted(channels[3]));
        }
        String closing = brokenIntoLines ? "\n}" : "}";
        return opens + size + " #{" + colours
                + (shown.storage().hasAlpha() ? closing + " #{" + alphas : "")
                + closing + shuts;
    }

    /**
     * Where a series stands, written after its contents by MOLD/ALL.
     *
     * <p>{@code Post_Mold} appends the index only when the series is not at
     * its head, so {@code #(image! 8x1 #\{...\})} and
     * {@code #(image! 8x1 #\{...\} 2)} are the same picture read from
     * different places. The pixels are written from the head either way --
     * {@code VAL_INDEX(&val) = 0; // mold all of it} -- because a construct
     * that dropped the pixels behind the position could not put the position
     * back.
     */
    private static String positionOf(SeriesValue series) {
        return series.index() > 1 ? " " + series.index() : "";
    }

    private static String renderBinary(BinaryValue binary, boolean forReading) {
        byte[] octets = new byte[binary.lengthFromHere()];
        for (int offset = 0; offset < octets.length; offset++) {
            octets[offset] = (byte) binary.storage().at(binary.index() + offset);
        }
        return forReading ? moldedBytes(octets) : hexOf(octets);
    }

    /**
     * How many bytes a limit could still reach.
     *
     * <p>{@code CHECK_MOLD_LIMIT} cuts the byte count against the characters
     * left, which is generous -- a byte costs two characters in hex and eight
     * in binary -- but it is the cut the C makes, and it is what stops
     * {@code mold/part} of a thirty-three-megabyte image encoding all of it
     * to throw away everything past the eighth character.
     */
    private static int asManyBytesAsTheLimitCouldUse(int bytes) {
        int limit = AS_MUCH_AS_WAS_ASKED_FOR.get();
        return limit == NO_LIMIT ? bytes : Math.min(bytes, Math.max(0, limit));
    }

    /**
     * Which notation a binary molds in: sixteen, sixty-four or two.
     *
     * <p>{@code Mold_Binary} reads {@code system/options/binary-base} at the
     * moment it writes, so the answer is a property of the interpreter's state
     * and not of the call. FORM does not read it -- {@code form #\{FFAA\}} is
     * {@code FFAA} whatever the option says -- because forming a binary is
     * asking for its digits rather than for source that reads back.
     */
    private static final ThreadLocal<Integer> BINARY_BASE =
            ThreadLocal.withInitial(() -> 16);

    /** Molds binaries in the base the system object currently names. */
    public static String writingBinariesInBase(int base, Supplier<String> written) {
        int was = BINARY_BASE.get();
        BINARY_BASE.set(base);
        try {
            return written.get();
        } finally {
            BINARY_BASE.set(was);
        }
    }

    /**
     * A binary in braces, in the base the system object names, broken into
     * lines once there is enough of it.
     *
     * <p>Each base has its own run length and its own rule for when the run
     * is long enough to be worth breaking at all, and the three do not agree:
     * base sixteen breaks at thirty-two bytes, base two at eight, base
     * sixty-four at forty-eight. Each writes a newline after {@code #\{} as
     * well, so a binary long enough to matter arrives as a block of even
     * lines instead of one that runs off the screen.
     *
     * <p>MOLD only. FORM writes the digits bare and unbroken, because FIND
     * forms its needle before looking for it and a newline in the middle
     * would stop it matching.
     */
    private static String moldedBytes(byte[] whole) {
        byte[] octets = Arrays.copyOf(whole,
                asManyBytesAsTheLimitCouldUse(whole.length));
        boolean mayBreakLines = !WRITING_ON_ONE_LINE.get();
        return switch (BINARY_BASE.get()) {
            case 2 -> "2#{" + base2Digits(octets, mayBreakLines) + "}";
            case 64 -> "64#{" + base64Digits(octets, mayBreakLines) + "}";
            default -> "#{" + base16Digits(octets, mayBreakLines) + "}";
        };
    }

    /** How many bytes of a binary each base writes before breaking the line. */
    private static final int BYTES_TO_A_HEX_LINE = 32;

    private static final int BYTES_TO_A_BINARY_LINE = 8;

    private static final int BYTES_TO_A_BASE_SIXTY_FOUR_LINE = 48;

    /**
     * Base sixty-four writes on one line up to sixty-four bytes, though its
     * runs are forty-eight bytes long.
     *
     * <p>The two numbers come from different places in {@code Mold_Binary} --
     * one decides whether to break at all, the other how often -- and a
     * binary of between forty-nine and sixty-four bytes is where they
     * disagree.
     */
    private static final int BYTES_TO_A_BASE_SIXTY_FOUR_LINE_BREAK = 64;

    /**
     * The digits with a newline before the first and after each full run.
     *
     * <p>The closing brace follows the last run's digits, so a length that is
     * an exact multiple of the run leaves it alone on the line after -- the
     * newline belongs to the full run rather than being written before the
     * brace.
     */
    private static String brokenIntoRuns(String digits, int digitsToARun) {
        StringBuilder written = new StringBuilder("\n");
        for (int at = 0; at < digits.length(); at += digitsToARun) {
            int stops = Math.min(at + digitsToARun, digits.length());
            written.append(digits, at, stops);
            if (stops - at == digitsToARun) {
                written.append("\n");
            }
        }
        return written.toString();
    }

    private static String base16Digits(byte[] octets, boolean mayBreakLines) {
        String digits = hexOf(octets);
        return mayBreakLines && octets.length > BYTES_TO_A_HEX_LINE
                ? brokenIntoRuns(digits, BYTES_TO_A_HEX_LINE * 2)
                : digits;
    }

    /**
     * Eight ones and noughts a byte, and the last of them dropped when there
     * are exactly eight bytes.
     *
     * <p>{@code if (len == 8) --p} in {@code Encode_Base2} was written to
     * remove the newline that a run of eight would have left at the end. At
     * exactly eight bytes there is no newline to remove, because the break is
     * only written when the length is more than eight, so what it removes is
     * the last digit. A real 3.22 does it, {@code mold #\{FFAAFFAAFFAAFFAA\}}
     * comes back a bit short, and JEBOL does it too rather than disagree.
     */
    private static String base2Digits(byte[] octets, boolean mayBreakLines) {
        StringBuilder digits = new StringBuilder();
        for (byte octet : octets) {
            for (int bit = 7; bit >= 0; bit--) {
                digits.append(octet >> bit & 1);
            }
        }
        if (octets.length == BYTES_TO_A_BINARY_LINE) {
            digits.setLength(digits.length() - 1);
        }
        return mayBreakLines && octets.length > BYTES_TO_A_BINARY_LINE
                ? brokenIntoRuns(digits.toString(), BYTES_TO_A_BINARY_LINE * 8)
                : digits.toString();
    }

    private static final String BASE_SIXTY_FOUR_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    /**
     * The one base whose breaks are written as it goes rather than counted
     * over the finished digits.
     *
     * <p>{@code Encode_Base64} breaks on a byte boundary inside the loop over
     * whole groups, and the two or three bytes left over are written after
     * the loop has finished breaking. A run that ends exactly where the
     * leftovers begin therefore carries no newline, which counting
     * sixty-four characters at a time over the finished digits would have
     * put in.
     */
    private static String base64Digits(byte[] octets, boolean mayBreakLines) {
        boolean brokenIntoLines =
                mayBreakLines && octets.length > BYTES_TO_A_BASE_SIXTY_FOUR_LINE_BREAK;
        int wholeGroups = octets.length / 3;
        StringBuilder digits = new StringBuilder(
                brokenIntoLines && 4 * (wholeGroups - 1) > 64 ? "\n" : "");
        for (int at = 0; at < wholeGroups * 3; at += 3) {
            appendSextets(digits, octets, at, 3);
            if (brokenIntoLines
                    && (at + 3) % BYTES_TO_A_BASE_SIXTY_FOUR_LINE == 0) {
                digits.append("\n");
            }
        }
        int leftOver = octets.length % 3;
        if (leftOver != 0) {
            appendSextets(digits, octets, wholeGroups * 3, leftOver);
        }
        return digits.toString();
    }

    private static void appendSextets(StringBuilder digits, byte[] octets,
            int at, int bytes) {
        int held = 0;
        for (int offset = 0; offset < 3; offset++) {
            held <<= 8;
            held |= offset < bytes ? octets[at + offset] & 0xFF : 0;
        }
        for (int sextet = 0; sextet < 4; sextet++) {
            digits.append(sextet <= bytes
                    ? BASE_SIXTY_FOUR_ALPHABET.charAt(held >> 18 - sextet * 6 & 0x3F)
                    : '=');
        }
    }

    /** How deep the mold is inside line-broken blocks, for the indent. */
    private static final ThreadLocal<Integer> LINED_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    private static final String ONE_INDENT = "    ";

    /** {@code New_Indented_Line}: a break and four spaces a level. */
    private static String aLineIndentedAsDeepAsWeAre() {
        return "\n" + ONE_INDENT.repeat(LINED_DEPTH.get());
    }

    /**
     * Writes something one level further in.
     *
     * <p>{@code mold->indent++} around the fields of an object, the pairs of
     * a map and the fields of an event. The counter is shared, so a block
     * inside a map's value is written two levels in and not one, which is
     * what makes a map holding a laid-out block come back laid out.
     */
    private static String oneLevelIn(Supplier<String> written) {
        LINED_DEPTH.set(LINED_DEPTH.get() + 1);
        try {
            return written.get();
        } finally {
            LINED_DEPTH.set(LINED_DEPTH.get() - 1);
        }
    }

    /**
     * How many numbers a vector fits on one line before it breaks.
     *
     * <p>{@code Mold_Vector} counts to ten and starts a new indented line, and
     * only bothers at all when there are more than ten to show. A vector of
     * exactly ten stays on its line.
     */
    private static final int NUMBERS_TO_A_LINE = 10;

    /**
     * A vector as {@code #(int32! [1 2 3])}, or as its numbers alone.
     *
     * <p>{@code positionToName} is what MOLD/ALL adds after the closing
     * bracket so that LOAD reads the value back at the position it was at, and
     * is left off when that position is the head. Plain MOLD passes one, which
     * is the head and so never shows, and shows only what is left from where
     * the value points; MOLD/ALL shows the whole storage.
     */
    private static String writtenAsAVector(VectorValue vector, int from,
            boolean forReading, int positionToName) {
        List<String> numbers = new ArrayList<>();
        for (int at = from; at <= vector.storageLength(); at++) {
            numbers.add(render(vector.elementAt(at), forReading));
        }
        if (!forReading) {
            return String.join(" ", numbers);
        }
        int outer = LINED_DEPTH.get();
        boolean overALine = numbers.size() > NUMBERS_TO_A_LINE;
        StringBuilder out = new StringBuilder("#(")
                .append(vector.kind().spelling()).append(" [");
        if (overALine) {
            out.append('\n').append(ONE_INDENT.repeat(outer + 1));
        }
        for (int at = 0; at < numbers.size(); at++) {
            out.append(numbers.get(at));
            if (at + 1 == numbers.size()) {
                continue;
            }
            if (overALine && (at + 1) % NUMBERS_TO_A_LINE == 0) {
                out.append('\n').append(ONE_INDENT.repeat(outer + 1));
            } else {
                out.append(' ');
            }
        }
        if (overALine) {
            out.append('\n').append(ONE_INDENT.repeat(outer));
        }
        out.append(']');
        if (positionToName > 1) {
            out.append(' ').append(positionToName);
        }
        return out.append(')').toString();
    }

    /**
     * A block written out the shape its author laid it out in.
     *
     * <p>{@code Mold_Block_Series}, and the three things it keeps track of
     * matter more than they look. A break is written *before* a flagged
     * value, so the flag says "this value begins a line" rather than "a line
     * ends here". The indent goes up once, at the first break, and comes down
     * once, before the closing bracket -- so a block laid out over ten lines
     * is indented by one level and not by ten.
     *
     * <p>The first value is the exception. {@code line_flag} is false until
     * one value has been written, and a break for a flagged first value needs
     * either that flag or a bracket to write it against. So a block breaks
     * before its first value and MOLD/ONLY, which writes no brackets, does
     * not: {@code mold/only load "[1^/2]"} is {@code 1^/2} with nothing in
     * front of the one.
     *
     * <p>The closing bracket goes on its own line exactly when the indent
     * went up, which is to say when the first value began a line. A newline
     * before the bracket in the source does not put one there:
     * {@code mold load "[1 2^/]"} is {@code [1 2]}, because the scanner drops
     * a line feed that has no value after it.
     */
    private static String renderLined(BlockValue block, boolean forReading,
            boolean betweenBrackets) {
        boolean mayBreakLines = !WRITING_ON_ONE_LINE.get();
        StringBuilder out = new StringBuilder(
                betweenBrackets ? opensWith(block.datatype()) : "");
        int outer = LINED_DEPTH.get();
        boolean steppedIn = false;
        boolean somethingWritten = false;
        try {
            List<Value> items = block.remaining();
            for (int at = 0; at < items.size(); at++) {
                if (block.storage().breaksLineAt(block.index() + at)
                        && mayBreakLines && (betweenBrackets || somethingWritten)) {
                    if (!steppedIn && !somethingWritten) {
                        steppedIn = true;
                        LINED_DEPTH.set(LINED_DEPTH.get() + 1);
                    }
                    out.append('\n').append(ONE_INDENT.repeat(LINED_DEPTH.get()));
                } else if (at > 0) {
                    out.append(' ');
                }
                somethingWritten = true;
                out.append(render(items.get(at), forReading));
            }
        } finally {
            LINED_DEPTH.set(outer);
        }
        if (!betweenBrackets) {
            return out.toString();
        }
        if (mayBreakLines && steppedIn) {
            out.append('\n').append(ONE_INDENT.repeat(outer));
        }
        return out.append(closesWith(block.datatype())).toString();
    }

    /**
     * The three shapes that write their items between brackets, and so have
     * somewhere to put a line break.
     *
     * <p>A path writes its items between slashes and a line break has nowhere
     * to go, which is why a path carrying one still molds on a single line.
     */
    private static boolean moldsInBrackets(Datatype shape) {
        return shape == Datatype.BLOCK || shape == Datatype.PAREN
                || shape == Datatype.HASH;
    }

    private static String opensWith(Datatype shape) {
        return switch (shape) {
            case PAREN -> "(";
            case HASH -> "make hash! [";
            default -> "[";
        };
    }

    private static String closesWith(Datatype shape) {
        return shape == Datatype.PAREN ? ")" : "]";
    }

    private static String renderBlock(BlockValue block, boolean forReading) {
        if (forReading && moldsInBrackets(block.datatype())) {
            return renderLined(block, forReading, BETWEEN_BRACKETS);
        }
        String items = block.remaining().stream()
                .map(item -> render(item, forReading))
                .collect(Collectors.joining(" "));
        if (!forReading && block.datatype() == Datatype.BLOCK) {
            return items;
        }
        return switch (block.datatype()) {
            case PAREN -> "(" + items + ")";
            case PATH -> joinPath(block, "", "");
            case SET_PATH -> joinPath(block, "", ":");
            case GET_PATH -> joinPath(block, ":", "");
            case LIT_PATH -> joinPath(block, "'", "");
            case HASH -> "make hash! [" + items + "]";
            default -> "[" + items + "]";
        };
    }

    /**
     * A path molded with slashes, or as a construct when it would not read
     * back as one.
     *
     * <p>Two conditions send it to the construct, and the C states both in one
     * line: {@code if (VAL_TAIL <= 1 || !IS_WORD(VAL_BLK_DATA(value)))}.
     *
     * <p>A path of one item cannot be written with slashes at all, because a
     * slash needs something either side of it, so {@code a} on its own is
     * {@code #(path! [a])} even though it is the very word a path may start
     * with.
     *
     * <p>The first item must be a *plain* word, and the strictness is the
     * whole point. A set-word, a get-word, a lit-word, a refinement and an
     * issue are all any-word! and none of them may open a path: {@code a:/b}
     * would read back as a set-path, {@code /a/b} as a refinement, and
     * {@code #a/b} as an issue. {@code IS_WORD} is one datatype, not the
     * typeset, and reading it as the typeset put five kinds of path into a
     * form that does not read back.
     *
     * <p>What comes after the first item may be anything: {@code a/1} and
     * {@code a/b/c} both write themselves plainly.
     *
     * <p>An empty path writes nothing at all -- not even the colon a set-path
     * would carry -- which is the line above the two in the C:
     * {@code if (!MOLD_ALL && VAL_TAIL == VAL_INDEX) return;}. So
     * {@code make set-path! 4} is a path with room for four things and molds
     * as the empty string, where writing the colon alone would read back as
     * something else entirely.
     *
     * <p>Under MOLD/ALL that line does not fire, and an empty path falls to
     * the construct instead: {@code #(path! [])}, which is the only writing
     * of it LOAD reads back. Nothing is not a path.
     */
    private static String joinPath(BlockValue path, String prefix, String suffix) {
        List<Value> segments = path.remaining();
        if (segments.isEmpty() && !WRITING_EVERYTHING_OUT.get()) {
            return "";
        }
        if (wouldNotReadBackAsAPath(path, segments)) {
            return writingEverythingOut(() -> "#("
                    + path.datatype().literalSpelling() + " ["
                    + segments.stream().map(Molder::mold).collect(Collectors.joining(" "))
                    + "])");
        }
        return prefix + segments.stream()
                .map(Molder::mold)
                .collect(Collectors.joining("/")) + suffix;
    }

    /**
     * The two conditions, and each one asks about a different thing.
     *
     * <p>The length is the whole series {@code VAL_TAIL} and not what is left
     * from here, while the first item is {@code VAL_BLK_DATA}, which is the
     * one at the index. Asking the remaining count for both made
     * {@code mold next 'a/b} a construct where it is the plain {@code "b"}: a
     * path standing at its second of two is not a path of one.
     */
    private static boolean wouldNotReadBackAsAPath(BlockValue path, List<Value> segments) {
        return segments.isEmpty()
                || path.storageLength() <= 1
                || segments.getFirst().datatype() != Datatype.WORD;
    }

    /**
     * An event as the fields that answer something.
     *
     * <p>`Pre_Mold`, then each field of a fixed list that is not none, then a
     * bracket. A word gets a quote in front of it -- `if (IS_WORD(&val))
     * Append_Byte(mold->series, '\'')` -- so the mold reads back as the event it
     * molded, which is not true of every datatype here.
     */
    private static String renderEvent(EventValue event, boolean forReading) {
        boolean onSeparateLines = !WRITING_ON_ONE_LINE.get();
        List<Value> spec = event.moldingSpec();
        String fields = oneLevelIn(() -> {
            StringBuilder written = new StringBuilder();
            for (int at = 0; at < spec.size(); at++) {
                boolean opensAField = spec.get(at) instanceof WordValue name
                        && name.datatype() == Datatype.SET_WORD;
                if (onSeparateLines && opensAField) {
                    written.append(aLineIndentedAsDeepAsWeAre());
                } else if (at > 0) {
                    written.append(' ');
                }
                Value shown = spec.get(at);
                boolean quoted = shown instanceof WordValue word
                        && word.datatype() == Datatype.WORD;
                written.append(quoted ? "'" : "").append(render(shown, forReading));
            }
            return written.toString();
        });
        return openedFor(Datatype.EVENT) + "[" + fields
                + (onSeparateLines ? aLineIndentedAsDeepAsWeAre() : "")
                + "]" + closedAfterATypeName();
    }

    /**
     * A function as the spec and body that would build it again.
     *
     * <p>{@code Mold_Function} writes both blocks inside one pair of
     * brackets, so {@code func [a][print a]} molds as
     * {@code make function! [[a][print a]]} and reads back as the same
     * function. FORM gives the same thing: there is no shorter way to say
     * what a function is, so there is nothing for the two to differ about.
     *
     * <p>A closure names itself, because the two are separate datatypes to
     * the reader even though JEBOL holds them in one record.
     *
     * <p>Both blocks are *molded* whichever way the function is being
     * written, because {@code Mold_Block_Series} always writes its brackets.
     * Forming them instead dropped the brackets and left
     * {@code make function! [a print a]}, which is a spec of three words and
     * no body at all.
     */
    private static String renderFunction(FunctionValue function, boolean forReading) {
        Datatype names = function.closure() ? Datatype.CLOSURE : Datatype.FUNCTION;
        return openedFor(names) + "["
                + mold(function.spec().head())
                + mold(function.body().head())
                + "]" + closedAfterATypeName();
    }

    /**
     * A gob as the spec block that would remake it.
     *
     * <p>{@code Pre_Mold}, {@code Gob_To_Block}, {@code End_Mold} -- so a gob
     * molds as {@code make gob! [offset: 0x0 size: 100x100]} and reads back
     * as a gob. Which fields appear is not "the ones that were set": offset
     * and size always, the alpha only when the gob is see-through, and the
     * one content field it has.
     */
    private static String renderGob(GobValue gob, boolean forReading) {
        StringBuilder built = new StringBuilder(openedFor(Datatype.GOB)).append('[');
        List<Value> spec = gob.storage().moldingSpec();
        for (int at = 0; at < spec.size(); at++) {
            if (at > 0) {
                built.append(' ');
            }
            built.append(render(spec.get(at), forReading));
        }
        return built.append(']').append(closedAfterATypeName()).toString();
    }

    /**
     * The objects being rendered further up this call, so a cycle stops.
     *
     * <p>SELF is not the only way an object reaches itself. SYSTEM holds
     * SYSTEM/CONTEXTS/LIB, which is the context SYSTEM is defined in, so
     * molding SYSTEM walks into SYSTEM again. That ends in a
     * StackOverflowError rather than in an error a script could catch,
     * which is the one failure the evaluator promises never to produce.
     */
    private static final ThreadLocal<Set<Context>> BEING_RENDERED =
            ThreadLocal.withInitial(LinkedHashSet::new);

    /**
     * A field's value as it must be written inside an object body.
     *
     * <p>A word is quoted, because the body is read back as a spec and a
     * bare word there would be evaluated. Without this, an object holding
     * the word NONE molds as {@code b: none} and reads back holding the
     * none value, which is a different object.
     */
    private static String renderField(Value value, boolean forReading) {
        return value instanceof WordValue word && word.datatype() == Datatype.WORD
                && !WRITING_EVERYTHING_OUT.get()
                ? "'" + render(value, forReading)
                : render(value, forReading);
    }

    /**
     * An object as the MAKE that would build it again.
     *
     * <p>{@code self} is left out. It refers to the object being molded, so
     * printing it would recurse for ever, and REBOL leaves it out for the
     * same reason. It is still a word inside the object; it is just not a
     * field worth writing down.
     *
     * <p>A port and a module are written the same way and name themselves
     * rather than saying object, because {@code Mold_Object} writes
     * {@code VAL_TYPE(value)} and not a fixed word. JEBOL wrote
     * {@code make object!} for all three, so a molded port did not read back
     * as a port.
     */
    private static String renderObject(
            ObjectValue object, Datatype naming, boolean forReading) {

        Set<Context> enclosing = BEING_RENDERED.get();
        if (!enclosing.add(object.context())) {
            return openedFor(naming) + "[...]";
        }
        try {
            return forReading
                    ? openedFor(naming) + "["
                            + moldedFields(fieldsOutsideSelf(object).collect(
                                    Collectors.toMap(ContextSlot::spelling,
                                            ContextSlot::value,
                                            (older, newer) -> newer,
                                            LinkedHashMap::new)))
                            + "]" + closedAfterATypeName()
                    : formedFields(object);
        } finally {
            enclosing.remove(object.context());
        }
    }

    /**
     * The datatype name a construct or a MAKE puts in front of its body.
     *
     * <p>{@code Pre_Mold} writes {@code #(type! } under MOLD/ALL and
     * {@code make type! } without it, and {@code End_Mold} closes the bracket
     * only in the first case. The pair is what makes {@code mold/all} of an
     * object something LOAD reads back as an object, where the MAKE form
     * needs evaluating.
     */
    private static String openedFor(Datatype datatype) {
        return (WRITING_EVERYTHING_OUT.get() ? "#(" : "make ")
                + datatype.literalSpelling() + " ";
    }

    private static String closedAfterATypeName() {
        return WRITING_EVERYTHING_OUT.get() ? ")" : "";
    }

    /**
     * An error molded, which is an error written out as the object it is.
     *
     * <p>{@code Mold_Error} hands straight over to {@code Mold_Object} when
     * it is molding rather than forming, so {@code mold} of an error is its
     * eight fields and not the one-line summary a person reads. The summary
     * is what FORM gives, and the two are different jobs: one is for reading
     * back and one is for reading.
     */
    private static String renderError(ErrorValue error, boolean forReading) {
        if (!forReading) {
            return error.toString();
        }
        Map<String, Value> fields = new LinkedHashMap<>();
        for (String name : ErrorValue.FIELDS) {
            fields.put(name, error.field(name).orElseGet(NoneValue::none));
        }
        return openedFor(Datatype.ERROR) + "[" + moldedFields(fields) + "]"
                + closedAfterATypeName();
    }

    /**
     * A typeset formed: the names of what it holds, and nothing around them.
     *
     * <p>{@code Mold_Typeset} writes the brackets and the {@code #(typeset!}
     * only when it is molding. Formed, it emits each name followed by a space
     * and trims the last one off, so an empty typeset forms as nothing at all.
     */
    private static String namesInTheTypeset(TypesetValue typeset) {
        return typeset.members().stream()
                .sorted()
                .map(Datatype::literalSpelling)
                .collect(Collectors.joining(" "));
    }

    /**
     * The fields between the brackets, one to a line and a line before the
     * bracket that closes them.
     *
     * <p>{@code Mold_Object} writes {@code New_Indented_Line} before every
     * field and once more at the end, so an object with no fields at all is
     * still two lines. MOLD/FLAT writes them with spaces and nothing else.
     */
    private static String moldedFields(Map<String, Value> fields) {
        boolean onSeparateLines = !WRITING_ON_ONE_LINE.get();
        String written = oneLevelIn(() -> {
            StringBuilder out = new StringBuilder();
            for (Map.Entry<String, Value> field : fields.entrySet()) {
                if (onSeparateLines) {
                    out.append(aLineIndentedAsDeepAsWeAre());
                } else if (!out.isEmpty()) {
                    out.append(' ');
                }
                out.append(field.getKey()).append(": ")
                        .append(renderField(field.getValue(), true));
            }
            return out.toString();
        });
        return written + (onSeparateLines ? aLineIndentedAsDeepAsWeAre() : "");
    }

    /**
     * An object formed: one field to a line, and nothing around them.
     *
     * <p>{@code Form_Object} emits {@code "N: V\n"} for each field and then
     * takes the last newline off again, so there is no {@code make object!}
     * and no brackets -- {@code form make object! [a: 1 b: 2]} is the two
     * lines and nothing else.
     *
     * <p>The value is *molded* even though the object is being formed, which
     * is the part that cannot be guessed: {@code form make object! [a: "x"]}
     * keeps the quotes around the x.
     */
    private static String formedFields(ObjectValue object) {
        return fieldsOutsideSelf(object)
                .map(slot -> slot.spelling() + ": " + renderField(slot.value(), true))
                .collect(Collectors.joining("\n"));
    }

    private static Stream<ContextSlot> fieldsOutsideSelf(
            ObjectValue object) {
        return object.context().slots().stream()
                .filter(slot -> !slot.canonical().equals(SELF));
    }
}

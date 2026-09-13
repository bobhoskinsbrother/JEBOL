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

    private static final String THE_WORD_EVERY_OBJECT_HOLDS_FOR_ITSELF = "self";

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

    private static int standsWithinNeverPastTheEndForABlockOrAPath(
            SeriesValue series) {
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
            if (value instanceof SeriesValue series
                    && standsWithinNeverPastTheEndForABlockOrAPath(series) > 1) {
                return "#(" + value.datatype().literalSpelling() + " "
                        + constructBodyOf(series) + " "
                        + standsWithinNeverPastTheEndForABlockOrAPath(series) + ")";
            }
            if (value instanceof StringValue tag && tag.datatype() == Datatype.TAG
                    && tag.storageLength() == 0) {
                return "#(tag! " + moldedText("") + ")";
            }
            return render(value, true);
        });
    }

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

    private static final boolean BETWEEN_BRACKETS = true;

    private static final boolean WITH_NO_BRACKETS = false;

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

    private static String renderStruct(StructValue struct, boolean forReading) {
        String layout = forReading
                ? render(struct.spec().declaration(), true)
                : Integer.toUnsignedString(struct.spec().declaration().hashCode());
        return "#(struct! " + layout + " "
                + render(BlockValue.block(struct.body()), forReading) + ")";
    }

    private static final int SIGNIFICANT_DIGITS = 15;

    private static final int SMALLEST_PLAIN_EXPONENT = -6;

    private static String renderDecimal(DecimalValue decimal) {
        double quantity = decimal.quantity();
        return decimal.datatype() == Datatype.PERCENT && hasDigits(quantity)
                ? renderPercent(quantity) + "%"
                : renderDouble(quantity);
    }

    private static final int A_PERCENT_MOVES_THE_POINT_THIS_FAR = 2;

    private static String renderPercent(double quantity) {
        int digits = WRITING_EVERYTHING_OUT.get()
                ? EVERY_DIGIT_A_DOUBLE_HAS
                : SIGNIFICANT_DIGITS;
        if (quantity == 0.0) {
            return 1 / quantity < 0 ? "-0" : "0";
        }
        BigDecimal rounded = new BigDecimal(quantity)
                .round(new MathContext(digits))
                .stripTrailingZeros()
                .movePointRight(A_PERCENT_MOVES_THE_POINT_THIS_FAR);
        return renderRounded(rounded, digits, MINIMAL);
    }

    private static boolean hasDigits(double quantity) {
        return !Double.isNaN(quantity) && !Double.isInfinite(quantity);
    }

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

    private static final boolean MINIMAL = true;

    private static final boolean KEEPS_ITS_POINT = false;

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

        return renderRounded(new BigDecimal(quantity)
                .round(new MathContext(digits))
                .stripTrailingZeros(), digits, minimal);
    }

    private static String renderRounded(
            BigDecimal rounded, int digits, boolean minimal) {

        int exponent = rounded.precision() - rounded.scale() - 1;
        return exponent < SMALLEST_PLAIN_EXPONENT || exponent > digits - 1
                ? withExponent(rounded, exponent, minimal)
                : pointAsWanted(rounded.toPlainString(), minimal);
    }

    private static String withExponent(
            BigDecimal rounded, int exponent, boolean minimal) {

        BigDecimal mantissa = rounded.movePointLeft(exponent).stripTrailingZeros();
        return pointAsWanted(mantissa.toPlainString(), minimal) + "e" + exponent;
    }

    private static String pointAsWanted(String rendered, boolean minimal) {
        return minimal
                ? trimTrailingZero(withThePointADecimalNeverLoses(rendered))
                : withThePointADecimalNeverLoses(rendered);
    }

    private static String withThePointADecimalNeverLoses(String rendered) {
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

    private static String renderMap(MapValue map, boolean forReading) {
        if (!forReading) {
            return formedPairsOf(map);
        }
        boolean asAConstruct = WRITING_EVERYTHING_OUT.get();
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

    private static String formedPairsOf(MapValue map) {
        List<Value> flat = map.flattened();
        StringBuilder written = new StringBuilder();
        for (int at = 0; at < flat.size(); at += 2) {
            if (at > 0) {
                written.append('\n');
            }
            written.append(render(flat.get(at), true))
                    .append(' ')
                    .append(render(flat.get(at + 1), true));
        }
        return written.toString();
    }

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

    private static final String LEXER_DELIMITERS = "()[]{}\"/;";

    private static final char OPENS_AN_EMAIL_INSTEAD = '@';

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

    private static final int LONGEST_QUOTED = 50;

    private static final String FILE_DELIMITERS = ";\"()[]{}<>\\^%:";

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

    private static String moldedText(String text) {
        String deciding = asFarAsTheLimitLooks(text);
        long newlines = deciding.chars().filter(each -> each == '\n').count();
        boolean quoted = deciding.indexOf('"') < 0
                && newlines < 3
                && deciding.codePointCount(0, deciding.length()) <= LONGEST_QUOTED;
        return quoted
                ? "\"" + escape(text) + "\""
                : "{" + escapeInBraces(text, !balancedBraces(text)) + "}";
    }

    private static String asFarAsTheLimitLooks(String text) {
        int limit = AS_MUCH_AS_WAS_ASKED_FOR.get();
        if (limit == NO_LIMIT) {
            return text;
        }
        int reaching = Math.max(0, limit - 1);
        return reaching >= text.codePointCount(0, text.length())
                ? text
                : text.substring(0, text.offsetByCodePoints(0, reaching));
    }

    private static String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        text.codePoints().forEach(
                codepoint -> escaped.append(escapedCodepoint(codepoint)));
        return escaped.toString();
    }

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

    private static String hexOf(byte[] octets) {
        StringBuilder hex = new StringBuilder();
        for (byte octet : octets) {
            hex.append("%02X".formatted(octet & 0xFF));
        }
        return hex.toString();
    }

    private static final int PIXELS_TO_A_LINE = 10;

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

    private static int asManyBytesAsTheLimitCouldUse(int bytes) {
        int limit = AS_MUCH_AS_WAS_ASKED_FOR.get();
        return limit == NO_LIMIT ? bytes : Math.min(bytes, Math.max(0, limit));
    }

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

    private static String moldedBytes(byte[] whole) {
        byte[] octets = Arrays.copyOf(whole,
                asManyBytesAsTheLimitCouldUse(whole.length));
        boolean mayBreakLines = !WRITING_ON_ONE_LINE.get();
        return switch (BINARY_BASE.get()) {
            case 2 -> "2#{" + base2DigitsDroppingTheLastAtExactlyEightBytes(
                    octets, mayBreakLines) + "}";
            case 64 -> "64#{" + base64Digits(octets, mayBreakLines) + "}";
            default -> "#{" + base16Digits(octets, mayBreakLines) + "}";
        };
    }

    private static final int BYTES_TO_A_HEX_LINE = 32;

    private static final int BYTES_TO_A_BINARY_LINE = 8;

    private static final int BYTES_TO_A_BASE_SIXTY_FOUR_LINE = 48;

    private static final int BYTES_TO_A_BASE_SIXTY_FOUR_LINE_BREAK = 64;

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

    private static String base2DigitsDroppingTheLastAtExactlyEightBytes(
            byte[] octets, boolean mayBreakLines) {
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

    private static final ThreadLocal<Integer> LINED_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    private static final String ONE_INDENT = "    ";

    private static String aLineIndentedAsDeepAsWeAre() {
        return "\n" + ONE_INDENT.repeat(LINED_DEPTH.get());
    }

    private static String oneLevelIn(Supplier<String> written) {
        LINED_DEPTH.set(LINED_DEPTH.get() + 1);
        try {
            return written.get();
        } finally {
            LINED_DEPTH.set(LINED_DEPTH.get() - 1);
        }
    }

    private static final int NUMBERS_TO_A_LINE = 10;

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

    private static boolean wouldNotReadBackAsAPath(BlockValue path, List<Value> segments) {
        return segments.isEmpty()
                || path.storageLength() <= 1
                || segments.getFirst().datatype() != Datatype.WORD;
    }

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

    private static String renderFunction(FunctionValue function, boolean forReading) {
        Datatype names = function.closure() ? Datatype.CLOSURE : Datatype.FUNCTION;
        return openedFor(names) + "["
                + mold(function.spec().head())
                + mold(function.body().head())
                + "]" + closedAfterATypeName();
    }

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

    private static final ThreadLocal<Set<Context>> BEING_RENDERED =
            ThreadLocal.withInitial(LinkedHashSet::new);

    private static String renderField(Value value, boolean forReading) {
        return value instanceof WordValue word && word.datatype() == Datatype.WORD
                && !WRITING_EVERYTHING_OUT.get()
                ? "'" + render(value, forReading)
                : render(value, forReading);
    }

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

    private static String openedFor(Datatype datatype) {
        return (WRITING_EVERYTHING_OUT.get() ? "#(" : "make ")
                + datatype.literalSpelling() + " ";
    }

    private static String closedAfterATypeName() {
        return WRITING_EVERYTHING_OUT.get() ? ")" : "";
    }

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

    private static String namesInTheTypeset(TypesetValue typeset) {
        return typeset.members().stream()
                .sorted()
                .map(Datatype::literalSpelling)
                .collect(Collectors.joining(" "));
    }

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

    private static String formedFields(ObjectValue object) {
        return fieldsOutsideSelf(object)
                .map(slot -> slot.spelling() + ": " + renderField(slot.value(), true))
                .collect(Collectors.joining("\n"));
    }

    private static Stream<ContextSlot> fieldsOutsideSelf(
            ObjectValue object) {
        return object.context().slots().stream()
                .filter(slot -> !slot.canonical()
                        .equals(THE_WORD_EVERY_OBJECT_HOLDS_FOR_ITSELF));
    }
}

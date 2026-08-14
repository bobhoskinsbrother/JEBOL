package org.jebol.domain.value;

import java.util.List;
import java.util.stream.Collectors;

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
        return mold(value).replaceAll("\\n\\s*", " ")
                .replace("[ ", "[").replace(" ]", "]");
    }

    /** Source text that reads back as an equal value. */
    public static String mold(Value value) {
        return render(value, true);
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
        if (value instanceof SeriesValue series && series.index() > 1) {
            return "#(" + value.datatype().literalSpelling() + " "
                    + constructBodyOf(series) + " " + series.index() + ")";
        }
        if (value instanceof StringValue tag && tag.datatype() == Datatype.TAG
                && tag.storageLength() == 0) {
            return "#(tag! " + moldedText("") + ")";
        }
        return render(value, true);
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
        return block.remaining().stream()
                .map(Molder::mold)
                .collect(Collectors.joining(" "));
    }

    private static String render(Value value, boolean forReading) {
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
            case DateValue date -> date.toString();
            case StringValue string -> renderString(string, forReading);
            case BinaryValue binary -> renderBinary(binary, forReading);
            case ImageValue image -> renderImage(image, forReading);
            case GobValue gob -> renderGob(gob, forReading);
            case BlockValue block -> renderBlock(block, forReading);
            case WordValue word -> forReading ? word.toString() : word.spelling();
            case DatatypeValue datatype -> forReading
                    ? "#(" + datatype.represents().literalSpelling() + ")"
                    : datatype.represents().literalSpelling();
            case TypesetValue typeset -> typeset.toString();
            case NativeValue native0 -> "#[native! " + native0.nativeName() + "]";
            case FunctionValue function -> "#[function! " + function.arity() + "]";
            case OperatorValue operator -> "#[op! " + operator.operatorName() + "]";
            case MapValue map -> renderMap(map, forReading);
            case BitsetValue bitset -> "#(bitset! "
                    + (bitset.isComplemented() ? "not " : "")
                    + "#{" + hexOf(bitset.octets()) + "})";
            case ObjectValue object -> renderObject(object, forReading);
            case PortValue port -> renderObject(
                    new ObjectValue(port.context()), forReading);
            case ModuleValue module -> renderObject(
                    new ObjectValue(module.context()), forReading);
            case ErrorValue error -> "#[error! " + error.errorId() + "]";
            case StructValue struct -> "make struct! "
                    + render(struct.layout(), forReading);
            case JavaObjectValue host -> "#[java-object! " + host.className() + "]";
        };
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
        if (decimal.datatype() == Datatype.PERCENT) {
            return trimTrailingZero(renderDouble(quantity * 100.0)) + "%";
        }
        return renderDouble(quantity);
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

    private static String renderDouble(double quantity) {
        return renderDouble(quantity, SIGNIFICANT_DIGITS, KEEPS_ITS_POINT);
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

        java.math.BigDecimal rounded = new java.math.BigDecimal(quantity)
                .round(new java.math.MathContext(digits))
                .stripTrailingZeros();
        int exponent = rounded.precision() - rounded.scale() - 1;

        return exponent < SMALLEST_PLAIN_EXPONENT || exponent > digits - 1
                ? withExponent(rounded, exponent, minimal)
                : pointAsWanted(rounded.toPlainString(), minimal);
    }

    /** {@code 1.0e15}: a mantissa that always has a point, and a bare e. */
    private static String withExponent(
            java.math.BigDecimal rounded, int exponent, boolean minimal) {

        java.math.BigDecimal mantissa = rounded.movePointLeft(exponent).stripTrailingZeros();
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

    /** {@code #[]} when empty, and one pair per line otherwise. */
    private static String renderMap(MapValue map, boolean forReading) {
        if (map.pairCount() == 0) {
            return "#[]";
        }
        StringBuilder rendered = new StringBuilder("#[\n");
        List<Value> flat = map.flattened();
        for (int at = 0; at < flat.size(); at += 2) {
            rendered.append("    ")
                    .append(render(flat.get(at), forReading))
                    .append(' ')
                    .append(render(flat.get(at + 1), forReading))
                    .append('\n');
        }
        return rendered.append(']').toString();
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
            case REF -> "@" + text;
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
     */
    private static String moldedFile(String text) {
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
     */
    private static String renderImage(ImageValue image, boolean forReading) {
        ImageValue shown = image;
        StringBuilder colours = new StringBuilder();
        StringBuilder alphas = new StringBuilder();
        for (int pixel = 1; pixel <= shown.lengthFromHere(); pixel++) {
            int[] channels = shown.pixelAt(pixel);
            colours.append("%02X%02X%02X".formatted(channels[0], channels[1], channels[2]));
            alphas.append("%02X".formatted(channels[3]));
        }
        String pixels = shown.storage().wide() + "x" + shown.storage().high()
                + " #{" + colours + "}"
                + (shown.storage().hasAlpha() ? " #{" + alphas + "}" : "");
        return "make image! [" + pixels + "]";
    }

    private static String renderBinary(BinaryValue binary, boolean forReading) {
        StringBuilder hex = new StringBuilder();
        for (int at = binary.index(); at <= binary.storageLength(); at++) {
            hex.append("%02X".formatted(binary.storage().at(at)));
        }
        return forReading ? "#{" + hex + "}" : hex.toString();
    }

    /** How deep the mold is inside line-broken blocks, for the indent. */
    private static final ThreadLocal<Integer> LINED_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    private static final String ONE_INDENT = "    ";

    private static boolean carriesLineBreaks(BlockValue block) {
        for (int at = block.index(); at <= block.storageLength(); at++) {
            if (block.storage().breaksLineAt(at)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A block whose positions carry line breaks molds one line per flagged
     * item, indented four spaces a level, with the closing bracket on its
     * own line -- {@code Mold_Block_Series} through
     * {@code New_Indented_Line}. NEW-LINE sets the flags and BODY-OF an
     * object carries them, which is how SAVE writes a header a person can
     * read.
     */
    private static String renderLined(BlockValue block, boolean forReading) {
        int outer = LINED_DEPTH.get();
        LINED_DEPTH.set(outer + 1);
        StringBuilder out = new StringBuilder("[");
        try {
            List<Value> items = block.remaining();
            for (int at = 0; at < items.size(); at++) {
                if (block.storage().breaksLineAt(block.index() + at)) {
                    out.append('\n').append(ONE_INDENT.repeat(outer + 1));
                } else if (at > 0) {
                    out.append(' ');
                }
                out.append(render(items.get(at), forReading));
            }
        } finally {
            LINED_DEPTH.set(outer);
        }
        out.append('\n').append(ONE_INDENT.repeat(outer)).append(']');
        return out.toString();
    }

    private static String renderBlock(BlockValue block, boolean forReading) {
        if (block.datatype() == Datatype.BLOCK && forReading
                && carriesLineBreaks(block)) {
            return renderLined(block, forReading);
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
        return prefix + path.remaining().stream()
                .map(Molder::mold)
                .collect(Collectors.joining("/")) + suffix;
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
        StringBuilder built = new StringBuilder("make event! [");
        List<Value> spec = event.moldingSpec();
        for (int at = 0; at < spec.size(); at++) {
            if (at > 0) {
                built.append(' ');
            }
            Value shown = spec.get(at);
            boolean quoted = shown instanceof WordValue word
                    && word.datatype() == Datatype.WORD;
            built.append(quoted ? "'" : "").append(render(shown, forReading));
        }
        return built.append(']').toString();
    }

    /**
     * A gob as the spec block that would remake it.
     *
     * <p>`Pre_Mold`, `Gob_To_Block`, `End_Mold` -- so a gob molds as
     * `make gob! [offset: 0x0 size: 100x100]` and reads back as a gob. Which
     * fields appear is not "the ones that were set": offset and size always, the
     * alpha only when the gob is see-through, and the one content field it has.
     */
    private static String renderGob(GobValue gob, boolean forReading) {
        StringBuilder built = new StringBuilder("make gob! [");
        List<Value> spec = gob.storage().moldingSpec();
        for (int at = 0; at < spec.size(); at++) {
            if (at > 0) {
                built.append(' ');
            }
            built.append(render(spec.get(at), forReading));
        }
        return built.append(']').toString();
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
    private static final ThreadLocal<java.util.Set<Context>> BEING_RENDERED =
            ThreadLocal.withInitial(java.util.LinkedHashSet::new);

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
     */
    private static String renderObject(ObjectValue object, boolean forReading) {
        java.util.Set<Context> enclosing = BEING_RENDERED.get();
        if (!enclosing.add(object.context())) {
            return "make object! [...]";
        }
        try {
            String fields = object.context().slots().stream()
                    .filter(slot -> !slot.canonical().equals(SELF))
                    .map(slot -> "    " + slot.spelling() + ": "
                            + renderField(slot.value(), forReading) + "\n")
                    .collect(Collectors.joining());
            return "make object! [\n" + fields + "]";
        } finally {
            enclosing.remove(object.context());
        }
    }
}

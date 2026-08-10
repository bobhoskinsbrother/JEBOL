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
            // The values with no literal spelling of their own. MOLD writes
            // them as construction syntax so they read back as themselves;
            // FORM writes the bare word, because FORM is for people. Molding
            // these as bare words is what JEBOL did, and it broke
            // round-tripping in the direction nobody checks: `true` read back
            // as a word bound to a function rather than as the logic.
            // NONE is the odd one, a single underscore rather than #(none),
            // though #(none) still reads.
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
            case TupleValue tuple -> tuple.toString();
            case TimeValue time -> time.toString();
            case DateValue date -> date.toString();
            case StringValue string -> renderString(string, forReading);
            case BinaryValue binary -> renderBinary(binary, forReading);
            case BlockValue block -> renderBlock(block, forReading);
            case WordValue word -> word.toString();
            case DatatypeValue datatype -> forReading
                    ? "#(" + datatype.represents().literalSpelling() + ")"
                    : datatype.represents().literalSpelling();
            case TypesetValue typeset -> typeset.toString();
            case NativeValue native0 -> "#[native! " + native0.nativeName() + "]";
            case FunctionValue function -> "#[function! " + function.arity() + "]";
            case OperatorValue operator -> "#[op! " + operator.operatorName() + "]";
            case MapValue map -> renderMap(map, forReading);
            // A complemented set prints the bits it names and the word
            // NOT before them, because that is what a caller wrote. The
            // flipped bits would print as a wall of FF and say nothing.
            case BitsetValue bitset -> "#(bitset! "
                    + (bitset.isComplemented() ? "not " : "")
                    + "#{" + hexOf(bitset.octets()) + "})";
            case ObjectValue object -> renderObject(object, forReading);
            case ErrorValue error -> "#[error! " + error.errorId() + "]";
            case JavaObjectValue host -> "#[java-object! " + host.className() + "]";
        };
    }

    /** How many significant digits a decimal is printed to. */
    private static final int SIGNIFICANT_DIGITS = 15;

    /** Outside these exponents, the exponent form is used. */
    private static final int SMALLEST_PLAIN_EXPONENT = -6;
    private static final int LARGEST_PLAIN_EXPONENT = 14;

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
     * A pair's half prints as a whole number when it is one, so 1x2 reads
     * back as 1x2 rather than as 1.0x2.0. This is the only place where a
     * decimal drops its point, and it is why the halves being decimals at
     * all is invisible until you take one out.
     */
    public static String moldHalf(double half) {
        if (half == Math.rint(half) && Math.abs(half) < 1e15) {
            return String.valueOf((long) half);
        }
        return renderDouble(half);
    }

    private static String renderDouble(double quantity) {
        if (Double.isNaN(quantity)) {
            return "1.#NaN";
        }
        if (Double.isInfinite(quantity)) {
            return quantity > 0 ? "1.#INF" : "-1.#INF";
        }
        if (quantity == 0.0) {
            return (1 / quantity < 0 ? "-" : "") + "0.0";
        }

        java.math.BigDecimal rounded = new java.math.BigDecimal(quantity)
                .round(new java.math.MathContext(SIGNIFICANT_DIGITS))
                .stripTrailingZeros();
        int exponent = rounded.precision() - rounded.scale() - 1;

        return exponent < SMALLEST_PLAIN_EXPONENT || exponent > LARGEST_PLAIN_EXPONENT
                ? withExponent(rounded, exponent)
                : withPoint(rounded.toPlainString());
    }

    /** {@code 1.0e15}: a mantissa that always has a point, and a bare e. */
    private static String withExponent(java.math.BigDecimal rounded, int exponent) {
        java.math.BigDecimal mantissa = rounded.movePointLeft(exponent).stripTrailingZeros();
        return withPoint(mantissa.toPlainString()) + "e" + exponent;
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
        // The sign goes before the currency, not after it: -$1, never $-1.
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
                    .append(": ")
                    .append(render(flat.get(at + 1), forReading))
                    .append('\n');
        }
        return rendered.append(']').toString();
    }

    /**
     * Escaped for the braced form, which spares the quotes and nothing
     * else: a caret is still doubled or the text would not read back.
     */
    private static String escapeInBraces(String text) {
        StringBuilder escaped = new StringBuilder();
        text.codePoints().forEach(codepoint -> {
            switch (codepoint) {
                case '^' -> escaped.append("^^");
                case '\n' -> escaped.append("^/");
                case '\t' -> escaped.append("^-");
                default -> escaped.appendCodePoint(codepoint);
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
            // A tag is the odd one in the family: its brackets are part of
            // its text, where a file's percent and a ref's at-sign are
            // punctuation the reader needs and a person does not. That is
            // what lets a tag be looked for inside a string and match the
            // brackets as written.
            return string.datatype() == Datatype.TAG ? "<" + text + ">" : text;
        }
        return switch (string.datatype()) {
            case FILE -> "%" + text;
            case URL, EMAIL -> text;
            case TAG -> "<" + text + ">";
            case REF -> "@" + text;
            // A string holding a quote molds with braces rather than
            // escaping it, which is what makes the molded form readable
            // and not merely re-readable.
            default -> text.indexOf('"') >= 0 && balancedBraces(text)
                    ? "{" + escapeInBraces(text) + "}"
                    : "\"" + escape(text) + "\"";
        };
    }

    private static String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        text.codePoints().forEach(codepoint -> {
            switch (codepoint) {
                case '"' -> escaped.append("^\"");
                case '^' -> escaped.append("^^");
                case '\n' -> escaped.append("^/");
                case '\t' -> escaped.append("^-");
                default -> escaped.append(escapedCodepoint(codepoint));
            }
        });
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
        // 30 is the one hole in the range. Sixty-four above it is the
        // caret itself, so ^^ would read back as a caret and the round
        // trip would lose the value. R3 writes the hex form for that one
        // and the letter for every other.
        if (codepoint < 0x20 && codepoint != 0x1E) {
            return "^" + (char) (codepoint + 64);
        }
        if (codepoint >= 0x20 && codepoint < 0x7F) {
            return String.valueOf((char) codepoint);
        }
        return "^(" + "%02X".formatted(codepoint) + ")";
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

    private static String renderBinary(BinaryValue binary, boolean forReading) {
        StringBuilder hex = new StringBuilder();
        for (int at = binary.index(); at <= binary.storageLength(); at++) {
            hex.append("%02X".formatted(binary.storage().at(at)));
        }
        return forReading ? "#{" + hex + "}" : hex.toString();
    }

    private static String renderBlock(BlockValue block, boolean forReading) {
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
            default -> "[" + items + "]";
        };
    }

    private static String joinPath(BlockValue path, String prefix, String suffix) {
        return prefix + path.remaining().stream()
                .map(Molder::mold)
                .collect(Collectors.joining("/")) + suffix;
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
            // One field per line, indented four, as a real R3 writes it.
            // The layout is part of what MOLD answers: a script that
            // compares molded text is comparing this too.
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

package org.jebol.domain.value;

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
            case UnsetValue ignored -> "#[unset!]";
            case NoneValue ignored -> "none";
            case LogicValue logic -> logic.truth() ? "true" : "false";
            case IntegerValue integer -> Long.toString(integer.magnitude());
            case DecimalValue decimal -> renderDecimal(decimal);
            case MoneyValue money -> renderMoney(money);
            case CharacterValue character -> forReading
                    ? "#\"" + escape(character.toString()) + "\""
                    : character.toString();
            case PairValue pair -> pair.x() + "x" + pair.y();
            case TupleValue tuple -> tuple.toString();
            case TimeValue time -> time.toString();
            case DateValue date -> date.toString();
            case StringValue string -> renderString(string, forReading);
            case BinaryValue binary -> renderBinary(binary);
            case BlockValue block -> renderBlock(block, forReading);
            case WordValue word -> word.toString();
            case DatatypeValue datatype -> datatype.represents().literalSpelling();
            case TypesetValue typeset -> typeset.represents().literalSpelling();
            case NativeValue native0 -> "#[native! " + native0.nativeName() + "]";
            case FunctionValue function -> "#[function! " + function.arity() + "]";
            case OperatorValue operator -> "#[op! " + operator.operatorName() + "]";
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
        return money.currency().orElse("$") + money.amount().toPlainString();
    }

    private static String renderString(StringValue string, boolean forReading) {
        String text = string.text();
        if (!forReading) {
            return text;
        }
        return switch (string.datatype()) {
            case FILE -> "%" + text;
            case URL, EMAIL -> text;
            case TAG -> "<" + text + ">";
            default -> "\"" + escape(text) + "\"";
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
                default -> {
                    if (codepoint < 0x20) {
                        escaped.append("^(").append(Integer.toHexString(codepoint)).append(')');
                    } else {
                        escaped.appendCodePoint(codepoint);
                    }
                }
            }
        });
        return escaped.toString();
    }

    private static String renderBinary(BinaryValue binary) {
        StringBuilder rendered = new StringBuilder("#{");
        for (int offset = 0; offset < binary.lengthFromHere(); offset++) {
            rendered.append(String.format("%02X", binary.storage().at(binary.index() + offset)));
        }
        return rendered.append('}').toString();
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
     * An object as the MAKE that would build it again.
     *
     * <p>{@code self} is left out. It refers to the object being molded, so
     * printing it would recurse for ever, and REBOL leaves it out for the
     * same reason. It is still a word inside the object; it is just not a
     * field worth writing down.
     */
    private static String renderObject(ObjectValue object, boolean forReading) {
        String fields = object.context().slots().stream()
                .filter(slot -> !slot.canonical().equals(SELF))
                .map(slot -> slot.spelling() + ": " + render(slot.value(), forReading))
                .collect(Collectors.joining(" "));
        return "make object! [" + fields + "]";
    }
}

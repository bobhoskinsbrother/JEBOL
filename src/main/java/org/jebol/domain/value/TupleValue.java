package org.jebol.domain.value;

import java.util.Arrays;
import java.util.List;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Up to twelve octets, written {@code 100.150.150}, with zeros behind them.
 *
 * <p>Used for colours, version numbers and addresses. The count of dots is
 * what separates a tuple from a decimal: one dot is a decimal, two or more is
 * a tuple, and the digits between them play no part.
 *
 * <p>Two lengths, and nearly everything about a tuple follows from the gap
 * between them. The kept length is how many octets were written down, and
 * a tuple made from a short binary or a short block may keep fewer than
 * three. The shown length is never below three, so a tuple of one octet
 * still reads and molds as three.
 *
 * <p>The octets past the kept length are zeros rather than absent, which
 * is what makes {@code 1.2.3} equal to {@code 1.2.3.0}. The two are equal
 * and are not the same tuple, because only the length tells them apart.
 * That is the whole of the difference between {@code =} and {@code ==}
 * here, and it is why the length is kept rather than being padded away on
 * the way in.
 *
 * <p>This is {@code REBTUP} in {@code sys-value.h}: a length byte and
 * twelve octets. {@code Emit_Tuple} in {@code t-tuple.c} is the padding to
 * three, and {@code Cmp_Tuple} beside it is the comparison over the zeros.
 */
public record TupleValue(int[] segments) implements Value, PathTarget {

    /** What a tuple shows however few octets it keeps. */
    public static final int MINIMUM_SHOWN_SEGMENTS = 3;

    public static final int MAXIMUM_SEGMENTS = 12;

    private static final int HEX_DIGITS_A_SEGMENT = 2;

    @Override
    public String writtenInHex(HexWidth width) {
        StringBuilder hex = new StringBuilder();
        for (int segment : segments) {
            hex.append("%02X".formatted(segment));
        }
        for (int padded = segments.length; padded < MINIMUM_SHOWN_SEGMENTS; padded++) {
            hex.append("00");
        }
        return width.keptFromTheLeft(hex.toString(), HEX_DIGITS_A_SEGMENT * segments.length);
    }

    public TupleValue {
        if (segments == null) {
            throw new IllegalArgumentException("a tuple must have segments");
        }
        if (segments.length > MAXIMUM_SEGMENTS) {
            throw new IllegalArgumentException(
                    "a tuple keeps at most " + MAXIMUM_SEGMENTS
                            + " segments, got " + segments.length);
        }
        for (int segment : segments) {
            if (segment < 0 || segment > 255) {
                throw new IllegalArgumentException(
                        "a tuple segment is an octet, got " + segment);
            }
        }
        segments = segments.clone();
    }

    public static TupleValue of(int... segments) {
        return new TupleValue(segments);
    }

    @Override
    public Value arithmetic(Value right, ArithmeticOperation operation) {
        return new TupleActions().combinedWith(this, right, operation);
    }

    @Override
    public Value heldBetween(Value lowest, Value highest) {
        TupleValue floor = (TupleValue) lowest;
        TupleValue ceiling = (TupleValue) highest;
        int[] held = new int[segments.length];
        for (int at = 0; at < held.length; at++) {
            held[at] = Math.max(floor.octetAt(at + 1),
                    Math.min(ceiling.octetAt(at + 1), segments[at]));
        }
        return TupleValue.of(held);
    }

    @Override
    public Value partWayTo(Value destination, double fraction) {
        if (!(destination instanceof TupleValue reached)) {
            throw Raised.of(EvaluationFailure.TYPE_MISMATCH, Molder.mold(destination));
        }
        int width = Math.max(segmentCount(), reached.segmentCount());
        int[] octets = new int[width];
        for (int at = 1; at <= width; at++) {
            int from = octetAt(at);
            octets[at - 1] = (int) (from + (reached.octetAt(at) - from) * fraction);
        }
        return TupleValue.of(octets);
    }


    @Override
    public Value bitwise(Value right, BitwiseOperation operation) {
        return switch (right) {
            case TupleValue theirs -> octetByOctetAgainst(theirs, operation);
            case IntegerValue(long magnitude) -> everyOctetAgainst(magnitude, operation);
            default -> throw Raised.notRelated(this, right);
        };
    }

    private Value everyOctetAgainst(long magnitude, BitwiseOperation operation) {
        int[] combined = new int[segments.length];
        for (int at = 0; at < combined.length; at++) {
            combined[at] = clampedToAnOctet(
                    operation.onWholeElements(segments[at], magnitude));
        }
        return TupleValue.of(combined);
    }

    private Value octetByOctetAgainst(TupleValue theirs, BitwiseOperation operation) {
        int[] yours = theirs.segments;
        int[] combined = new int[Math.max(segments.length, yours.length)];
        for (int at = 0; at < combined.length; at++) {
            long mine = at < segments.length ? segments[at] : 0;
            long theirsHere = at < yours.length ? yours[at] : 0;
            combined[at] = clampedToAnOctet(
                    operation.onWholeElements(mine, theirsHere));
        }
        return TupleValue.of(combined);
    }

    private int clampedToAnOctet(long combined) {
        return (int) Math.clamp(combined, 0L, 255L);
    }

    /** How many octets were written down, which may be fewer than three. */
    public int segmentCount() {
        return segments.length;
    }

    /** How many octets a tuple shows, which is never fewer than three. */
    public int shownCount() {
        return Math.max(segments.length, MINIMUM_SHOWN_SEGMENTS);
    }

    public TupleValue reversedFront(int howMany) {
        if (howMany < 0) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, Integer.toString(howMany));
        }
        int width = Math.min(howMany, segmentCount());
        int[] octets = segments();
        for (int at = 0; at < width / 2; at++) {
            int held = octets[at];
            octets[at] = octets[width - at - 1];
            octets[width - at - 1] = held;
        }
        return TupleValue.of(octets);
    }

    @Override
    public Value picked(int oneBasedPosition) {
        return oneBasedPosition < 1 || oneBasedPosition > shownCount()
                ? NoneValue.none()
                : IntegerValue.of(octetAt(oneBasedPosition));
    }

    @Override
    public Value steppedIntoBy(Value selector) {
        if (!(selector instanceof IntegerValue(long at))) {
            throw Raised.of(EvaluationFailure.INVALID_PATH,
                    "cannot select " + selector.datatype().literalSpelling()
                            + " from " + datatype().literalSpelling());
        }
        return at < 1 || at > shownCount() ? NoneValue.none() : picked((int) at);
    }

    @Override
    public void writeThrough(Slot place, Value selector, Value written) {
        if (!(selector instanceof IntegerValue(long magnitude))) {
            throw Raised.of(EvaluationFailure.INVALID_PATH, Molder.mold(selector));
        }
        place.setValue(withOctetWritten((int) magnitude, written));
    }

    private TupleValue withOctetWritten(int position, Value written) {
        if (position < 1 || position > MAXIMUM_SEGMENTS) {
            throw Raised.of(EvaluationFailure.INVALID_PATH, Integer.toString(position));
        }
        if (written instanceof NoneValue) {
            return TupleValue.of(Arrays.copyOf(segments, position - 1));
        }
        long amount = switch (written) {
            case IntegerValue(long magnitude) -> magnitude;
            case AnyDecimalValue quantity -> (long) quantity.quantity();
            default -> throw Raised.of(EvaluationFailure.BAD_PATH_SET);
        };
        int[] octets = octetsToTwelve();
        octets[position - 1] = (int) Math.max(0, Math.min(TupleDatatype.THE_LARGEST_OCTET, amount));
        int kept = position > shownCount() ? position : segmentCount();
        return TupleValue.of(Arrays.copyOf(octets, kept));
    }

    /** An octet by position, counting from one, and zero past the kept ones. */
    public int octetAt(int oneBasedPosition) {
        return oneBasedPosition >= 1 && oneBasedPosition <= segments.length
                ? segments[oneBasedPosition - 1]
                : 0;
    }

    /** The octets a comparison sees: what was written, then zeros. */
    public int[] octetsToTwelve() {
        int[] all = new int[MAXIMUM_SEGMENTS];
        System.arraycopy(segments, 0, all, 0, segments.length);
        return all;
    }

    @Override
    public int[] segments() {
        return segments.clone();
    }

    private static final int THE_BITS_OF_AN_OCTET = 0xFF;

    @Override
    public Value randomised(RandomDraw draw) {
        int[] octets = segments();
        for (int at = 0; at < octets.length; at++) {
            if (octets[at] != 0) {
                octets[at] = draw.belowWithoutNarrowing(octets[at] + 1) & THE_BITS_OF_AN_OCTET;
            }
        }
        return TupleValue.of(octets);
    }

    @Override
    public long asRandomSeed(ToLongFunction<byte[]> checksumOfTheOctets) {
        return checksumOfTheOctets.applyAsLong(shownOctets());
    }

    private byte[] shownOctets() {
        byte[] octets = new byte[shownCount()];
        for (int at = 0; at < octets.length; at++) {
            octets[at] = (byte) octetAt(at + 1);
        }
        return octets;
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    public static final Datatype TYPE = new TupleDatatype();

    private static final class TupleDatatype extends ScalarDatatype {

        private static final int THE_LARGEST_OCTET = 255;

        TupleDatatype() {
            super("tuple");
        }

        @Override
        protected Value built(Conversion asking, Value from, Maker maker) {
            return switch (from) {
                case TupleValue already -> already;
                case AnyStringValue text -> tupleScannedFrom(text.text(), from);
                case AnyBlockValue segments -> tupleOfSegments(segments);
                case BinaryValue octets -> tupleOfOctets(octets);
                case IssueValue issue -> tupleOfHexPairs(issue.spelling(), from);
                default -> throw refusing(from);
            };
        }

        private Value tupleOfSegments(AnyBlockValue segments) {
            List<Value> items = segments.remaining();
            if (items.size() > MAXIMUM_SEGMENTS) {
                throw refusing(segments);
            }
            int[] octets = new int[items.size()];
            for (int at = 0; at < items.size(); at++) {
                octets[at] = octetOf(items.get(at), segments);
            }
            return TupleValue.of(octets);
        }

        private int octetOf(Value item, Value whole) {
            long number = switch (item) {
                case IntegerValue wholeNumber -> wholeNumber.magnitude();
                case CharacterValue letter -> letter.codepoint();
                case AnyDecimalValue fractional -> Math.round(Math.abs(fractional.quantity()))
                        * (fractional.quantity() < 0 ? -1 : 1);
                default -> throw refusing(whole);
            };
            if (number < 0 || number > THE_LARGEST_OCTET) {
                throw refusing(whole);
            }
            return (int) number;
        }

        private Value tupleOfOctets(BinaryValue octets) {
            int width = Math.min(octets.lengthFromHere(), MAXIMUM_SEGMENTS);
            int[] kept = new int[width];
            for (int at = 0; at < width; at++) {
                kept[at] = octets.storage().at(octets.index() + at) & THE_BITS_OF_AN_OCTET;
            }
            return TupleValue.of(kept);
        }

        private Value tupleOfHexPairs(String digits, Value original) {
            if (digits.length() % 2 != 0 || digits.length() / 2 > MAXIMUM_SEGMENTS) {
                throw refusing(original);
            }
            int[] octets = new int[digits.length() / 2];
            for (int at = 0; at < octets.length; at++) {
                try {
                    octets[at] = Integer.parseInt(digits.substring(at * 2, at * 2 + 2), 16);
                } catch (NumberFormatException notHexadecimal) {
                    throw refusing(original);
                }
            }
            return TupleValue.of(octets);
        }

        private Value tupleScannedFrom(String text, Value original) {
            String[] parts = text.split("\\.", -1);
            if (text.isEmpty() || parts.length > MAXIMUM_SEGMENTS) {
                throw refusing(original);
            }
            int width = Math.max(parts.length, MINIMUM_SHOWN_SEGMENTS);
            int[] octets = new int[width];
            for (int at = 0; at < parts.length; at++) {
                if (parts[at].isEmpty() && at == parts.length - 1) {
                    break;
                }
                int written;
                try {
                    written = Integer.parseInt(parts[at].trim());
                } catch (NumberFormatException notANumber) {
                    throw refusing(original);
                }
                if (written < 0 || written > THE_LARGEST_OCTET) {
                    throw refusing(original);
                }
                octets[at] = written;
            }
            return TupleValue.of(octets);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TupleValue tuple
                && Arrays.equals(octetsToTwelve(), tuple.octetsToTwelve());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(octetsToTwelve());
    }

    @Override
    public String toString() {
        return IntStream.rangeClosed(1, shownCount())
                .mapToObj(position -> Integer.toString(octetAt(position)))
                .collect(Collectors.joining("."));
    }
}

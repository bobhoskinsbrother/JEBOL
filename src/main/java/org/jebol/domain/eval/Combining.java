package org.jebol.domain.eval;

import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Bringing two values together bit by bit or member by member: AND~, OR~
 * and XOR~ on one side, INTERSECT, UNION, EXCLUDE and DIFFERENCE on the
 * other.
 *
 * <p>Both are decided by the pair of operands rather than by either one
 * alone, and both try their pairings in a fixed order. {@link BitKind} and
 * {@link SetKind} name those pairings once each, in that order, and the
 * first that claims a pair owns it.
 */
public final class Combining {

    /** What AND~, OR~ and XOR~ ask for. */
    public enum Bitwise { AND, OR, XOR }

    /** What INTERSECT, UNION, EXCLUDE and DIFFERENCE ask for. */
    public enum Sets { INTERSECT, UNION, EXCLUDE, DIFFERENCE }

    private Combining() {
    }

    /** AND~, OR~ or XOR~ over whichever pairing the two operands make. */
    public static Value bitwise(Value left, Value right, Bitwise operation) {
        return theBitKindThatClaims(left, right).combine(left, right, operation);
    }

    /** INTERSECT, UNION, EXCLUDE or DIFFERENCE, comparing loosely. */
    public static Value sets(Value first, Value second, Sets how) {
        return sets(first, second, how, false, 1);
    }

    /**
     * INTERSECT, UNION, EXCLUDE or DIFFERENCE, where /CASE says to tell
     * two spellings apart and /SKIP says how many items make one record.
     */
    public static Value sets(
            Value first, Value second, Sets how, boolean mindingCase, int stride) {

        TwoSets asked = new TwoSets(first, second, how, mindingCase, stride);
        return theSetKindThatClaims(first, second).combine(asked);
    }

    private static BitKind theBitKindThatClaims(Value left, Value right) {
        return Arrays.stream(BitKind.values())
                .filter(kind -> kind.claims(left, right))
                .findFirst()
                .orElse(BitKind.WHOLE_NUMBERS);
    }

    private static SetKind theSetKindThatClaims(Value first, Value second) {
        return Arrays.stream(SetKind.values())
                .filter(kind -> kind.claims(first, second))
                .findFirst()
                .orElseThrow(() -> Raised.cannotUse(
                        first instanceof BlockValue ? second : first, "a set operation"));
    }

    /**
     * The pairings AND~, OR~ and XOR~ know, in the order they are tried.
     * WHOLE_NUMBERS is what a pair falls through to when nothing above has
     * claimed it, which is where anything that is not a number is refused.
     */
    private enum BitKind {

        VECTORS {
            @Override
            boolean claims(Value left, Value right) {
                return VectorMath.isVectorArithmetic(left, right);
            }

            @Override
            Value combine(Value left, Value right, Bitwise operation) {
                if (!(left instanceof VectorValue)) {
                    throw Arithmetic.notRelated(left, right);
                }
                return VectorMath.done(left, right, switch (operation) {
                    case AND -> VectorMath.Operation.AND;
                    case OR -> VectorMath.Operation.OR;
                    case XOR -> VectorMath.Operation.XOR;
                });
            }
        },

        TRUTHS {
            @Override
            boolean claims(Value left, Value right) {
                return left instanceof LogicValue && right instanceof LogicValue;
            }

            @Override
            Value combine(Value left, Value right, Bitwise operation) {
                boolean ours = left.isTruthy();
                boolean theirs = right.isTruthy();
                return LogicValue.of(switch (operation) {
                    case AND -> ours && theirs;
                    case OR -> ours || theirs;
                    case XOR -> ours ^ theirs;
                });
            }
        },

        POINTS {
            @Override
            boolean claims(Value left, Value right) {
                return left instanceof PairValue;
            }

            @Override
            Value combine(Value left, Value right, Bitwise operation) {
                PairValue point = (PairValue) left;
                return PairValue.of(
                        bitsOf(point.x(), Arithmetic.firstHalfOf(right), operation),
                        bitsOf(point.y(), Arithmetic.secondHalfOf(right), operation));
            }

            private long bitsOf(double ours, double theirs, Bitwise operation) {
                return combinedBits(
                        roundedHalfUp(ours), roundedHalfUp(theirs), operation);
            }
        },

        TUPLES {
            @Override
            boolean claims(Value left, Value right) {
                return left instanceof TupleValue;
            }

            @Override
            Value combine(Value left, Value right, Bitwise operation) {
                return Arithmetic.octetByOctet(left, right,
                        (octet, against, fractional) ->
                                combinedBits(octet, (long) against, operation));
            }
        },

        OCTETS {
            @Override
            boolean claims(Value left, Value right) {
                return left instanceof BinaryValue && right instanceof BinaryValue;
            }

            @Override
            Value combine(Value left, Value right, Bitwise operation) {
                return octetsCycledAgainstTheLonger(
                        (BinaryValue) left, (BinaryValue) right, operation);
            }
        },

        WHOLE_NUMBERS {
            @Override
            boolean claims(Value left, Value right) {
                return false;
            }

            @Override
            Value combine(Value left, Value right, Bitwise operation) {
                return IntegerValue.of(combinedBits(
                        wholeNumberOf(left), wholeNumberOf(right), operation));
            }
        };

        abstract boolean claims(Value left, Value right);

        abstract Value combine(Value left, Value right, Bitwise operation);
    }

    private static Value octetsCycledAgainstTheLonger(
            BinaryValue left, BinaryValue right, Bitwise operation) {

        BinaryValue longer = left.lengthFromHere() >= right.lengthFromHere() ? left : right;
        BinaryValue shorter = longer == left ? right : left;
        int cycle = shorter.lengthFromHere();
        int[] combined = new int[longer.lengthFromHere()];
        for (int at = 0; at < combined.length; at++) {
            int theirs = cycle == 0 ? 0 : shorter.storage().at(shorter.index() + at % cycle);
            combined[at] = (int) combinedBits(
                    longer.storage().at(longer.index() + at), theirs, operation) & 0xFF;
        }
        return BinaryValue.of(combined);
    }

    static long combinedBits(long left, long right, Bitwise operation) {
        return switch (operation) {
            case AND -> left & right;
            case OR -> left | right;
            case XOR -> left ^ right;
        };
    }

    private static long roundedHalfUp(double half) {
        return (long) Math.floor(half + 0.5);
    }

    private static long wholeNumberOf(Value value) {
        if (value instanceof IntegerValue whole) {
            return whole.magnitude();
        }
        throw Raised.of(EvaluationFailure.EXPECT_ARG,
                "and takes a whole number, not " + value.datatype().literalSpelling());
    }

    /**
     * The two operands of one set operation and how it was asked for, which
     * is what every pairing below needs and what carries the walk they share:
     * keep the records of the first set the combination wants, then let the
     * second set contribute the ones it is entitled to.
     */
    private record TwoSets(
            Value first, Value second, Sets how, boolean mindingCase, int stride) {

        List<Value> keptFrom(List<Value> ourMembers, List<Value> theirMembers) {
            List<List<Value>> ours = inRecords(ourMembers);
            List<List<Value>> theirs = inRecords(theirMembers);
            List<List<Value>> kept = new ArrayList<>();
            for (List<Value> candidate : ours) {
                if (theFirstSetKeeps(theirs, candidate) && isNew(kept, candidate)) {
                    kept.add(candidate);
                }
            }
            if (theSecondSetContributesAsWell()) {
                for (List<Value> candidate : theirs) {
                    if (theSecondSetKeeps(ours, candidate) && isNew(kept, candidate)) {
                        kept.add(candidate);
                    }
                }
            }
            return kept.stream().flatMap(List::stream).toList();
        }

        private boolean theFirstSetKeeps(List<List<Value>> theirs, List<Value> candidate) {
            boolean inTheirs = holds(theirs, candidate);
            return switch (how) {
                case INTERSECT -> inTheirs;
                case UNION -> true;
                case EXCLUDE, DIFFERENCE -> !inTheirs;
            };
        }

        private boolean theSecondSetKeeps(List<List<Value>> ours, List<Value> candidate) {
            return how == Sets.UNION || !holds(ours, candidate);
        }

        private boolean theSecondSetContributesAsWell() {
            return how == Sets.UNION || how == Sets.DIFFERENCE;
        }

        private List<List<Value>> inRecords(List<Value> items) {
            List<List<Value>> records = new ArrayList<>();
            for (int at = 0; at < items.size(); at += stride) {
                records.add(items.subList(at, Math.min(at + stride, items.size())));
            }
            return records;
        }

        private boolean isNew(List<List<Value>> kept, List<Value> candidate) {
            return !holds(kept, candidate);
        }

        private boolean holds(List<List<Value>> records, List<Value> candidate) {
            return records.stream().anyMatch(each -> sameRecord(each, candidate));
        }

        private boolean sameRecord(List<Value> ours, List<Value> theirs) {
            if (ours.isEmpty() || theirs.isEmpty()) {
                return ours.isEmpty() && theirs.isEmpty();
            }
            return mindingCase
                    ? Comparison.identicallyEqual(ours.getFirst(), theirs.getFirst())
                    : Comparison.looselyEqual(ours.getFirst(), theirs.getFirst());
        }
    }

    /**
     * The pairings a set operation knows, in the order they are tried. A
     * pair that reaches the end of the list is refused rather than guessed
     * at, which is why there is no fallback here.
     */
    private enum SetKind {

        BITSETS {
            @Override
            boolean claims(Value first, Value second) {
                return first instanceof BitsetValue && second instanceof BitsetValue;
            }

            @Override
            Value combine(TwoSets asked) {
                return bitsetsOctetByOctet((BitsetValue) asked.first(),
                        (BitsetValue) asked.second(), asked.how());
            }
        },

        TYPESETS {
            @Override
            boolean claims(Value first, Value second) {
                return first instanceof TypesetValue && second instanceof TypesetValue;
            }

            @Override
            Value combine(TwoSets asked) {
                return typesetsDatatypeByDatatype((TypesetValue) asked.first(),
                        (TypesetValue) asked.second(), asked.how());
            }
        },

        TEXT {
            @Override
            boolean claims(Value first, Value second) {
                return first instanceof StringValue || second instanceof StringValue;
            }

            @Override
            Value combine(TwoSets asked) {
                List<Value> kept = asked.keptFrom(
                        charactersOf(asked.first()), charactersOf(asked.second()));
                StringBuilder written = new StringBuilder();
                kept.forEach(letter -> written.appendCodePoint(
                        ((CharacterValue) letter).codepoint()));
                Datatype datatype = asked.first() instanceof StringValue text
                        ? text.datatype()
                        : Datatype.STRING;
                return StringValue.of(written.toString(), datatype);
            }
        },

        MAPS {
            @Override
            boolean claims(Value first, Value second) {
                return first instanceof MapValue || second instanceof MapValue;
            }

            @Override
            Value combine(TwoSets asked) {
                return mapsKeyByKey(asked);
            }
        },

        BLOCKS {
            @Override
            boolean claims(Value first, Value second) {
                return first instanceof BlockValue && second instanceof BlockValue;
            }

            @Override
            Value combine(TwoSets asked) {
                return BlockValue.block(asked.keptFrom(
                        ((BlockValue) asked.first()).remaining(),
                        ((BlockValue) asked.second()).remaining()));
            }
        };

        abstract boolean claims(Value first, Value second);

        abstract Value combine(TwoSets asked);
    }

    private static List<Value> charactersOf(Value value) {
        if (!(value instanceof StringValue text)) {
            return value instanceof BlockValue block ? block.remaining() : List.of(value);
        }
        return text.text().codePoints()
                .<Value>mapToObj(CharacterValue::of)
                .toList();
    }

    private static BitsetValue bitsetsOctetByOctet(
            BitsetValue ours, BitsetValue theirs, Sets how) {

        byte[] left = ours.octets();
        byte[] right = theirs.octets();
        byte[] both = new byte[Math.max(left.length, right.length)];
        for (int at = 0; at < both.length; at++) {
            int mine = at < left.length ? left[at] & 0xFF : 0;
            int yours = at < right.length ? right[at] & 0xFF : 0;
            both[at] = (byte) switch (how) {
                case UNION -> mine | yours;
                case INTERSECT -> mine & yours;
                case EXCLUDE -> mine & ~yours;
                case DIFFERENCE -> mine ^ yours;
            };
        }
        return BitsetValue.of(both);
    }

    private static TypesetValue typesetsDatatypeByDatatype(
            TypesetValue ours, TypesetValue theirs, Sets how) {

        Set<Datatype> mine = ours.members();
        Set<Datatype> yours = theirs.members();
        Set<Datatype> result = EnumSet.noneOf(Datatype.class);
        for (Datatype each : Datatype.values()) {
            boolean inMine = mine.contains(each);
            boolean inYours = yours.contains(each);
            boolean kept = switch (how) {
                case UNION -> inMine || inYours;
                case INTERSECT -> inMine && inYours;
                case DIFFERENCE -> inMine ^ inYours;
                case EXCLUDE -> inMine && !inYours;
            };
            if (kept) {
                result.add(each);
            }
        }
        return TypesetValue.of(Set.copyOf(result));
    }

    private static MapValue mapsKeyByKey(TwoSets asked) {
        boolean mindingCase = asked.mindingCase();
        Sets how = asked.how();
        MapValue ours = asked.first() instanceof MapValue map ? map : MapValue.empty();
        MapValue theirs = asked.second() instanceof MapValue map ? map : MapValue.empty();
        MapValue kept = MapValue.empty();
        for (Value key : ours.keys()) {
            boolean inTheirs = theirs.holds(key, mindingCase);
            boolean wanted = switch (how) {
                case INTERSECT -> inTheirs;
                case UNION -> true;
                case EXCLUDE, DIFFERENCE -> !inTheirs;
            };
            if (wanted && !kept.holds(key, mindingCase)) {
                kept.put(key, ours.select(key, mindingCase), mindingCase);
            }
        }
        if (how == Sets.UNION || how == Sets.DIFFERENCE) {
            for (Value key : theirs.keys()) {
                boolean inOurs = ours.holds(key, mindingCase);
                if ((how == Sets.UNION || !inOurs) && !kept.holds(key, mindingCase)) {
                    kept.put(key, theirs.select(key, mindingCase), mindingCase);
                }
            }
        }
        return kept;
    }
}

package org.jebol.domain.eval;

import org.jebol.domain.eval.arithmetic.BitwiseOperation;
import org.jebol.domain.eval.bit.bitType.BitType;
import org.jebol.domain.eval.bit.bitType.Bitsets;
import org.jebol.domain.eval.bit.bitType.Characters;
import org.jebol.domain.eval.bit.bitType.Datatypes;
import org.jebol.domain.eval.bit.bitType.Octets;
import org.jebol.domain.eval.bit.bitType.Points;
import org.jebol.domain.eval.bit.bitType.Truths;
import org.jebol.domain.eval.bit.bitType.Tuples;
import org.jebol.domain.eval.bit.bitType.Typesets;
import org.jebol.domain.eval.bit.bitType.Vectors;
import org.jebol.domain.eval.bit.bitType.WholeNumbers;
import org.jebol.domain.eval.sets.SetOperation;
import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Combining {

    private Combining() {
    }

    public static Value bitwise(Value left, Value right, BitwiseOperation operation) {
        return theBitTypeThatHandles(left, right).combine(left, right, operation);
    }

    public static Value sets(Value first, Value second, SetOperation how) {
        return sets(first, second, how, false, 1);
    }

    public static Value sets(
            Value first, Value second, SetOperation how, boolean mindingCase, int stride) {

        TwoSets asked = new TwoSets(first, second, how, mindingCase, stride);
        return theSetKindThatClaims(first, second).combine(asked);
    }

    private static BitType theBitTypeThatHandles(Value left, Value right) {
        return bitTypes().stream()
                .filter(kind -> kind.shouldHandle(left, right))
                .findFirst()
                .orElseThrow(() -> Raised.of(EvaluationFailure.EXPECT_ARG, left));
    }

    private static SetKind theSetKindThatClaims(Value first, Value second) {
        return Arrays.stream(SetKind.values())
                .filter(kind -> kind.claims(first, second))
                .findFirst()
                .orElseThrow(() -> Raised.cannotUse(
                        first instanceof BlockValue ? second : first, "a set operation"));
    }

    private static List<BitType> bitTypes() {

        return List.of(
                new WholeNumbers(),
                new Characters(),
                new Truths(),
                new Points(),
                new Tuples(),
                new Octets(),
                new Bitsets(),
                new Typesets(),
                new Datatypes(),
                new Vectors()
        );
    }

    private record TwoSets(
            Value first, Value second, SetOperation how, boolean mindingCase, int stride) {

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
            return how.theFirstSetKeeps(holds(theirs, candidate));
        }

        private boolean theSecondSetKeeps(List<List<Value>> ours, List<Value> candidate) {
            return how.theSecondSetKeeps(holds(ours, candidate));
        }

        private boolean theSecondSetContributesAsWell() {
            return how.theSecondSetContributes();
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
                return new TypesetActions((TypesetValue) asked.first())
                        .combinedWith((TypesetValue) asked.second(), asked.how());
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
                MapValue ours = asked.first() instanceof MapValue map
                        ? map
                        : MapValue.empty();
                return new MapActions(ours).combinedWith(
                        asked.second(), asked.how(), asked.mindingCase());
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
            BitsetValue ours, BitsetValue theirs, SetOperation how) {

        byte[] left = ours.octets();
        byte[] right = theirs.octets();
        byte[] both = new byte[Math.max(left.length, right.length)];
        for (int at = 0; at < both.length; at++) {
            int mine = at < left.length ? left[at] & 0xFF : 0;
            int yours = at < right.length ? right[at] & 0xFF : 0;
            both[at] = (byte) how.combinedBits(mine, yours);
        }
        return BitsetValue.of(both);
    }

}

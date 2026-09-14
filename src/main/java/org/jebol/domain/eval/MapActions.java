package org.jebol.domain.eval;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.ArrayList;
import java.util.List;

/**
 * What a map does when an action is performed on it, which is what
 * {@code REBTYPE(Map)} answers in {@code t-map.c}.
 *
 * <p>A map is keyed rather than positional, and most of what it refuses comes
 * from that. APPEND takes a block of pairs and nothing else, because there is
 * no position for a lone value to go into. /DUP means nothing, because adding
 * a key twice over leaves one key. A walk over a map takes a key and a value,
 * so a third name is one more than a pair has.
 */
public final class MapActions implements Actions {

    private final MapValue pairs;

    public MapActions(MapValue pairs) {
        this.pairs = pairs;
    }

    @Override
    public Value append(Asked asked) {
        return givenTheBlockOfPairs(asked, "append");
    }

    @Override
    public Value insert(Asked asked) {
        return givenTheBlockOfPairs(asked, "insert");
    }

    /**
     * APPEND and INSERT, which mean the same thing to a map: there is no
     * position for a pair to go into, so both put the keys in.
     */
    Value givenTheBlockOfPairs(Asked asked, String nativeName) {
        Natives.requireChangeable(pairs);
        refuseWhatIsNotAWholeBlockOfPairs(
                asked.given(), asked.refinementsAsked().contains("dup"), nativeName);
        return given(theWantedPairsOf((BlockValue) asked.given(), asked));
    }

    /**
     * INTERSECT, UNION, EXCLUDE and DIFFERENCE, walked key by key.
     *
     * <p>A key already kept is left as it was rather than written again, so
     * the value that survives a union is the one the first map held.
     */
    public MapValue combinedWith(Value other, Combining.Sets how, boolean mindingCase) {
        MapValue theirs = other instanceof MapValue map ? map : MapValue.empty();
        MapValue kept = MapValue.empty();
        for (Value key : pairs.keys()) {
            boolean inTheirs = theirs.holds(key, mindingCase);
            boolean wanted = switch (how) {
                case INTERSECT -> inTheirs;
                case UNION -> true;
                case EXCLUDE, DIFFERENCE -> !inTheirs;
            };
            if (wanted && !kept.holds(key, mindingCase)) {
                kept.put(key, pairs.select(key, mindingCase), mindingCase);
            }
        }
        if (how == Combining.Sets.UNION || how == Combining.Sets.DIFFERENCE) {
            for (Value key : theirs.keys()) {
                boolean inOurs = pairs.holds(key, mindingCase);
                if ((how == Combining.Sets.UNION || !inOurs)
                        && !kept.holds(key, mindingCase)) {
                    kept.put(key, theirs.select(key, mindingCase), mindingCase);
                }
            }
        }
        return kept;
    }

    /**
     * The pairs {@code /part} leaves of a block, always an even number.
     *
     * <p>A negative count reaches backwards from where the block is held,
     * and an odd count is rounded down, because half a pair is not one.
     */
    static List<Value> theWantedPairsOf(BlockValue block, Asked asked) {
        List<Value> whole = block.head().remaining();
        int here = block.index() - 1;
        long asking = asked.howMuchOfIt().orElse((long) (whole.size() - here));
        int from = here;
        int count;
        if (asking >= 0) {
            count = (int) Math.min(asking, whole.size() - here);
        } else {
            count = (int) Math.min(-asking, here);
            from = here - count;
        }
        count -= count % 2;
        return whole.subList(from, from + count);
    }

    /** APPEND and INSERT, which a map only takes as whole pairs. */
    public Value given(List<Value> wanted) {
        for (int at = 0; at + 1 < wanted.size(); at += 2) {
            pairs.put(wanted.get(at), wanted.get(at + 1));
        }
        return pairs;
    }

    /**
     * What APPEND and INSERT refuse before any pair is read: anything that is
     * not a block, and /DUP whatever the block holds.
     */
    public static void refuseWhatIsNotAWholeBlockOfPairs(
            Value given, boolean duplicating, String nativeName) {

        if (!(given instanceof BlockValue block) || block.datatype() != Datatype.BLOCK) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    nativeName + " puts pairs into a map, and needs a block of them, "
                            + "not a " + given.datatype().literalSpelling());
        }
        if (duplicating) {
            throw Raised.of(EvaluationFailure.BAD_REFINES,
                    nativeName + "/dup means nothing for a map, where adding a key "
                            + "twice over leaves one key");
        }
    }

    /** MAKE and TO, from another map, an object, a block, or room for so many. */
    public static Value madeFrom(Value given, List<Value> flattened) {
        if (flattened.size() % 2 != 0) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "a map needs a value for every key, and this has "
                            + flattened.size() + " items");
        }
        return MapValue.of(flattened);
    }

    /**
     * The pairs a value offers a map, or nothing when it offers none.
     *
     * <p>An object hands over its fields in order, without the self it answers
     * to, because a map has no such notion and would otherwise gain a key
     * pointing at the object it came from.
     */
    public static List<Value> pairsOffered(Value given) {
        return switch (given) {
            case MapValue already -> already.flattened();
            case ObjectValue object -> object.context().slots().stream()
                    .filter(slot -> !slot.canonical().equals("self"))
                    .<Value>mapMulti((slot, accept) -> {
                        accept.accept(WordValue.of(slot.spelling()));
                        accept.accept(slot.value());
                    })
                    .toList();
            case BlockValue block when block.datatype() == Datatype.BLOCK
                    || block.datatype() == Datatype.PAREN -> block.remaining();
            default -> null;
        };
    }

    /** How much room MAKE was asked for, which a map answers by ignoring. */
    public static void refuseRoomForFewerThanNoPairs(Value given) {
        if (Comparison.asDouble(given) < 0) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    "a map cannot have room for " + Molder.form(given) + " pairs");
        }
    }

    /**
     * COMPOSE, which reaches into the values and leaves the keys alone: a
     * paren in a value position is evaluated, and going deep follows nested
     * blocks and nested maps.
     */
    public MapValue composedThrough(java.util.function.UnaryOperator<Value> compose) {
        List<Value> built = new ArrayList<>();
        for (Value key : pairs.keys()) {
            built.add(key);
            built.add(compose.apply(pairs.select(key)));
        }
        return MapValue.of(built);
    }

    /** A walk over a map takes a key and a value, and a third name is one too many. */
    public static void refuseMoreNamesThanAPairHas(Value series, List<WordValue> names) {
        if (names.size() > 2
                && (series instanceof MapValue || series instanceof ObjectValue)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "a walk over " + series.datatype().literalSpelling()
                            + " takes a key and a value, and " + names.size()
                            + " names is one more than a pair has");
        }
    }
}

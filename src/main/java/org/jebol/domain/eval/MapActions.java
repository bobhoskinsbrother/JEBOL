package org.jebol.domain.eval;

import org.jebol.domain.eval.sets.SetOperation;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.ArrayList;
import java.util.List;

public final class MapActions implements Actions {

    private final MapValue pairs;

    public MapActions(MapValue pairs) {
        this.pairs = pairs;
    }

    @Override
    public Value subject() {
        return pairs;
    }

    @Override
    public Value cleared() {
        Natives.requireChangeable(pairs);
        pairs.clear();
        return pairs;
    }

    @Override
    public int length() {
        return pairs.pairCount();
    }

    @Override
    public Value append(Asked asked) {
        return givenTheBlockOfPairs(asked, "append");
    }

    @Override
    public Value insert(Asked asked) {
        return givenTheBlockOfPairs(asked, "insert");
    }

    Value givenTheBlockOfPairs(Asked asked, String nativeName) {
        Natives.requireChangeable(pairs);
        refuseWhatIsNotAWholeBlockOfPairs(
                asked.given(), asked.refinementsAsked().contains("dup"), nativeName);
        return given(theWantedPairsOf((BlockValue) asked.given(), asked));
    }

    public MapValue combinedWith(Value other, SetOperation how, boolean mindingCase) {
        MapValue theirs = other instanceof MapValue map ? map : MapValue.empty();
        MapValue kept = MapValue.empty();
        for (Value key : pairs.keys()) {
            boolean inTheirs = theirs.holds(key, mindingCase);
            if (how.theFirstSetKeeps(inTheirs) && !kept.holds(key, mindingCase)) {
                kept.put(key, pairs.select(key, mindingCase), mindingCase);
            }
        }
        if (how.theSecondSetContributes()) {
            for (Value key : theirs.keys()) {
                boolean inOurs = pairs.holds(key, mindingCase);
                if (how.theSecondSetKeeps(inOurs)
                        && !kept.holds(key, mindingCase)) {
                    kept.put(key, theirs.select(key, mindingCase), mindingCase);
                }
            }
        }
        return kept;
    }

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

    public Value given(List<Value> wanted) {
        for (int at = 0; at + 1 < wanted.size(); at += 2) {
            pairs.put(wanted.get(at), wanted.get(at + 1));
        }
        return pairs;
    }

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

    public static Value madeFrom(Value given, List<Value> flattened) {
        if (flattened.size() % 2 != 0) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "a map needs a value for every key, and this has "
                            + flattened.size() + " items");
        }
        return MapValue.of(flattened);
    }

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

    public static void refuseRoomForFewerThanNoPairs(Value given) {
        if (Comparison.asDouble(given) < 0) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    "a map cannot have room for " + Molder.form(given) + " pairs");
        }
    }

    public MapValue composedThrough(java.util.function.UnaryOperator<Value> compose) {
        List<Value> built = new ArrayList<>();
        for (Value key : pairs.keys()) {
            built.add(key);
            built.add(compose.apply(pairs.select(key)));
        }
        return MapValue.of(built);
    }

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

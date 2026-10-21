package org.jebol.domain.eval;

import org.jebol.domain.value.*;
import org.jebol.domain.value.sets.SetOperation;

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
    public Value poke(Value key, Value written) {
        pairs.put(key, written, false);
        return written;
    }

    @Override
    public Value clear() {
        pairs.requireChangeable();
        pairs.clear();
        return pairs;
    }

    @Override
    public int measureLength() {
        return pairs.pairCount();
    }

    @Override
    public Value append(Asked asked) {
        return addPairsFrom(asked, "append");
    }

    @Override
    public Value insert(Asked asked) {
        return addPairsFrom(asked, "insert");
    }

    Value addPairsFrom(Asked asked, String nativeName) {
        pairs.requireChangeable();
        refuseWhatIsNotAWholeBlockOfPairs(
                asked.given(), asked.refinementsAsked().contains("dup"), nativeName);
        return takeWanted(pickTheWantedPairsOf((AnyBlockValue) asked.given(), asked));
    }

    public MapValue combineWith(Value other, SetOperation how, boolean mindingCase) {
        MapValue theirs = other instanceof MapValue map ? map : MapValue.empty();
        MapValue kept = MapValue.empty();
        for (Value key : pairs.keys()) {
            boolean inTheirs = theirs.holds(key, mindingCase);
            if (how.keepsFromFirstSet(inTheirs) && !kept.holds(key, mindingCase)) {
                kept.put(key, pairs.select(key, mindingCase), mindingCase);
            }
        }
        if (how.takesFromSecondSet()) {
            for (Value key : theirs.keys()) {
                boolean inOurs = pairs.holds(key, mindingCase);
                if (how.keepsFromSecondSet(inOurs)
                        && !kept.holds(key, mindingCase)) {
                    kept.put(key, theirs.select(key, mindingCase), mindingCase);
                }
            }
        }
        return kept;
    }

    static List<Value> pickTheWantedPairsOf(AnyBlockValue block, Asked asked) {
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

    public Value takeWanted(List<Value> wanted) {
        for (int at = 0; at + 1 < wanted.size(); at += 2) {
            pairs.put(wanted.get(at), wanted.get(at + 1));
        }
        return pairs;
    }

    public static void refuseWhatIsNotAWholeBlockOfPairs(
            Value given, boolean duplicating, String nativeName) {

        if (!(given instanceof BlockValue block)) {
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

    public static Value makeFrom(Value given, List<Value> flattened) {
        if (flattened.size() % 2 != 0) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "a map needs a value for every key, and this has "
                            + flattened.size() + " items");
        }
        return MapValue.of(flattened);
    }

    public static List<Value> listThePairsOffered(Value given) {
        return switch (given) {
            case MapValue already -> already.flatten();
            case ObjectValue object -> object.context().slots().stream()
                    .<Value>mapMulti((slot, accept) -> {
                        accept.accept(WordValue.of(slot.spelling()));
                        accept.accept(slot.value());
                    })
                    .toList();
            case BlockValue block -> block.remaining();
            case ParenValue paren -> paren.remaining();
            default -> null;
        };
    }

    public MapValue composeThrough(java.util.function.UnaryOperator<Value> compose) {
        List<Value> built = new ArrayList<>();
        for (Value key : pairs.keys()) {
            built.add(key);
            built.add(compose.apply(pairs.select(key)));
        }
        return MapValue.of(built);
    }

    public static void refuseMoreNamesThanPairHas(Value series, List<AnyWordValue> names) {
        if (names.size() > 2
                && (series instanceof MapValue || series instanceof ObjectValue)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "a walk over " + series.datatype().literalSpelling()
                            + " takes a key and a value, and " + names.size()
                            + " names is one more than a pair has");
        }
    }
}

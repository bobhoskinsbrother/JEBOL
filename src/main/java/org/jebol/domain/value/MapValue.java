package org.jebol.domain.value;

import org.jebol.domain.eval.MapActions;
import org.jebol.domain.value.sets.MembersKept;
import org.jebol.domain.value.sets.SetOperation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MapValue implements Value, PathTarget {

    private final Map<Value, Value> entries;
    private final KeysAlikeApartFromCase keysAlikeApartFromCase;
    private boolean protectedFromChange;

    private MapValue(Map<Value, Value> entries) {
        this.entries = entries;
        this.keysAlikeApartFromCase = new KeysAlikeApartFromCase(entries.keySet());
    }

    private static final boolean MINDING_CASE = true;

    @Override
    public boolean isEqualTo(Value other, Sameness how) {
        if (!(other instanceof MapValue theirs) || pairCount() != theirs.pairCount()) {
            return false;
        }
        for (Value key : keys()) {
            if (!theirs.holds(key, MINDING_CASE)) {
                return false;
            }
            Value ours = select(key, MINDING_CASE);
            if (!ours.isEqualTo(theirs.select(key, MINDING_CASE), Sameness.insideSeries())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean atTail() {
        return pairCount() == 0;
    }

    @Override
    public Value pickBy(Value selector) {
        return select(selector);
    }

    @Override
    public Value asSetWith(Value other, MembersKept keeping, boolean mindingCase) {
        MapValue theirs = other instanceof MapValue map ? map : MapValue.empty();
        return combineWith(theirs, keeping.how(), mindingCase);
    }

    MapValue combineWith(MapValue theirs, SetOperation how, boolean mindingCase) {
        MapValue kept = MapValue.empty();
        for (Value key : keys()) {
            if (how.keepsFromFirstSet(theirs.holds(key, mindingCase))
                    && !kept.holds(key, mindingCase)) {
                kept.put(key, select(key, mindingCase), mindingCase);
            }
        }
        if (how.takesFromSecondSet()) {
            for (Value key : theirs.keys()) {
                if (how.keepsFromSecondSet(holds(key, mindingCase))
                        && !kept.holds(key, mindingCase)) {
                    kept.put(key, theirs.select(key, mindingCase), mindingCase);
                }
            }
        }
        return kept;
    }

    public static MapValue empty() {
        return new MapValue(new LinkedHashMap<>());
    }

    public static MapValue of(List<Value> pairs) {
        if (pairs.size() % 2 != 0) {
            throw new IllegalArgumentException(
                    "a map needs a value for every key, and got " + pairs.size() + " items");
        }
        Map<Value, Value> built = new LinkedHashMap<>();
        for (int at = 0; at < pairs.size(); at += 2) {
            built.put(copyAndLockText(
                    storeAnyWordAsSetWord(pairs.get(at))), pairs.get(at + 1));
        }
        return new MapValue(built);
    }

    private static Value storeAnyWordAsSetWord(Value written) {
        return written instanceof AnyWordValue word && !(word instanceof SetWordValue)
                ? word.asSetWord()
                : written;
    }

    private static Value copyAndLockText(Value key) {
        if (!(key instanceof AnyStringValue text)) {
            return key;
        }
        AnyStringValue own = text.wrap(text.text());
        own.storage().protectFromChange(true);
        return own;
    }

    private static Value handBackKeyAsWord(Value stored) {
        return stored instanceof SetWordValue word
                ? word.asWord()
                : stored;
    }

    private Value findFirstStoredKeyMatching(
            Value asked, boolean mindingCase) {
        Value wanted = storeAnyWordAsSetWord(asked);
        if (mindingCase) {
            return entries.containsKey(wanted) ? wanted : NoneValue.none();
        }
        return keysAlikeApartFromCase.findFirstStoredLike(wanted);
    }

    public Value select(Value key) {
        return select(key, false);
    }

    public Value select(Value key, boolean mindingCase) {
        Value found = findFirstStoredKeyMatching(key, mindingCase);
        return found instanceof NoneValue
                ? NoneValue.none()
                : entries.getOrDefault(found, NoneValue.none());
    }

    public boolean holds(Value key) {
        return holds(key, false);
    }

    public boolean holds(Value key, boolean mindingCase) {
        return !(findFirstStoredKeyMatching(key, mindingCase)
                instanceof NoneValue);
    }

    public Value findStoredKeyLike(Value asked, boolean mindingCase) {
        return findFirstStoredKeyMatching(asked, mindingCase);
    }

    public void put(Value key, Value value) {
        put(key, value, false);
    }

    @Override
    public Value stepIntoBy(Value selector) {
        return select(selector);
    }

    @Override
    public Slot placeSteppedIntoBy(Value selector) {
        return new MapSlot(this, selector, select(selector));
    }

    @Override
    public void writeThrough(Slot place, Value selector, Value written) {
        if (isProtected()) {
            throw Raised.of(EvaluationFailure.PROTECTED);
        }
        putUnlessKeyIsNone(selector, written);
    }

    void putUnlessKeyIsNone(Value key, Value written) {
        if (key instanceof NoneValue) {
            return;
        }
        put(key, written);
    }

    public void put(Value key, Value value, boolean mindingCase) {
        refuseChangeIfProtected();
        putWhetherProtectedOrNot(key, value, mindingCase);
    }

    public void putWhetherProtectedOrNot(Value key, Value value, boolean mindingCase) {
        Value existing = findFirstStoredKeyMatching(key, mindingCase);
        if (existing instanceof NoneValue) {
            Value added = copyAndLockText(storeAnyWordAsSetWord(key));
            entries.put(added, value);
            keysAlikeApartFromCase.add(added);
        } else {
            entries.put(existing, value);
        }
    }

    public void clear() {
        refuseChangeIfProtected();
        entries.clear();
        keysAlikeApartFromCase.clear();
    }

    public void remove(Value key) {
        refuseChangeIfProtected();
        Value stored = storeAnyWordAsSetWord(key);
        if (entries.containsKey(stored)) {
            entries.remove(stored);
            keysAlikeApartFromCase.remove(stored);
        }
    }

    public int pairCount() {
        return entries.size();
    }

    public List<Value> keys() {
        return entries.keySet().stream()
                .map(MapValue::handBackKeyAsWord).toList();
    }

    public List<Value> values() {
        return List.copyOf(entries.values());
    }

    public List<Value> flatten() {
        List<Value> flat = new ArrayList<>();
        entries.forEach((key, value) -> {
            flat.add(key);
            flat.add(value);
        });
        return List.copyOf(flat);
    }

    public AnyBlockValue layPairsOnLines() {
        AnyBlockValue block = BlockValue.of(flatten());
        block.putEachPairOnLine();
        return block;
    }

    @Override
    public Value reflect(AnyWordValue field) {
        return switch (field.canonical()) {
            case "words" -> BlockValue.of(keys());
            case "values" -> BlockValue.of(values());
            case "body" -> layPairsOnLines();
            default -> NoneValue.none();
        };
    }

    @Override
    public List<Value> listItems() {
        List<Value> flat = new ArrayList<>();
        entries.forEach((key, value) -> {
            flat.add(handBackKeyAsWord(key));
            flat.add(value);
        });
        return List.copyOf(flat);
    }

    @Override
    public boolean isProtected() {
        return protectedFromChange;
    }

    public void protectFromChange(boolean refusing) {
        protectedFromChange = refusing;
    }

    @Override
    public Value copy(boolean deeply, Set<Datatype> kinds) {
        List<Value> flattened = flatten();
        List<Value> copiedPairs = new ArrayList<>(flattened.size());
        for (int at = 0; at < flattened.size(); at++) {
            boolean isValueRatherThanKey = at % 2 == 1;
            copiedPairs.add(isValueRatherThanKey
                    ? flattened.get(at).copyAsMember(deeply, kinds)
                    : flattened.get(at));
        }
        return MapValue.of(copiedPairs);
    }

    public MapValue copy() {
        return new MapValue(new LinkedHashMap<>(entries));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MapValue map && entries.equals(map.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    public static final Datatype TYPE = new MapDatatype();

    private static final class MapDatatype extends Datatype {

        private static final int BYTES_SLOT_TAKES = 32;

        MapDatatype() {
            super("map");
        }

        @Override
        public Value makeFrom(Value spec, Maker maker) {
            return switch (spec) {
                case IntegerValue(long magnitude) -> withRoomFor(spec, magnitude);
                case DecimalValue number -> withRoomFor(spec, number.quantity());
                default -> makeFromPairsIn(spec);
            };
        }

        @Override
        protected Value build(Conversion asking, Value from, Maker maker) {
            if (from instanceof IntegerValue || from instanceof DecimalValue) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, from);
            }
            return makeFromPairsIn(from);
        }

        private Value withRoomFor(Value spec, double asked) {
            if (asked < 0) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE, spec);
            }
            refuseMoreRoomThanFits(asked, BYTES_SLOT_TAKES);
            return MapValue.empty();
        }

        private Value makeFromPairsIn(Value source) {
            List<Value> pairs = MapActions.listPairsOffered(source);
            if (pairs == null) {
                throw refuse(source);
            }
            return MapActions.makeFrom(source, pairs);
        }
    }

    @Override
    public String toString() {
        return "map of " + entries.size() + " pairs";
    }
}

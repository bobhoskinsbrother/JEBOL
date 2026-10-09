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
    private boolean protectedFromChange;

    private MapValue(Map<Value, Value> entries) {
        this.entries = entries;
    }

    private static final boolean MINDING_CASE = true;

    @Override
    public boolean equalTo(Value other, Sameness how) {
        if (!(other instanceof MapValue theirs) || pairCount() != theirs.pairCount()) {
            return false;
        }
        for (Value key : keys()) {
            if (!theirs.holds(key, MINDING_CASE)) {
                return false;
            }
            Value ours = select(key, MINDING_CASE);
            if (!ours.equalTo(theirs.select(key, MINDING_CASE), Sameness.insideASeries())) {
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
    public Value pickedBy(Value selector) {
        return select(selector);
    }

    @Override
    public Value asASetWith(Value other, MembersKept keeping, boolean mindingCase) {
        MapValue theirs = other instanceof MapValue map ? map : MapValue.empty();
        return combinedWith(theirs, keeping.how(), mindingCase);
    }

    MapValue combinedWith(MapValue theirs, SetOperation how, boolean mindingCase) {
        MapValue kept = MapValue.empty();
        for (Value key : keys()) {
            if (how.theFirstSetKeeps(theirs.holds(key, mindingCase))
                    && !kept.holds(key, mindingCase)) {
                kept.put(key, select(key, mindingCase), mindingCase);
            }
        }
        if (how.theSecondSetContributes()) {
            for (Value key : theirs.keys()) {
                if (how.theSecondSetKeeps(holds(key, mindingCase))
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
            built.put(copiedAndLockedIfItIsText(
                    anyWordStoredAsTheSetWordItNames(pairs.get(at))), pairs.get(at + 1));
        }
        return new MapValue(built);
    }

    private static Value anyWordStoredAsTheSetWordItNames(Value written) {
        return written instanceof AnyWordValue word && !(word instanceof SetWordValue)
                ? word.asSetWord()
                : written;
    }

    private static Value copiedAndLockedIfItIsText(Value key) {
        if (!(key instanceof AnyStringValue text)) {
            return key;
        }
        AnyStringValue own = text.holding(text.text());
        own.storage().protectFromChange(true);
        return own;
    }

    private static Value keyHandedBackAsAWordNotASetWord(Value stored) {
        return stored instanceof SetWordValue word
                ? word.asWord()
                : stored;
    }

    private Value theFirstStoredKeyMatchingNotTheExactOne(
            Value asked, boolean mindingCase) {
        Value wanted = anyWordStoredAsTheSetWordItNames(asked);
        if (mindingCase) {
            return entries.containsKey(wanted) ? wanted : NoneValue.none();
        }
        return entries.keySet().stream()
                .filter(held -> held.equals(wanted) || alikeApartFromCase(held, wanted))
                .findFirst()
                .orElseGet(NoneValue::none);
    }

    private static boolean alikeApartFromCase(Value held, Value wanted) {
        if (held instanceof AnyStringValue one && wanted instanceof AnyStringValue other) {
            return one.datatype() == other.datatype()
                    && one.text().equalsIgnoreCase(other.text());
        }
        if (held instanceof AnyWordValue one && wanted instanceof AnyWordValue other) {
            return one.datatype() == other.datatype()
                    && one.canonical().equals(other.canonical());
        }
        if (held instanceof CharacterValue(int codepoint1) && wanted instanceof CharacterValue(int codepoint)) {
            return Character.toLowerCase(codepoint1)
                    == Character.toLowerCase(codepoint);
        }
        return false;
    }

    public Value select(Value key) {
        return select(key, false);
    }

    public Value select(Value key, boolean mindingCase) {
        Value found = theFirstStoredKeyMatchingNotTheExactOne(key, mindingCase);
        return found instanceof NoneValue
                ? NoneValue.none()
                : entries.getOrDefault(found, NoneValue.none());
    }

    public boolean holds(Value key) {
        return holds(key, false);
    }

    public boolean holds(Value key, boolean mindingCase) {
        return !(theFirstStoredKeyMatchingNotTheExactOne(key, mindingCase)
                instanceof NoneValue);
    }

    public Value storedKeyLike(Value asked, boolean mindingCase) {
        return theFirstStoredKeyMatchingNotTheExactOne(asked, mindingCase);
    }

    public void put(Value key, Value value) {
        put(key, value, false);
    }

    @Override
    public Value steppedIntoBy(Value selector) {
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
        putUnlessTheKeyIsNone(selector, written);
    }

    void putUnlessTheKeyIsNone(Value key, Value written) {
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
        Value existing = theFirstStoredKeyMatchingNotTheExactOne(key, mindingCase);
        entries.put(existing instanceof NoneValue
                ? copiedAndLockedIfItIsText(anyWordStoredAsTheSetWordItNames(key))
                : existing, value);
    }

    public void clear() {
        refuseChangeIfProtected();
        entries.clear();
    }

    public void remove(Value key) {
        refuseChangeIfProtected();
        entries.remove(anyWordStoredAsTheSetWordItNames(key));
    }

    public int pairCount() {
        return entries.size();
    }

    public List<Value> keys() {
        return entries.keySet().stream()
                .map(MapValue::keyHandedBackAsAWordNotASetWord).toList();
    }

    public List<Value> values() {
        return List.copyOf(entries.values());
    }

    public List<Value> flattened() {
        List<Value> flat = new ArrayList<>();
        entries.forEach((key, value) -> {
            flat.add(key);
            flat.add(value);
        });
        return List.copyOf(flat);
    }

    public AnyBlockValue pairsOnLines() {
        AnyBlockValue block = BlockValue.block(flattened());
        block.putEachPairOnALine();
        return block;
    }

    @Override
    public Value reflected(AnyWordValue field) {
        return switch (field.canonical()) {
            case "words" -> BlockValue.block(keys());
            case "values" -> BlockValue.block(values());
            case "body" -> pairsOnLines();
            default -> NoneValue.none();
        };
    }

    @Override
    public List<Value> items() {
        List<Value> flat = new ArrayList<>();
        entries.forEach((key, value) -> {
            flat.add(keyHandedBackAsAWordNotASetWord(key));
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
    public Value copied(boolean deeply, Set<Datatype> kinds) {
        List<Value> flattened = flattened();
        List<Value> copiedPairs = new ArrayList<>(flattened.size());
        for (int at = 0; at < flattened.size(); at++) {
            boolean isAValueRatherThanAKey = at % 2 == 1;
            copiedPairs.add(isAValueRatherThanAKey
                    ? flattened.get(at).copiedAsAMember(deeply, kinds)
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

        private static final int BYTES_A_SLOT_TAKES = 32;

        MapDatatype() {
            super("map");
        }

        @Override
        public Value madeFrom(Value spec, Maker maker) {
            return switch (spec) {
                case IntegerValue(long magnitude) -> withRoomFor(spec, magnitude);
                case DecimalValue number -> withRoomFor(spec, number.quantity());
                default -> madeOfPairsIn(spec);
            };
        }

        @Override
        protected Value built(Conversion asking, Value from, Maker maker) {
            if (from instanceof IntegerValue || from instanceof DecimalValue) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, from);
            }
            return madeOfPairsIn(from);
        }

        private Value withRoomFor(Value spec, double asked) {
            if (asked < 0) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                        "a map cannot have room for " + Molder.form(spec) + " pairs");
            }
            refuseMoreRoomThanFits(asked, BYTES_A_SLOT_TAKES);
            return MapValue.empty();
        }

        private Value madeOfPairsIn(Value source) {
            List<Value> pairs = MapActions.pairsOffered(source);
            if (pairs == null) {
                throw refusing(source);
            }
            return MapActions.madeFrom(source, pairs);
        }
    }

    @Override
    public String toString() {
        return "map of " + entries.size() + " pairs";
    }
}

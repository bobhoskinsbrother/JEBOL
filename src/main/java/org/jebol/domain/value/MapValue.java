package org.jebol.domain.value;

import org.jebol.domain.value.sets.MembersKept;
import org.jebol.domain.value.sets.SetOperation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MapValue implements Value {

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
        return written instanceof WordValue word && word.datatype() != Datatype.SET_WORD
                ? word.as(Datatype.SET_WORD)
                : written;
    }

    private static Value copiedAndLockedIfItIsText(Value key) {
        if (!(key instanceof StringValue text)) {
            return key;
        }
        StringValue own = StringValue.of(text.text(), text.datatype());
        own.storage().protectFromChange(true);
        return own;
    }

    private static Value keyHandedBackAsAWordNotASetWord(Value stored) {
        return stored instanceof WordValue word && word.datatype() == Datatype.SET_WORD
                ? word.as(Datatype.WORD)
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
        if (held instanceof StringValue one && wanted instanceof StringValue other) {
            return one.datatype() == other.datatype()
                    && one.text().equalsIgnoreCase(other.text());
        }
        if (held instanceof WordValue one && wanted instanceof WordValue other) {
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

    public BlockValue pairsOnLines() {
        BlockValue block = BlockValue.block(flattened());
        block.putEachPairOnALine();
        return block;
    }

    @Override
    public Value reflected(WordValue field) {
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
        return Datatype.MAP;
    }

    @Override
    public String toString() {
        return "map of " + entries.size() + " pairs";
    }
}

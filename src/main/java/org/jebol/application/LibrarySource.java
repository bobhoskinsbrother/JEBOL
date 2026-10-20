package org.jebol.application;

import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class LibrarySource {

    private LibrarySource() {
    }

    private static final Map<String, AnyBlockValue> READ_VALUES = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> CANNOT_BE_COPIED = new ConcurrentHashMap<>();

    static TranscodeResult read(String name, String source) {
        if (CANNOT_BE_COPIED.containsKey(name)) {
            return Transcoder.transcode(source);
        }
        AnyBlockValue current = READ_VALUES.get(name);
        if (current != null) {
            return new TranscodeResult.Success((AnyBlockValue) newCopy(current));
        }
        TranscodeResult read = Transcoder.transcode(source);
        if (read.values().isEmpty()) {
            return read;
        }
        AnyBlockValue values = read.values().orElseThrow();
        if (cannotCopySeries(values)) {
            CANNOT_BE_COPIED.put(name, true);
            return read;
        }
        READ_VALUES.put(name, values);
        return new TranscodeResult.Success((AnyBlockValue) newCopy(values));
    }

    private static Value newCopy(Value value) {
        return switch (value) {
            case AnyBlockValue block -> copyBlock(block);
            case AnyStringValue text -> text.wrap(text.text());
            case BinaryValue octets -> new BinaryValue(new BinaryStorage(octets.octetsFromHere()), 1);
            case BitsetValue set -> copyBitset(set);
            case MapValue map -> copyMap(map);
            default -> scalar(value);
        };
    }

    private static Value copyBitset(BitsetValue set) {
        BitsetValue copy = BitsetValue.of(set.octets());
        copy = set.isComplemented() ? copy.complement() : copy;
        copy.protectFromChange(set.isProtected());
        return copy;
    }

    private static Value copyMap(MapValue map) {
        List<Value> flattened = map.flatten();
        List<Value> members = new ArrayList<>(flattened.size());
        for (Value member : flattened) {
            members.add(newCopy(member));
        }
        MapValue copy = MapValue.of(members);
        copy.protectFromChange(map.isProtected());
        return copy;
    }

    private static Value scalar(Value value) {
        return value;
    }

    private static AnyBlockValue copyBlock(AnyBlockValue block) {
        List<Value> items = new ArrayList<>(block.lengthFromHere());
        for (Value item : block.remaining()) {
            items.add(newCopy(item));
        }
        BlockStorage storage = new BlockStorage(items);
        storage.takeLineBreaksFrom(block.storage(), block.index());
        return block.wrap(storage);
    }

    private static boolean cannotCopySeries(Value value) {
        return switch (value) {
            case AnyBlockValue block -> cannotCopyMembers(block.remaining());
            case MapValue map -> cannotCopyMembers(map.flatten());
            case BitsetValue _, AnyStringValue _, BinaryValue _, IntegerValue _, AnyDecimalValue _, MoneyValue _,
                 CharacterValue _, LogicValue _, NoneValue _, UnsetValue _, DateValue _, TimeValue _, PairValue _,
                 TupleValue _, AnyWordValue _, Datatype _ -> false;
            default -> true;
        };
    }

    private static boolean cannotCopyMembers(List<Value> members) {
        for (Value member : members) {
            if (cannotCopySeries(member)) { return true; }
        }
        return false;
    }
}

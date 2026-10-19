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

    private static final Map<String, AnyBlockValue> READINGS = new ConcurrentHashMap<>();

    private static final Map<String, Boolean> NAMES_HOLDING_SOMETHING_UNCOPYABLE =
            new ConcurrentHashMap<>();

    static TranscodeResult read(String name, String source) {
        if (NAMES_HOLDING_SOMETHING_UNCOPYABLE.containsKey(name)) {
            return Transcoder.transcode(source);
        }
        AnyBlockValue held = READINGS.get(name);
        if (held != null) {
            return new TranscodeResult.Success((AnyBlockValue) makeAFreshCopy(held));
        }
        TranscodeResult read = Transcoder.transcode(source);
        if (read.values().isEmpty()) {
            return read;
        }
        AnyBlockValue values = read.values().orElseThrow();
        if (!canCopyEverySeries(values)) {
            NAMES_HOLDING_SOMETHING_UNCOPYABLE.put(name, true);
            return read;
        }
        READINGS.put(name, values);
        return new TranscodeResult.Success((AnyBlockValue) makeAFreshCopy(values));
    }

    private static Value makeAFreshCopy(Value value) {
        return switch (value) {
            case AnyBlockValue block -> copyTheBlock(block);
            case AnyStringValue text -> text.wrap(text.text());
            case BinaryValue octets -> new BinaryValue(
                    new BinaryStorage(octets.octetsFromHere()), 1);
            case BitsetValue set -> copyTheBitset(set);
            case MapValue map -> copyTheMap(map);
            default -> shareAScalar(value);
        };
    }

    private static Value copyTheBitset(BitsetValue set) {
        BitsetValue copy = BitsetValue.of(set.octets());
        copy = set.isComplemented() ? copy.complement() : copy;
        copy.protectFromChange(set.isProtected());
        return copy;
    }

    private static Value copyTheMap(MapValue map) {
        List<Value> flattened = map.flatten();
        List<Value> members = new ArrayList<>(flattened.size());
        for (Value member : flattened) {
            members.add(makeAFreshCopy(member));
        }
        MapValue copy = MapValue.of(members);
        copy.protectFromChange(map.isProtected());
        return copy;
    }

    private static Value shareAScalar(Value value) {
        return value;
    }

    private static AnyBlockValue copyTheBlock(AnyBlockValue block) {
        List<Value> items = new ArrayList<>(block.lengthFromHere());
        for (Value item : block.remaining()) {
            items.add(makeAFreshCopy(item));
        }
        BlockStorage storage = new BlockStorage(items);
        storage.takeLineBreaksFrom(block.storage(), block.index());
        return block.wrap(storage);
    }

    private static boolean canCopyEverySeries(Value value) {
        return switch (value) {
            case AnyBlockValue block -> canCopyEveryMember(block.remaining());
            case MapValue map -> canCopyEveryMember(map.flatten());
            case BitsetValue set -> true;
            case AnyStringValue text -> true;
            case BinaryValue octets -> true;
            case IntegerValue whole -> true;
            case AnyDecimalValue fraction -> true;
            case MoneyValue money -> true;
            case CharacterValue letter -> true;
            case LogicValue truth -> true;
            case NoneValue nothing -> true;
            case UnsetValue unset -> true;
            case DateValue date -> true;
            case TimeValue time -> true;
            case PairValue pair -> true;
            case TupleValue tuple -> true;
            case AnyWordValue word -> true;
            case Datatype datatype -> true;
            default -> false;
        };
    }

    private static boolean canCopyEveryMember(List<Value> members) {
        for (Value member : members) {
            if (!canCopyEverySeries(member)) {
                return false;
            }
        }
        return true;
    }
}

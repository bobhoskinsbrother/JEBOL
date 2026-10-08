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

    static TranscodeResult reading(String name, String source) {
        if (NAMES_HOLDING_SOMETHING_UNCOPYABLE.containsKey(name)) {
            return Transcoder.transcode(source);
        }
        AnyBlockValue held = READINGS.get(name);
        if (held != null) {
            return new TranscodeResult.Success((AnyBlockValue) freshCopyOf(held));
        }
        TranscodeResult read = Transcoder.transcode(source);
        if (read.values().isEmpty()) {
            return read;
        }
        AnyBlockValue values = read.values().orElseThrow();
        if (!everySeriesCanBeCopied(values)) {
            NAMES_HOLDING_SOMETHING_UNCOPYABLE.put(name, true);
            return read;
        }
        READINGS.put(name, values);
        return new TranscodeResult.Success((AnyBlockValue) freshCopyOf(values));
    }

    private static Value freshCopyOf(Value value) {
        return switch (value) {
            case AnyBlockValue block -> copiedBlock(block);
            case AnyStringValue text -> text.holding(text.text());
            case BinaryValue octets -> new BinaryValue(
                    new BinaryStorage(octets.octetsFromHere()), 1);
            default -> aScalarWithNothingBehindItToShare(value);
        };
    }

    private static Value aScalarWithNothingBehindItToShare(Value value) {
        return value;
    }

    private static AnyBlockValue copiedBlock(AnyBlockValue block) {
        List<Value> items = new ArrayList<>(block.lengthFromHere());
        for (Value item : block.remaining()) {
            items.add(freshCopyOf(item));
        }
        BlockStorage storage = new BlockStorage(items);
        storage.takeLineBreaksFrom(block.storage(), block.index());
        return block.holding(storage);
    }

    private static boolean everySeriesCanBeCopied(Value value) {
        return switch (value) {
            case AnyBlockValue block -> block.remaining().stream()
                    .allMatch(LibrarySource::everySeriesCanBeCopied);
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
            case DatatypeValue datatype -> true;
            default -> false;
        };
    }
}

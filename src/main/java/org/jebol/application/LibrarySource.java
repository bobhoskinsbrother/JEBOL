package org.jebol.application;

import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The library files, read once instead of once an interpreter.
 *
 * <p>Rebol's own library is six hundred thousand characters and every
 * interpreter was reading all of it. That was most of the sixty milliseconds a
 * new interpreter cost, and a test suite that builds sixteen thousand of them
 * spent most of its afternoon on it. The text does not change between
 * interpreters, so neither does what it reads as.
 *
 * <p>What does change is what happens to it afterwards. A block loaded from
 * source is the block a function's body <em>is</em>, and a script that appends
 * to a literal inside one is changing that literal for good -- which is REBOL
 * behaving as it should within one interpreter and a disaster across two. So
 * nothing shared is ever handed out: every series in the cached reading is
 * copied on the way to the caller, and a reading holding a series this does
 * not know how to copy is not cached at all.
 *
 * <p>That last clause is the honest part. Refusing to cache is slower and
 * always right; guessing that some other datatype is safe to share would be
 * faster and wrong in a way no test would name.
 */
final class LibrarySource {

    private LibrarySource() {
    }

    private static final Map<String, BlockValue> READINGS = new ConcurrentHashMap<>();

    /**
     * Names whose source holds something this cannot copy, read afresh each
     * time. Recorded so the decision is made once rather than per interpreter.
     */
    private static final Map<String, Boolean> UNCACHEABLE = new ConcurrentHashMap<>();

    /**
     * What a named source reads as, as a reading nobody else holds.
     *
     * <p>The name is the cache key and the source is what to read if it is not
     * cached; the two must agree, which they do because both come from the
     * same resource.
     */
    static TranscodeResult reading(String name, String source) {
        if (UNCACHEABLE.containsKey(name)) {
            return Transcoder.transcode(source);
        }
        BlockValue held = READINGS.get(name);
        if (held != null) {
            return new TranscodeResult.Success((BlockValue) freshCopyOf(held));
        }
        TranscodeResult read = Transcoder.transcode(source);
        if (read.values().isEmpty()) {
            return read;
        }
        BlockValue values = read.values().orElseThrow();
        if (!everySeriesCanBeCopied(values)) {
            UNCACHEABLE.put(name, true);
            return read;
        }
        READINGS.put(name, values);
        return new TranscodeResult.Success((BlockValue) freshCopyOf(values));
    }

    /**
     * A value sharing no mutable storage with the one it was copied from.
     *
     * <p>Scalars -- numbers, words, characters, dates -- are records with
     * nothing behind them to share, so they come back as they were.
     */
    private static Value freshCopyOf(Value value) {
        return switch (value) {
            case BlockValue block -> copiedBlock(block);
            case StringValue text -> StringValue.of(text.text(), text.datatype());
            case BinaryValue octets -> new BinaryValue(
                    new BinaryStorage(octets.octetsFromHere()), 1);
            default -> value;
        };
    }

    private static BlockValue copiedBlock(BlockValue block) {
        List<Value> items = new ArrayList<>(block.lengthFromHere());
        for (Value item : block.remaining()) {
            items.add(freshCopyOf(item));
        }
        BlockStorage storage = new BlockStorage(items);
        storage.takeLineBreaksFrom(block.storage(), block.index());
        return new BlockValue(storage, 1, block.datatype());
    }

    /**
     * Whether every series in the reading is one {@link #freshCopyOf} knows.
     *
     * <p>Construction syntax can put a map, a bitset, a vector or an image
     * into a source, and each of those has mutable storage of its own. None
     * appears in the files JEBOL borrows today, and the day one does the file
     * drops out of the cache rather than sharing storage between every
     * interpreter in the process.
     */
    private static boolean everySeriesCanBeCopied(Value value) {
        return switch (value) {
            case BlockValue block -> block.remaining().stream()
                    .allMatch(LibrarySource::everySeriesCanBeCopied);
            case StringValue text -> true;
            case BinaryValue octets -> true;
            case IntegerValue whole -> true;
            case DecimalValue fraction -> true;
            case MoneyValue money -> true;
            case CharacterValue letter -> true;
            case LogicValue truth -> true;
            case NoneValue nothing -> true;
            case UnsetValue unset -> true;
            case DateValue date -> true;
            case TimeValue time -> true;
            case PairValue pair -> true;
            case TupleValue tuple -> true;
            case WordValue word -> true;
            case DatatypeValue datatype -> true;
            default -> false;
        };
    }
}

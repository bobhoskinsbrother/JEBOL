package org.jebol.domain.value;

import java.util.List;

/**
 * A position into block storage, reported as one of the {@code any-block!}
 * datatypes: {@code block!}, {@code paren!}, or one of the four path types.
 *
 * <p>They share a representation because they are the same thing read
 * differently. A path is a block whose items are selectors, which is why
 * {@code first 'face/color} gives you a word.
 */
public record BlockValue(BlockStorage storage, int index, Datatype datatype)
        implements SeriesValue {

    public BlockValue {
        if (storage == null) {
            throw new IllegalArgumentException("a block value needs storage");
        }
        if (!datatype.isAnyBlock()) {
            throw new IllegalArgumentException(
                    datatype.literalSpelling() + " is not an any-block! datatype");
        }
        if (index < 1 || index > storage.length() + 1) {
            throw new IllegalArgumentException(
                    "index " + index + " is outside 1.." + (storage.length() + 1));
        }
    }

    public static BlockValue block(Value... items) {
        return new BlockValue(BlockStorage.of(items), 1, Datatype.BLOCK);
    }

    public static BlockValue block(List<Value> items) {
        return new BlockValue(new BlockStorage(items), 1, Datatype.BLOCK);
    }

    public static BlockValue paren(List<Value> items) {
        return new BlockValue(new BlockStorage(items), 1, Datatype.PAREN);
    }

    public static BlockValue path(List<Value> segments, Datatype pathDatatype) {
        if (!pathDatatype.isAnyPath()) {
            throw new IllegalArgumentException(
                    pathDatatype.literalSpelling() + " is not an any-path! datatype");
        }
        return new BlockValue(new BlockStorage(segments), 1, pathDatatype);
    }

    @Override
    public int storageLength() {
        return storage.length();
    }

    @Override
    public BlockValue atIndex(int oneBasedIndex) {
        return new BlockValue(storage, oneBasedIndex, datatype);
    }

    @Override
    public BlockValue head() {
        return atIndex(1);
    }

    @Override
    public BlockValue tail() {
        return atIndex(storage.length() + 1);
    }

    /** The same storage and position, read as a different any-block! type. */
    public BlockValue as(Datatype otherDatatype) {
        return new BlockValue(storage, index, otherDatatype);
    }

    /** The item at this position. Fails at the tail, which holds nothing. */
    public Value first() {
        if (atTail()) {
            throw new IllegalStateException("nothing to read at the tail");
        }
        return storage.at(index);
    }

    /** The items from this position to the tail. */
    public List<Value> remaining() {
        return storage.snapshot().subList(index - 1, storage.length());
    }

    @Override
    public boolean sharesStorageWith(SeriesValue other) {
        return other instanceof BlockValue block && block.storage == storage;
    }

    /**
     * REBOL's {@code ==}: same datatype and the same remaining items, compared
     * in turn. Contents rather than storage, so two separately built blocks
     * holding equal items are equal; {@link #sharesStorageWith} is the
     * identity question, REBOL's {@code same?}.
     *
     * <p>A block that has been made to contain itself would recurse forever
     * here. That cannot happen yet, because only mutation can build a cycle
     * and the natives that mutate arrive in a later milestone. Recorded as an
     * open question in {@code spec/values.allium} rather than guarded against
     * speculatively.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof BlockValue block
                && block.datatype == datatype
                && block.remaining().equals(remaining());
    }

    @Override
    public int hashCode() {
        return datatype.hashCode() * 31 + remaining().hashCode();
    }

    @Override
    public String toString() {
        return datatype.literalSpelling() + "@" + index;
    }
}

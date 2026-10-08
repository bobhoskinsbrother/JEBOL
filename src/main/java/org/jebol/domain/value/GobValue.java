package org.jebol.domain.value;

import java.util.List;
import java.util.Set;

public record GobValue(GobStorage storage, int index) implements RebolSeries {

    public GobValue {
        if (storage == null) {
            throw new IllegalArgumentException("a gob value needs storage");
        }
    }

    public long positionCountedAsUnsigned() {
        return Integer.toUnsignedLong(index);
    }

    public int positionWithinThePane() {
        long counted = Integer.toUnsignedLong(index - 1) + 1;
        return (int) Math.min(Math.max(counted, 1), storage.length() + 1L);
    }

    public static GobValue empty() {
        return new GobValue(new GobStorage(), 1);
    }

    @Override
    public Datatype datatype() {
        return Datatype.GOB;
    }

    @Override
    public RebolSeries skipped(long steps) {
        return atIndex((int) (index + steps));
    }

    @Override
    public Value itemAt(int positionFromTheHead) {
        return storage.childAt(positionFromTheHead);
    }

    @Override
    public Value frontCopied(int howMany, boolean deeply, Set<Datatype> kinds) {
        throw Raised.cannotUseTheAction(this, "copy");
    }

    @Override
    public RebolSeries reversedFront(int howMany) {
        throw Raised.cannotUse(this, "reverse/part");
    }

    @Override
    public RebolSeries reversedFromHere() {
        storage.turnRound();
        return this;
    }

    @Override
    public Value picked(int oneBasedPosition) {
        return childCounted(oneBasedPosition);
    }

    @Override
    public Value pickedBy(Value selector) {
        return childCounted(selector.asPosition());
    }

    public Value childCounted(long count) {
        long at = index - 1 + count;
        return at < 1 || at > storage.length()
                ? NoneValue.none()
                : storage.childAt((int) at);
    }

    @Override
    public int storageLength() {
        return storage.length();
    }

    @Override
    public List<Value> items() {
        return storage.pane().subList(
                Math.min(index - 1, storage.length()), storage.length());
    }

    @Override
    public GobValue atIndex(int oneBasedIndex) {
        return new GobValue(storage, oneBasedIndex);
    }

    @Override
    public GobValue head() {
        return atIndex(1);
    }

    @Override
    public GobValue tail() {
        return atIndex(storage.length() + 1);
    }

    @Override
    public boolean sharesStorageWith(RebolSeries other) {
        return other instanceof GobValue gob && gob.storage == storage;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GobValue(GobStorage storage1, int index1)
                && storage1 == storage
                && index1 == index;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(storage) * 31 + index;
    }

    @Override
    public String toString() {
        return "gob " + storage.offset() + " @" + index;
    }
}

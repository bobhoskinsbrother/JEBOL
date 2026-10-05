package org.jebol.domain.value;

import java.util.List;

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

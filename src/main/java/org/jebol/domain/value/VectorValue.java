package org.jebol.domain.value;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.ToLongFunction;

public record VectorValue(VectorStorage storage, int index) implements RebolSeries {

    @Override
    public Value randomised(RandomDraw draw) {
        for (int remaining = lengthFromHere(); remaining > 1; remaining--) {
            int chosen = index + draw.below(remaining);
            int last = index + remaining - 1;
            long held = storage.at(chosen);
            storage.set(chosen, storage.at(last));
            storage.set(last, held);
        }
        return this;
    }

    @Override
    public Value pickedAtRandom(RandomDraw draw) {
        throw Raised.of(EvaluationFailure.BAD_REFINES);
    }

    @Override
    public long asRandomSeed(ToLongFunction<byte[]> checksumOfTheOctets) {
        throw Raised.of(EvaluationFailure.BAD_REFINES);
    }

    public VectorValue {
        if (storage == null) {
            throw new IllegalArgumentException("a vector value needs storage");
        }
        if (index < 1 || index > storage.length() + 1) {
            throw new IllegalArgumentException(
                    "index " + index + " is outside 1.." + (storage.length() + 1));
        }
    }

    public static VectorValue holding(VectorKind kind, long... stored) {
        return new VectorValue(VectorStorage.holding(kind, stored), 1);
    }

    @Override
    public boolean isProtected() {
        return storage.isProtected();
    }

    @Override
    public List<Value> items() {
        return remaining();
    }

    @Override
    public Value copied(boolean deeply, Set<Datatype> kinds) {
        return copyOfTheFirst(lengthFromHere());
    }

    public VectorValue copyOfTheFirst(int howMany) {
        VectorStorage made = new VectorStorage(kind(), 0);
        for (int at = 0; at < howMany; at++) {
            made.append(storage.at(index + at));
        }
        return new VectorValue(made, 1);
    }

    @Override
    public Value arithmetic(Value right, ArithmeticOperation operation) {
        return VectorMath.done(this, right, operation);
    }


    @Override
    public Value bitwise(Value right, BitwiseOperation operation) {
        refuseWhatItCannotTake(right);
        return VectorMath.done(this, right, operation);
    }

    private void refuseWhatItCannotTake(Value right) {
        if (!(right instanceof VectorValue) && !(right instanceof IntegerValue)) {
            throw Raised.cannotUse(right, "a bit operation on a vector");
        }
    }

    @Override
    public boolean equalTo(Value other, Sameness how) {
        if (!(other instanceof VectorValue theirs)) {
            return false;
        }
        if (kind().measures() != theirs.kind().measures()) {
            throw Raised.of(EvaluationFailure.NOT_SAME_TYPE,
                    kind().spelling() + " against " + theirs.kind().spelling());
        }
        return compareWith(theirs) == 0;
    }

    public VectorKind kind() {
        return storage.kind();
    }

    @Override
    public Value itemAt(int oneBasedIndex) {
        return kind().read(storage.at(oneBasedIndex));
    }

    @Override
    public Value frontCopied(int howMany, boolean deeply, Set<Datatype> kinds) {
        return copyOfTheFirst(howMany);
    }

    @Override
    public RebolSeries reversedFront(int howMany) {
        for (int at = 0; at < howMany / 2; at++) {
            int near = index + at;
            int far = index + howMany - 1 - at;
            long held = storage.at(near);
            storage.set(near, storage.at(far));
            storage.set(far, held);
        }
        return this;
    }

    public List<Value> remaining() {
        List<Value> found = new ArrayList<>();
        for (int at = index; at <= storage.length(); at++) {
            found.add(itemAt(at));
        }
        return found;
    }

    public byte[] octetsFromHere() {
        VectorKind kind = kind();
        byte[] octets = new byte[lengthFromHere() * kind.bytes()];
        int written = 0;
        for (int at = index; at <= storage.length(); at++) {
            byte[] one = kind.octetsOf(storage.at(at));
            System.arraycopy(one, 0, octets, written, one.length);
            written += one.length;
        }
        return octets;
    }

    @Override
    public Datatype datatype() {
        return Datatype.VECTOR;
    }

    @Override
    public int storageLength() {
        return storage.length();
    }

    @Override
    public VectorValue atIndex(int oneBasedIndex) {
        return new VectorValue(storage, oneBasedIndex);
    }

    @Override
    public VectorValue head() {
        return atIndex(1);
    }

    @Override
    public VectorValue tail() {
        return atIndex(storage.length() + 1);
    }

    @Override
    public boolean sharesStorageWith(RebolSeries other) {
        return other instanceof VectorValue vector && vector.storage == storage;
    }

    public int compareWith(VectorValue other) {
        if (kind().measures() != other.kind().measures()) {
            throw new IllegalArgumentException("a counting vector and a measuring one");
        }
        int shared = Math.min(lengthFromHere(), other.lengthFromHere());
        for (int step = 0; step < shared; step++) {
            int order = compareOneElement(other, step);
            if (order != 0) {
                return order;
            }
        }
        return Integer.compare(lengthFromHere(), other.lengthFromHere());
    }

    private int compareOneElement(VectorValue other, int step) {
        long mine = storage.at(index + step);
        long theirs = other.storage.at(other.index + step);
        if (kind().measures()) {
            double ours = kind().asDecimal(mine);
            double yours = other.kind().asDecimal(theirs);
            return (ours > yours ? 1 : 0) - (ours < yours ? 1 : 0);
        }
        boolean mineIsUnsigned = !kind().isSigned();
        boolean theirsIsUnsigned = !other.kind().isSigned();
        if (mineIsUnsigned == theirsIsUnsigned) {
            return mineIsUnsigned
                    ? Long.compareUnsigned(mine, theirs)
                    : Long.compare(mine, theirs);
        }
        boolean mineIsNegative = !mineIsUnsigned && mine < 0;
        boolean theirsIsNegative = !theirsIsUnsigned && theirs < 0;
        if (mineIsNegative != theirsIsNegative) {
            return mineIsNegative ? -1 : 1;
        }
        return mineIsNegative
                ? Long.compare(mine, theirs)
                : Long.compareUnsigned(mine, theirs);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof VectorValue vector)) {
            return false;
        }
        if (kind().measures() != vector.kind().measures()) {
            return false;
        }
        return compareWith(vector) == 0;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        for (int at = index; at <= storage.length(); at++) {
            hash = hash * 31 + Long.hashCode(storage.at(at));
        }
        return hash;
    }

    @Override
    public String toString() {
        return "vector!@" + index;
    }
}

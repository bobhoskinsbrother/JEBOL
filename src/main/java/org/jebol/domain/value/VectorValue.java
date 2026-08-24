package org.jebol.domain.value;

import java.util.ArrayList;
import java.util.List;

/**
 * A position into a vector's storage, written {@code #(int32! [1 2 3])}.
 *
 * <p>A vector is a series of numbers at one fixed machine width rather than a
 * series of REBOL values. What it costs is that only numbers go in it and only
 * numbers come out; what it buys is that a million of them are a million
 * machine words rather than a million boxed values, and that TO BINARY! is the
 * bytes themselves.
 */
public record VectorValue(VectorStorage storage, int index) implements SeriesValue {

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

    public VectorKind kind() {
        return storage.kind();
    }

    /** The element at a 1-based position within the whole storage. */
    public Value elementAt(int oneBasedIndex) {
        return kind().read(storage.at(oneBasedIndex));
    }

    /** Every element from this position on, as the numbers a script sees. */
    public List<Value> remaining() {
        List<Value> found = new ArrayList<>();
        for (int at = index; at <= storage.length(); at++) {
            found.add(elementAt(at));
        }
        return found;
    }

    /** The stored bytes from this position on, least significant byte first. */
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
    public boolean sharesStorageWith(SeriesValue other) {
        return other instanceof VectorValue vector && vector.storage == storage;
    }

    /**
     * Compares two vectors element by element, then by what is left over.
     *
     * <p>Widths and signedness need not match, and a narrower vector holding
     * the same numbers is equal to a wider one. What may not be mixed is
     * counting with measuring: {@code Compare_Vector} refuses that pair
     * outright rather than converting one to the other.
     */
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

    /** REBOL's {@code =}: the same remaining numbers, whatever the widths. */
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

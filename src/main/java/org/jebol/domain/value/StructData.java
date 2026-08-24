package org.jebol.domain.value;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The bytes one struct and everything nested inside it share.
 *
 * <p>A struct value is a layout, this, and an offset into it. That is the C's
 * {@code REBSTU} exactly, and it is what makes a field of struct type work:
 * {@code s/pos} hands back a struct pointing at the same bytes further along,
 * so writing through it writes into the parent. Copying by value instead
 * would make {@code s/pos/x: 22} change nothing anyone could see.
 *
 * <p>Fields declared {@code rebval!} hold a whole REBOL value rather than a
 * number, and those are kept beside the bytes under the offset they sit at.
 * The C writes the value into the bytes; a JVM cannot, and nothing in Rebol's
 * own tests reads those bytes, because a struct carrying one refuses a raw
 * binary change outright.
 */
public final class StructData {

    private final byte[] bytes;
    private final Map<Integer, Value> liveValues = new HashMap<>();

    public StructData(int size) {
        this.bytes = new byte[size];
    }

    private StructData(byte[] bytes, Map<Integer, Value> liveValues) {
        this.bytes = bytes;
        this.liveValues.putAll(liveValues);
    }

    public int size() {
        return bytes.length;
    }

    public byte[] bytesFrom(int offset, int howMany) {
        byte[] taken = new byte[howMany];
        System.arraycopy(bytes, offset, taken, 0, howMany);
        return taken;
    }

    public void write(int offset, byte[] written, int howMany) {
        System.arraycopy(written, 0, bytes, offset, howMany);
    }

    public long numberAt(int offset, VectorKind kind) {
        return kind.fromOctets(bytes, offset);
    }

    public void writeNumberAt(int offset, VectorKind kind, long stored) {
        write(offset, kind.octetsOf(stored), kind.bytes());
    }

    public Optional<Value> liveValueAt(int offset) {
        return Optional.ofNullable(liveValues.get(offset));
    }

    public void putLiveValueAt(int offset, Value held) {
        liveValues.put(offset, held);
    }

    /** What CLEAR does: zero the bytes and forget the values they stood for. */
    public void clearFrom(int offset, int howMany) {
        java.util.Arrays.fill(bytes, offset, offset + howMany, (byte) 0);
        liveValues.keySet().removeIf(at -> at >= offset && at < offset + howMany);
    }

    /** A separate copy of one span, which is what COPY on a struct gives. */
    public StructData copyOf(int offset, int howMany) {
        StructData taken = new StructData(howMany);
        taken.write(0, bytesFrom(offset, howMany), howMany);
        liveValues.forEach((at, held) -> {
            if (at >= offset && at < offset + howMany) {
                taken.liveValues.put(at - offset, held);
            }
        });
        return taken;
    }

    public StructData sameBytesAgain() {
        return new StructData(bytes.clone(), liveValues);
    }
}

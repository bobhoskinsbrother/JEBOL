package org.jebol.domain.value;

import java.util.Arrays;

public final class BitsetValue implements Value {

    private static final int BITS_PER_OCTET = 8;

    private byte[] octets;

    private boolean complemented;

    private boolean protectedFromChange;

    private BitsetValue(byte[] octets) {
        this.octets = octets;
    }

    public void protectFromChange(boolean wanted) {
        this.protectedFromChange = wanted;
    }

    public boolean isProtected() {
        return protectedFromChange;
    }

    public boolean isComplemented() {
        return complemented;
    }

    public BitsetValue duplicate() {
        return new BitsetValue(octets.clone());
    }

    public BitsetValue complemented() {
        BitsetValue turned = new BitsetValue(octets.clone());
        turned.complemented = !complemented;
        return turned;
    }

    public void addAll(BitsetValue others) {
        byte[] theirs = others.octets;
        if (theirs.length > octets.length) {
            byte[] wider = new byte[theirs.length];
            System.arraycopy(octets, 0, wider, 0, octets.length);
            octets = wider;
        }
        for (int at = 0; at < theirs.length; at++) {
            octets[at] = (byte) (octets[at] | theirs[at]);
        }
    }

    public void holdAll(BitsetValue members, boolean wanted) {
        byte[] theirs = members.octets;
        for (int code = 0; code < theirs.length * BITS_PER_OCTET; code++) {
            if (members.namesDirectly(code)) {
                hold(code, wanted);
            }
        }
    }

    public void clearAllDirectly(BitsetValue members) {
        byte[] theirs = members.octets;
        for (int code = 0; code < theirs.length * BITS_PER_OCTET; code++) {
            if (members.namesDirectly(code)) {
                clearDirectly(code);
            }
        }
    }

    public static BitsetValue of(byte[] octets) {
        return new BitsetValue(octets.clone());
    }

    public static BitsetValue ofCharacters(int... codes) {
        if (codes.length == 0) {
            return new BitsetValue(new byte[0]);
        }
        int widest = 0;
        for (int code : codes) {
            widest = Math.max(widest, code);
        }
        BitsetValue built = new BitsetValue(new byte[widest / BITS_PER_OCTET + 1]);
        for (int code : codes) {
            built.add(code);
        }
        return built;
    }

    public void add(int code) {
        int octet = code / BITS_PER_OCTET;
        if (octet >= octets.length) {
            octets = Arrays.copyOf(octets, octet + 1);
        }
        octets[octet] |= (byte) (1 << (7 - code % BITS_PER_OCTET));
    }

    public boolean holds(int code) {
        return complemented != namesDirectly(code);
    }

    public boolean holdsEitherCaseOf(int code) {
        boolean held = code >= UNICODE_FOLDING_TABLE_SIZE
                ? namesDirectly(code)
                : namesDirectly(Character.toLowerCase(code))
                        || namesDirectly(Character.toUpperCase(code));
        return complemented != held;
    }

    private static final int UNICODE_FOLDING_TABLE_SIZE = 0x2E00;

    public void hold(int code, boolean wanted) {
        if (complemented == wanted) {
            clearDirectly(code);
        } else {
            add(code);
        }
    }

    private void clearDirectly(int code) {
        int octet = code / BITS_PER_OCTET;
        if (octet < octets.length) {
            octets[octet] &= (byte) ~(1 << (7 - code % BITS_PER_OCTET));
        }
    }

    private boolean namesDirectly(int code) {
        int octet = code / BITS_PER_OCTET;
        return octet < octets.length
                && (octets[octet] & (1 << (7 - code % BITS_PER_OCTET))) != 0;
    }

    public void clear() {
        octets = new byte[0];
    }

    public byte[] octets() {
        return octets.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BitsetValue bitset
                && complemented == bitset.complemented
                && Arrays.equals(octets, bitset.octets);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(octets) * 31 + Boolean.hashCode(complemented);
    }

    @Override
    public Datatype datatype() {
        return Datatype.BITSET;
    }

    @Override
    public String toString() {
        return "bitset of " + octets.length + " octets";
    }
}

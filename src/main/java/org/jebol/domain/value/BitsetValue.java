package org.jebol.domain.value;

import org.jebol.domain.value.sets.MembersKept;

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

    @Override
    public boolean isProtected() {
        return protectedFromChange;
    }

    @Override
    public Value copied(boolean deeply, java.util.Set<Datatype> kinds) {
        return duplicate();
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

    @Override
    public Value negated() {
        return complemented();
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
    public Value asASetWith(Value other, MembersKept keeping, boolean mindingCase) {
        if (!(other instanceof BitsetValue theirs)) {
            throw Raised.cannotUse(this, "a set operation");
        }
        byte[] mine = octets;
        byte[] yours = theirs.octets;
        byte[] both = new byte[Math.max(mine.length, yours.length)];
        for (int at = 0; at < both.length; at++) {
            int ours = at < mine.length ? mine[at] & 0xFF : 0;
            int theirsHere = at < yours.length ? yours[at] & 0xFF : 0;
            both[at] = (byte) keeping.how().combinedBits(ours, theirsHere);
        }
        return BitsetValue.of(both);
    }

    @Override
    public Value bitwise(Value right, BitwiseOperation operation) {
        return BitsetValue.of(withoutTheTrailingZeros(
                octetByOctet(someBitsFrom(right), operation)));
    }

    private byte[] someBitsFrom(Value right) {
        return switch (right) {
            case BitsetValue members -> members.octets();
            case BinaryValue given -> theBytesOf(given);
            default -> throw Raised.notRelated(this, right);
        };
    }

    private byte[] theBytesOf(BinaryValue given) {
        byte[] read = new byte[given.lengthFromHere()];
        for (int at = 0; at < read.length; at++) {
            read[at] = (byte) given.storage().at(given.index() + at);
        }
        return read;
    }

    private byte[] octetByOctet(byte[] theirs, BitwiseOperation operation) {
        byte[] both = new byte[Math.max(octets.length, theirs.length)];
        for (int at = 0; at < both.length; at++) {
            long mine = at < octets.length ? octets[at] & 0xFF : 0;
            long yours = at < theirs.length ? theirs[at] & 0xFF : 0;
            both[at] = (byte) operation.onWholeElements(mine, yours);
        }
        return both;
    }

    private byte[] withoutTheTrailingZeros(byte[] combined) {
        int kept = combined.length;
        while (kept > 0 && combined[kept - 1] == 0) {
            kept--;
        }
        return Arrays.copyOf(combined, kept);
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

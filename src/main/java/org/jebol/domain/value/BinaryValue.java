package org.jebol.domain.value;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public record BinaryValue(BinaryStorage storage, int index) implements RebolSeries {

    @Override
    public boolean isProtected() {
        return storage.isProtected();
    }

    @Override
    public List<Value> items() {
        List<Value> read = new ArrayList<>(lengthFromHere());
        for (int at = 0; at < lengthFromHere(); at++) {
            read.add(IntegerValue.of(storage.at(index + at)));
        }
        return List.copyOf(read);
    }

    @Override
    public Value bitwise(Value right, BitwiseOperation operation) {
        return octetsCycledAgainstTheLonger(someOctetsFrom(right), operation);
    }

    private BinaryValue someOctetsFrom(Value right) {
        if (right instanceof BinaryValue octets) {
            return octets;
        }
        throw Raised.of(EvaluationFailure.INVALID_ARG, right);
    }

    private Value octetsCycledAgainstTheLonger(
            BinaryValue right, BitwiseOperation operation) {

        BinaryValue longer = lengthFromHere() >= right.lengthFromHere() ? this : right;
        BinaryValue shorter = longer == this ? right : this;
        int cycle = shorter.lengthFromHere();
        int[] combined = new int[longer.lengthFromHere()];
        for (int at = 0; at < combined.length; at++) {
            int theirs = cycle == 0 ? 0 : shorter.storage().at(shorter.index() + at % cycle);
            combined[at] = (int) operation.onWholeElements(
                    longer.storage().at(longer.index() + at), theirs) & 0xFF;
        }
        return BinaryValue.of(combined);
    }


    public BinaryValue {
        if (storage == null) {
            throw new IllegalArgumentException("a binary value needs storage");
        }
        if (index < 1 || index > storage.length() + 1) {
            throw new IllegalArgumentException(
                    "index " + index + " is outside 1.." + (storage.length() + 1));
        }
    }

    public static BinaryValue of(int... octets) {
        return new BinaryValue(BinaryStorage.of(octets), 1);
    }

    public String asText() {
        return new String(octetsFromHere(), StandardCharsets.UTF_8);
    }

    public byte[] octetsFromHere() {
        return octetsUpTo(storageLength() + 1);
    }

    public byte[] bytesFromHere() {
        int howMany = lengthFromHere();
        byte[] bytes = new byte[howMany];
        for (int at = 0; at < howMany; at++) {
            bytes[at] = (byte) storage.at(index + at);
        }
        return bytes;
    }

    public BinaryValue copyOfTheFirst(int howMany) {
        BinaryStorage copied = new BinaryStorage();
        for (int at = 0; at < howMany; at++) {
            copied.append(storage.at(index + at));
        }
        return new BinaryValue(copied, 1);
    }

    public String asStrictText() {
        return strictlyUtf8(octetsFromHere());
    }

    public String asStrictTextUpTo(int endIndex) {
        return strictlyUtf8(octetsUpTo(endIndex));
    }

    private byte[] octetsUpTo(int endIndex) {
        byte[] octets = new byte[Math.max(0, endIndex - index)];
        for (int at = 0; at < octets.length; at++) {
            octets[at] = (byte) storage.at(index + at);
        }
        return octets;
    }

    private static String strictlyUtf8(byte[] octets) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(octets))
                    .toString();
        } catch (CharacterCodingException notText) {
            throw Raised.of(EvaluationFailure.INVALID_CHARS,
                    "the bytes given to load are not valid UTF-8 text");
        }
    }

    @Override
    public java.util.Optional<Value> asDecimal(Datatype wanted, Conversion asking) {
        return java.util.Optional.of(inHundredths(
                wanted, Double.longBitsToDouble(bitsOfTheLastEightOctets())));
    }

    public long bitsOfTheLastEightOctets() {
        int howMany = lengthFromHere();
        long bits = 0;
        for (int at = Math.max(0, howMany - Long.BYTES); at < howMany; at++) {
            bits = (bits << 8) | (storage().at(index() + at) & 0xFFL);
        }
        return bits;
    }

    @Override
    public Datatype datatype() {
        return Datatype.BINARY;
    }

    @Override
    public int storageLength() {
        return storage.length();
    }

    @Override
    public BinaryValue atIndex(int oneBasedIndex) {
        return new BinaryValue(storage, oneBasedIndex);
    }

    @Override
    public BinaryValue head() {
        return atIndex(1);
    }

    @Override
    public BinaryValue tail() {
        return atIndex(storage.length() + 1);
    }

    @Override
    public boolean sharesStorageWith(RebolSeries other) {
        return other instanceof BinaryValue binary && binary.storage == storage;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BinaryValue binary)) {
            return false;
        }
        if (binary.lengthFromHere() != lengthFromHere()) {
            return false;
        }
        for (int offset = 0; offset < lengthFromHere(); offset++) {
            if (binary.storage.at(binary.index + offset) != storage.at(index + offset)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        for (int offset = 0; offset < lengthFromHere(); offset++) {
            hash = hash * 31 + storage.at(index + offset);
        }
        return hash;
    }

    @Override
    public String toString() {
        return "binary!@" + index;
    }
}

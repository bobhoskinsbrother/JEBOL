package org.jebol.domain.value;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToLongFunction;

public record BinaryValue(BinaryStorage storage, int index) implements RebolSeries {

    @Override
    public Value randomised(RandomDraw draw) {
        List<Integer> octets = new ArrayList<>();
        for (int at = index; at <= storageLength(); at++) {
            octets.add(storage.at(at));
        }
        draw.shuffle(octets);
        for (int at = 0; at < octets.size(); at++) {
            storage.set(index + at, octets.get(at));
        }
        return this;
    }

    @Override
    public Value pickedAtRandom(RandomDraw draw) {
        byte[] octets = octetsFromHere();
        if (octets.length == 0) {
            return NoneValue.none();
        }
        return IntegerValue.of(octets[new CharacterBoundary().drawnIn(octets, draw)] & 0xFF);
    }

    @Override
    public long asRandomSeed(ToLongFunction<byte[]> checksumOfTheOctets) {
        return checksumOfTheOctets.applyAsLong(octetsFromHere());
    }

    @Override
    public boolean isProtected() {
        return storage.isProtected();
    }

    @Override
    public Value copied(boolean deeply, Set<Datatype> kinds) {
        return copyOfTheFirst(lengthFromHere());
    }

    @Override
    public byte[] asOctets() {
        return octetsFromHere();
    }

    @Override
    public Value itemAt(int positionFromTheHead) {
        return IntegerValue.of(storage.at(positionFromTheHead));
    }

    @Override
    public Value frontCopied(int howMany, boolean deeply, Set<Datatype> kinds) {
        return copyOfTheFirst(howMany);
    }

    @Override
    public RebolSeries reversedFront(int howMany) {
        int[] front = new int[howMany];
        for (int at = 0; at < howMany; at++) {
            front[at] = storage.at(index + howMany - 1 - at);
        }
        for (int at = 0; at < howMany; at++) {
            storage.set(index + at, front[at]);
        }
        return this;
    }

    @Override
    public RebolSeries reversedFromHere() {
        List<Integer> forwards = new ArrayList<>();
        for (int at = index; at <= storageLength(); at++) {
            forwards.add(storage.at(at));
        }
        for (int at = 0; at < forwards.size(); at++) {
            storage.set(index + at, forwards.get(forwards.size() - 1 - at));
        }
        return this;
    }

    public BinaryValue swapFirstItemWith(BinaryValue there) {
        if (!atTail() && !there.atTail()) {
            int mine = storage.at(index);
            storage.set(index, there.storage.at(there.index));
            there.storage.set(there.index, mine);
        }
        return this;
    }

    @Override
    public void refuseANeedleItCannotHold(Value needle, String nativeName) {
        if (needle instanceof IntegerValue(long magnitude) && (magnitude < 0 || magnitude > 255)) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    nativeName + " on a binary wanted a byte, not " + magnitude);
        }
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

    public static BinaryValue ofBytes(byte[] bytes) {
        int[] octets = new int[bytes.length];
        for (int at = 0; at < bytes.length; at++) {
            octets[at] = bytes[at] & 0xFF;
        }
        return of(octets);
    }

    public String asText() {
        return new String(octetsFromHere(), StandardCharsets.UTF_8);
    }

    public byte[] octetsFromHere() {
        return octetsUpTo(storageLength() + 1);
    }

    @Override
    public Value trimmed(Trimming trimming) {
        trimming.refuseWhatOnlyTextServes();
        List<Integer> kept = new ArrayList<>();
        for (int at = 0; at < lengthFromHere(); at++) {
            kept.add(storage.at(index + at));
        }
        if (trimming.everywhere()) {
            kept.removeIf(octet -> octet == 0);
        } else {
            while (trimming.fromTheHead() && !kept.isEmpty() && kept.getFirst() == 0) {
                kept.removeFirst();
            }
            while (trimming.fromTheTail() && !kept.isEmpty() && kept.getLast() == 0) {
                kept.removeLast();
            }
        }
        for (int at = storageLength(); at >= index; at--) {
            storage.removeAt(at);
        }
        for (int at = kept.size(); at > 0; at--) {
            storage.insertAt(index, kept.get(at - 1));
        }
        return this;
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

    public int byteOrderMark() {
        if (startsWith(0xEF, 0xBB, 0xBF)) {
            return 8;
        }
        if (startsWith(0xFE, 0xFF)) {
            return 16;
        }
        if (startsWith(0xFF, 0xFE)) {
            return startsWith(0xFF, 0xFE, 0x00, 0x00) ? -32 : -16;
        }
        if (startsWith(0x00, 0x00, 0xFE, 0xFF)) {
            return 32;
        }
        return 0;
    }

    private boolean startsWith(int... expected) {
        if (lengthFromHere() < expected.length) {
            return false;
        }
        for (int at = 0; at < expected.length; at++) {
            if (octetAt(index + at) != expected[at]) {
                return false;
            }
        }
        return true;
    }

    public Optional<BinaryValue> theFirstMalformedUtf8() {
        int end = storageLength() + 1;
        int at = index;
        while (at < end) {
            int width = utf8SequenceWidth(octetAt(at));
            if (width > 0 && at + width <= end && continuesCorrectly(at, width)) {
                at += width;
                continue;
            }
            if (isSurrogateHalfAt(at, end) && !isLowSurrogateAt(at)
                    && isSurrogateHalfAt(at + 3, end) && isLowSurrogateAt(at + 3)) {
                at += 6;
                continue;
            }
            return Optional.of(atIndex(at));
        }
        return Optional.empty();
    }

    private int octetAt(int position) {
        return storage.at(position) & 0xFF;
    }

    private int utf8SequenceWidth(int lead) {
        if (lead < 0x80) {
            return 1;
        }
        if (lead < 0xC2 || lead > 0xF4) {
            return 0;
        }
        if (lead < 0xE0) {
            return 2;
        }
        return lead < 0xF0 ? 3 : 4;
    }

    private boolean continuesCorrectly(int at, int width) {
        int lead = octetAt(at);
        if (width == 1) {
            return true;
        }
        for (int step = 1; step < width; step++) {
            int following = octetAt(at + step);
            if (following < 0x80 || following > 0xBF) {
                return false;
            }
        }
        int second = octetAt(at + 1);
        if (lead == 0xE0 && second < 0xA0) {
            return false;
        }
        if (lead == 0xED && second > 0x9F) {
            return false;
        }
        if (lead == 0xF0 && second < 0x90) {
            return false;
        }
        return lead != 0xF4 || second <= 0x8F;
    }

    private boolean isSurrogateHalfAt(int at, int end) {
        if (at + 3 > end || octetAt(at) != 0xED) {
            return false;
        }
        int second = octetAt(at + 1);
        int third = octetAt(at + 2);
        return second >= 0xA0 && second <= 0xBF && third >= 0x80 && third <= 0xBF;
    }

    private boolean isLowSurrogateAt(int at) {
        return octetAt(at + 1) >= 0xB0;
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
    public Optional<Value> asDecimal(Datatype wanted, Conversion asking) {
        return Optional.of(inHundredths(
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

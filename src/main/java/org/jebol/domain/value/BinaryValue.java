package org.jebol.domain.value;

import org.jebol.domain.eval.BitsetActions;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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

    @Override
    public void putItemAt(int positionFromTheHead, Value item) {
        storage.set(positionFromTheHead, (int) ((IntegerValue) item).magnitude());
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
    public Optional<Value> asDecimal(AnyDecimalValue.AnyDecimalDatatype wanted, Conversion asking) {
        return Optional.of(inHundredths(
                wanted, Double.longBitsToDouble(bitsOfTheLastEightOctets())));
    }

    public String decodedAsText() {
        byte[] bytes = octetsFromHere();
        int marked = byteOrderMark();
        if (marked != 0) {
            return textBehindTheMark(bytes, marked);
        }
        CharsetDecoder strictly = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        byte[] joined = withSurrogatePairsJoined(bytes);
        ByteBuffer reading = ByteBuffer.wrap(joined);
        CharBuffer written = CharBuffer.allocate(joined.length + 1);
        CoderResult stopped = strictly.decode(reading, written, true);
        if (stopped.isError()) {
            throw Raised.of(EvaluationFailure.INVALID_UTF, BinaryValue.ofBytes(
                    Arrays.copyOfRange(joined, reading.position(), joined.length)));
        }
        strictly.flush(written);
        return written.flip().toString();
    }

    private byte[] withSurrogatePairsJoined(byte[] bytes) {
        byte[] joined = new byte[bytes.length];
        int written = 0;
        int at = 0;
        while (at < bytes.length) {
            int high = surrogateAt(bytes, at, 0xA0);
            int low = high < 0 ? -1 : surrogateAt(bytes, at + 3, 0xB0);
            if (low < 0) {
                joined[written] = bytes[at];
                written++;
                at++;
                continue;
            }
            written = fourBytesOf(joined, written,
                    0x10000 + ((high - 0xD800) << 10) + (low - 0xDC00));
            at += 6;
        }
        return Arrays.copyOf(joined, written);
    }

    private int surrogateAt(byte[] bytes, int at, int leadingHalf) {
        if (at + 2 >= bytes.length || (bytes[at] & 0xFF) != 0xED) {
            return -1;
        }
        int second = bytes[at + 1] & 0xFF;
        int third = bytes[at + 2] & 0xFF;
        if (second < leadingHalf || second >= leadingHalf + 0x10
                || third < 0x80 || third > 0xBF) {
            return -1;
        }
        return 0xD000 | (second & 0x3F) << 6 | third & 0x3F;
    }

    private int fourBytesOf(byte[] joined, int written, int codepoint) {
        joined[written] = (byte) (0xF0 | codepoint >> 18);
        joined[written + 1] = (byte) (0x80 | codepoint >> 12 & 0x3F);
        joined[written + 2] = (byte) (0x80 | codepoint >> 6 & 0x3F);
        joined[written + 3] = (byte) (0x80 | codepoint & 0x3F);
        return written + 4;
    }

    private String textBehindTheMark(byte[] bytes, int marked) {
        Charset theMarkAnnounces = switch (marked) {
            case 8 -> StandardCharsets.UTF_8;
            case 16 -> StandardCharsets.UTF_16BE;
            case -16 -> StandardCharsets.UTF_16LE;
            case 32 -> Charset.forName("UTF-32BE");
            default -> Charset.forName("UTF-32LE");
        };
        int width = Math.abs(marked) == 8 ? 3 : Math.abs(marked) / 8;
        try {
            return theMarkAnnounces.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, width, bytes.length - width))
                    .toString();
        } catch (CharacterCodingException notText) {
            throw Raised.of(EvaluationFailure.INVALID_UTF, "binary");
        }
    }

    public static final Datatype TYPE = new BinaryDatatype();

    private static final class BinaryDatatype extends PositionedSeriesDatatype {

        private static final int THE_LARGEST_OCTET = 255;

        BinaryDatatype() {
            super("binary");
        }

        @Override
        protected Value withRoomFor(int asked) {
            return new BinaryValue(new BinaryStorage(asked), 1);
        }

        @Override
        public Value as(Value value) {
            return value instanceof BinaryValue ? value : super.as(value);
        }

        @Override
        protected Value built(Conversion asking, Value from, Maker maker) {
            return switch (from) {
                case BinaryValue already -> already;
                case AnyStringValue text ->
                        BinaryValue.ofBytes(text.text().getBytes(StandardCharsets.UTF_8));
                case IntegerValue whole -> BinaryValue.ofBytes(
                        ByteBuffer.allocate(Long.BYTES).putLong(whole.magnitude()).array());
                case DecimalValue fractional -> BinaryValue.ofBytes(ByteBuffer.allocate(Long.BYTES)
                        .putLong(Double.doubleToRawLongBits(fractional.quantity())).array());
                case MoneyValue amount -> BinaryValue.ofBytes(amount.toBytes());
                case BlockValue block -> bytesOfEach(block);
                case VectorValue vector -> BinaryValue.ofBytes(vector.octetsFromHere());
                case StructValue struct -> BinaryValue.ofBytes(struct.octets());
                case TupleValue segments -> BinaryValue.ofBytes(octetsOf(segments));
                case BitsetValue members -> BinaryValue.ofBytes(new BitsetActions(members).asOctets());
                case ImageValue picture -> BinaryValue.ofBytes(picture.everyPixel());
                case CharacterValue letter -> BinaryValue.ofBytes(
                        Character.toString(letter.codepoint()).getBytes(StandardCharsets.UTF_8));
                default -> throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(from));
            };
        }

        private Value bytesOfEach(AnyBlockValue block) {
            List<Value> items = block.remaining();
            int[] octets = new int[items.size()];
            for (int at = 0; at < items.size(); at++) {
                octets[at] = anOctetIn(items.get(at));
            }
            return BinaryValue.of(octets);
        }

        private int anOctetIn(Value item) {
            if (!(item instanceof IntegerValue(long magnitude))) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, item);
            }
            if (magnitude < 0 || magnitude > THE_LARGEST_OCTET) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE, item);
            }
            return (int) magnitude;
        }

        private byte[] octetsOf(TupleValue segments) {
            byte[] octets = new byte[segments.segmentCount()];
            for (int at = 0; at < octets.length; at++) {
                octets[at] = (byte) segments.octetAt(at + 1);
            }
            return octets;
        }
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
        return TYPE;
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

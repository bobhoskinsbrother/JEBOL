package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;

public final class Bincode {

    private Bincode() {
    }

    private static int readTheWidthOf(String code) {
        return switch (baseCode(code)) {
            case "ui8", "si8" -> 1;
            case "ui16", "si16" -> 2;
            case "ui24", "si24" -> 3;
            case "ui32", "si32" -> 4;
            case "ui64", "si64" -> 8;
            default -> 0;
        };
    }

    private static int floatWidth(String code) {
        return switch (code) {
            case "float16", "f16", "f16be", "f16le" -> 2;
            case "float", "f32", "f32be", "f32le" -> 4;
            case "double", "f64", "f64be", "f64le" -> 8;
            default -> 0;
        };
    }

    private static boolean isFloatMostSignificantFirst(String code) {
        return code.endsWith("be");
    }

    private static String baseCode(String code) {
        if (code.endsWith("be") || code.endsWith("le")) {
            return code.substring(0, code.length() - 2);
        }
        return code;
    }

    private static boolean isMostSignificantFirst(String code) {
        return !code.endsWith("le");
    }

    private static boolean isSigned(String code) {
        return baseCode(code).startsWith("si");
    }

    static boolean knows(String code) {
        return readTheWidthOf(code) > 0 || floatWidth(code) > 0
                || POSITIONS.contains(baseCode(code))
                || DATA.contains(baseCode(code))
                || VARIABLE_WIDTH.contains(code)
                || code.equals("skipbits")
                || BITS.contains(code)
                || MOMENTS.contains(code)
                || SHAPES.contains(code)
                || !lengthCodeIn(code).isEmpty();
    }

    private static final List<String> POSITIONS =
            List.of("at", "atz", "index", "indexz", "skip", "length", "length?");

    private static final List<String> DATA = List.of(
            "bytes", "octal-bytes", "string-bytes",
            "pad", "align", "random-bytes", "crop");

    private static final List<String> VARIABLE_WIDTH =
            List.of("encodedu32", "encodedu64", "vint");

    private static final long WIDEST_NARROW_ONE_TAKES = 0xFFFFFFFFL;

    private static final List<String> BITS =
            List.of("ub", "sb", "fb", "bit", "not-bit");

    private static final List<String> SHAPES =
            List.of("tuple3", "tuple4", "fixed8", "fixed16", "string",
                    "bitset8", "bitset16", "bitset32");

    private static final List<String> MOMENTS =
            List.of("msdos-time", "msdos-date", "msdos-datetime",
                    "unixtime-now", "unixtime-now-le");

    private static void cropWhatHasBeenRead(Cursor cursor) {
        if (cursor.at <= 0) {
            return;
        }
        cursor.octets.subList(0, cursor.at).clear();
        cursor.cropped += cursor.at;
        cursor.at = 0;
    }

    public static final class Cursor {

        private final List<Integer> octets;
        private int at;

        private int bitsTaken;

        private int cropped;

        private Value reading = NoneValue.none();

        public Cursor(List<Integer> octets, int at) {
            this(octets, at, 0);
        }

        public Cursor(List<Integer> octets, int at, int bitsTaken) {
            this.octets = octets;
            this.at = at;
            this.bitsTaken = bitsTaken;
        }

        public List<Integer> octets() {
            return octets;
        }

        public int at() {
            return at;
        }

        public int bitsTaken() {
            return bitsTaken;
        }

        public int cropped() {
            return cropped;
        }
    }

    public record Script(List<Value> items, UnaryOperator<Value> lookedUp) {

        int size() {
            return items.size();
        }

        Value asWritten(int step) {
            return items.get(step);
        }

        Value readValueAt(int step) {
            return lookedUp.apply(items.get(step));
        }
    }

    public static void write(Cursor cursor, Script dialect,
            LongSupplier secondsSinceTheEpoch,
            BiConsumer<AnyWordValue, Value> nameTheValue) {

        for (int step = 0; step < dialect.size(); step++) {
            if (dialect.asWritten(step) instanceof SetWordValue naming) {
                nameTheValue.accept(naming, IntegerValue.of(cursor.at + 1));
                continue;
            }
            if (carriesItsOwnBytes(dialect.readValueAt(step))) {
                writeBytes(cursor, dialect.readValueAt(step));
                continue;
            }
            String code = findCodeAt(dialect, step);
            if (readTheWidthOf(code) > 0) {
                step++;
                writeWholeNumber(cursor, code,
                        writeWholeNumber(readItemAt(dialect, step, code)));
                continue;
            }
            if (floatWidth(code) > 0) {
                step++;
                writeFloat(cursor, code,
                        writeAnyNumber(readItemAt(dialect, step, code)));
                continue;
            }
            step = writeOtherThanANumber(cursor, dialect, step, code,
                    secondsSinceTheEpoch);
        }
    }

    private static boolean carriesItsOwnBytes(Value item) {
        return item instanceof BinaryValue
                || item instanceof StringValue
                || item instanceof FileValue
                || item instanceof UrlValue
                || item instanceof EmailValue;
    }

    private static int writeOtherThanANumber(Cursor cursor, Script dialect,
            int step, String code, LongSupplier secondsSinceTheEpoch) {
        if (!lengthCodeIn(code).isEmpty()) {
            writeBytesAfterTheirLength(cursor, lengthCodeIn(code),
                    readItemAt(dialect, ++step, code));
            return step;
        }
        if (MOMENTS.contains(code)) {
            return writeMoment(cursor, dialect, step, code, secondsSinceTheEpoch);
        }
        switch (baseCode(code)) {
            case "at" -> moveTo(cursor, writeWholeNumber(readItemAt(dialect, ++step, code)) - 1);
            case "atz" -> moveTo(cursor, writeWholeNumber(readItemAt(dialect, ++step, code)));
            case "bytes" -> writeBytes(cursor, readItemAt(dialect, ++step, code));
            case "pad" -> padTo(cursor,
                    alignUp(cursor.at, writeWholeNumber(readItemAt(dialect, ++step, code))));
            case "random-bytes" -> writeRandom(cursor,
                    writeWholeNumber(readItemAt(dialect, ++step, code)));
            case "encodedu32" -> writeSevenBitsByte(cursor, refuseWiderNumber(
                    writeWholeNumber(readItemAt(dialect, ++step, code))));
            case "encodedu64" -> writeSevenBitsByte(cursor,
                    writeWholeNumber(readItemAt(dialect, ++step, code)));
            case "vint" -> writeVariableNumber(cursor,
                    writeWholeNumber(readItemAt(dialect, ++step, code)));
            default -> throw refuse(dialect.asWritten(step));
        }
        return step;
    }

    public static List<Value> read(Cursor cursor, Script dialect,
            BiConsumer<AnyWordValue, Value> nameTheValue) {
        Produced read = new Produced(nameTheValue);
        for (int step = 0; step < dialect.size(); step++) {
            if (dialect.asWritten(step) instanceof SetWordValue naming) {
                read.nameNext(naming);
                continue;
            }
            if (dialect.asWritten(step) instanceof BinaryValue wanted) {
                read.add(LogicValue.of(matches(cursor, wanted)));
                continue;
            }
            String code = readCodeAt(dialect, step);
            cursor.reading = dialect.asWritten(step);
            if (readTheWidthOf(code) > 0) {
                read.add(IntegerValue.of(readWholeNumber(cursor, code)));
                continue;
            }
            if (floatWidth(code) > 0) {
                read.add(readFloat(cursor, code));
                continue;
            }
            step = readOtherThanANumber(cursor, dialect, step, code, read);
        }
        return read.values();
    }

    private static final class Produced {

        private final List<Value> values = new ArrayList<>();

        private final BiConsumer<AnyWordValue, Value> nameTheValue;

        private final List<AnyWordValue> waiting = new ArrayList<>();

        private Produced(BiConsumer<AnyWordValue, Value> nameTheValue) {
            this.nameTheValue = nameTheValue;
        }

        private void nameNext(AnyWordValue word) {
            waiting.add(word);
        }

        private void add(Value value) {
            values.add(value);
            for (AnyWordValue word : waiting) {
                nameTheValue.accept(word, value);
            }
            waiting.clear();
        }

        private List<Value> values() {
            return values;
        }
    }

    private static int readOtherThanANumber(Cursor cursor, Script dialect,
            int step, String code, Produced read) {
        if (!lengthCodeIn(code).isEmpty()) {
            read.add(readBytesAfterTheirLength(cursor, lengthCodeIn(code)));
            return step;
        }
        Value command = dialect.asWritten(step);
        switch (baseCode(code)) {
            case "at" -> moveTo(cursor,
                    readWholeNumberAfter(dialect, ++step, command) - 1);
            case "atz" -> moveTo(cursor,
                    readWholeNumberAfter(dialect, ++step, command));
            case "skip" -> moveTo(cursor,
                    cursor.at + readWholeNumberAfter(dialect, ++step, command));
            case "index" -> read.add(IntegerValue.of(cursor.at + 1));
            case "indexz" -> read.add(IntegerValue.of(cursor.at));
            case "length" -> read.add(IntegerValue.of(readLengthPrefix(cursor)));
            case "length?" -> read.add(
                    IntegerValue.of(cursor.octets.size() - (long) cursor.at));
            case "pad" -> moveTo(cursor, alignUp(cursor.at,
                    readWholeNumberAfter(dialect, ++step, command)));
            case "ub" -> read.add(IntegerValue.of(readBits(cursor,
                    (int) readWholeNumberAfter(dialect, ++step, command))));
            case "sb" -> read.add(IntegerValue.of(readSignedBits(cursor,
                    (int) readWholeNumberAfter(dialect, ++step, command))));
            case "fb" -> read.add(DecimalValue.of(readSignedBits(cursor,
                    (int) readWholeNumberAfter(dialect, ++step, command))
                    / WHOLE_FIXED_POINT_UNIT));
            case "encodedu32" -> read.add(IntegerValue.of(
                    readSevenBitsByte(cursor) & WIDEST_NARROW_ONE_TAKES));
            case "encodedu64" -> read.add(IntegerValue.of(readSevenBitsByte(cursor)));
            case "vint" -> read.add(IntegerValue.of(readVariableNumber(cursor)));
            case "skipbits" -> skipBits(cursor,
                    readWholeNumberAfter(dialect, ++step, command));
            case "bit" -> read.add(LogicValue.of(readNextBit(cursor) == 1));
            case "not-bit" -> read.add(LogicValue.of(readNextBit(cursor) == 0));
            case "align" -> alignToByte(cursor);
            case "msdos-time" -> read.add(readMsdosTime(cursor));
            case "msdos-date" -> read.add(readMsdosDate(cursor));
            case "msdos-datetime" -> read.add(readMsdosDateTime(cursor));
            case "crop" -> cropWhatHasBeenRead(cursor);
            case "tuple3" -> read.add(readTuple(cursor, 3));
            case "tuple4" -> read.add(readTuple(cursor, 4));
            case "fixed8" -> read.add(DecimalValue.of(
                    readWholeNumber(cursor, "ui16le") / (double) FIXED8_UNIT));
            case "fixed16" -> read.add(DecimalValue.of(
                    readWholeNumber(cursor, "ui32le") / WHOLE_FIXED_POINT_UNIT));
            case "string" -> read.add(StringValue.of(textUpToItsNought(cursor)));
            case "bitset8" -> read.add(readBitset(cursor, 1));
            case "bitset16" -> read.add(readBitset(cursor, 2));
            case "bitset32" -> read.add(readBitset(cursor, 4));
            case "bytes", "octal-bytes", "string-bytes" -> {
                boolean aCountFollows = step + 1 < dialect.size();
                read.add(runOfBytesRead(cursor, baseCode(code), aCountFollows
                        ? countOfBytesAt(dialect, step + 1)
                        : cursor.octets.size() - cursor.at));
                if (aCountFollows) {
                    step++;
                }
            }
            default -> throw refuse(code);
        }
        return step;
    }

    private static int countOfBytesAt(Script dialect, int step) {
        if (!(dialect.readValueAt(step) instanceof IntegerValue(long magnitude))) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, dialect.asWritten(step));
        }
        return (int) magnitude;
    }

    private static Value runOfBytesRead(Cursor cursor, String code, int wanted) {
        refuseReadPastEnd(cursor, wanted);
        int from = cursor.at;
        cursor.at += wanted;
        if (code.equals("bytes")) {
            return binaryAt(cursor, from, wanted);
        }
        StringBuilder digits = new StringBuilder();
        for (int at = from; at < from + wanted && readOctetAt(cursor, at) != 0; at++) {
            digits.appendCodePoint(readOctetAt(cursor, at));
        }
        if (code.equals("string-bytes")) {
            return StringValue.of(digits.toString());
        }
        long counted = 0;
        for (int at = 0; at < digits.length(); at++) {
            counted = counted << 3 | digits.charAt(at) - '0';
        }
        return IntegerValue.of(counted);
    }

    private static Value readBytesAfterTheirLength(Cursor cursor, String lengthCode) {
        int wanted = (int) readWholeNumber(cursor, lengthCode);
        refuseReadPastEnd(cursor, wanted);
        Value taken = binaryAt(cursor, cursor.at, wanted);
        cursor.at += wanted;
        return taken;
    }

    private static Value binaryAt(Cursor cursor, int from, int length) {
        int[] octets = new int[Math.max(0, length)];
        for (int at = 0; at < octets.length; at++) {
            octets[at] = readOctetAt(cursor, from + at);
        }
        return BinaryValue.of(octets);
    }

    private static String lengthCodeIn(String code) {
        if (!code.endsWith("bytes") || code.equals("bytes")
                || code.equals("random-bytes")) {
            return "";
        }
        String prefix = code.substring(0, code.length() - "bytes".length());
        return readTheWidthOf(prefix) > 0 ? prefix : "";
    }

    private static void refuseNumberTooWide(String code, long value) {
        String base = baseCode(code);
        long ceiling = largestValueFor(base);
        if (value > ceiling || value < smallestValueFor(base, ceiling)) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, IntegerValue.of(value));
        }
    }

    private static long largestValueFor(String base) {
        return switch (base) {
            case "ui8" -> 0xFFL;
            case "ui16" -> 0xFFFFL;
            case "ui24" -> 0xFFFFFFL;
            case "ui32" -> 0xFFFFFFFFL;
            case "si8" -> 0x7FL;
            case "si16" -> 0x7FFFL;
            case "si24" -> 0x7FFFFFL;
            case "si32" -> 0x7FFFFFFFL;
            default -> Long.MAX_VALUE;
        };
    }

    private static long smallestValueFor(String base, long ceiling) {
        return base.startsWith("si") || base.equals("ui32")
                ? -ceiling
                : Long.MIN_VALUE;
    }

    private static void writeWholeNumber(Cursor cursor, String code, long value) {
        refuseNumberTooWide(code, value);
        int width = readTheWidthOf(code);
        for (int byteAt = 0; byteAt < width; byteAt++) {
            int shift = isMostSignificantFirst(code)
                    ? (width - 1 - byteAt) * 8
                    : byteAt * 8;
            put(cursor, cursor.at + byteAt, (int) ((value >> shift) & 0xFF));
        }
        cursor.at += width;
    }

    private static void writeFloat(Cursor cursor, String code, double value) {
        int width = floatWidth(code);
        long bits = switch (width) {
            case 2 -> HalfFloats.of((float) value) & 0xFFFFL;
            case 4 -> Float.floatToIntBits((float) value) & 0xFFFFFFFFL;
            default -> Double.doubleToLongBits(value);
        };
        for (int byteAt = 0; byteAt < width; byteAt++) {
            int shift = isFloatMostSignificantFirst(code)
                    ? (width - 1 - byteAt) * 8
                    : byteAt * 8;
            put(cursor, cursor.at + byteAt, (int) ((bits >> shift) & 0xFF));
        }
        cursor.at += width;
    }

    private static Value readFloat(Cursor cursor, String code) {
        int width = floatWidth(code);
        refuseReadPastEnd(cursor, width);
        long bits = 0;
        for (int byteAt = 0; byteAt < width; byteAt++) {
            int shift = isFloatMostSignificantFirst(code)
                    ? (width - 1 - byteAt) * 8
                    : byteAt * 8;
            bits |= ((long) readOctetAt(cursor, cursor.at + byteAt)) << shift;
        }
        cursor.at += width;
        return DecimalValue.of(switch (width) {
            case 2 -> HalfFloats.asFloat((short) bits);
            case 4 -> Float.intBitsToFloat((int) bits);
            default -> Double.longBitsToDouble(bits);
        });
    }

    private static double readAnyNumberOf(Value given) {
        if (given instanceof IntegerValue(long magnitude)) {
            return magnitude;
        }
        if (given instanceof AnyDecimalValue fraction) {
            return fraction.quantity();
        }
        throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(given));
    }

    private static final double WHOLE_FIXED_POINT_UNIT = 65536.0;

    private static int readNextBit(Cursor cursor) {
        refuseReadPastEnd(cursor, 1);
        int bit = (readOctetAt(cursor, cursor.at) >> (7 - cursor.bitsTaken)) & 1;
        cursor.bitsTaken++;
        if (cursor.bitsTaken == 8) {
            cursor.bitsTaken = 0;
            cursor.at++;
        }
        return bit;
    }

    private static long readBits(Cursor cursor, int count) {
        long value = 0;
        for (int at = 0; at < count; at++) {
            value = (value << 1) | readNextBit(cursor);
        }
        return value;
    }

    private static long readSignedBits(Cursor cursor, int count) {
        long value = readBits(cursor, count);
        if (count <= 0) {
            return value;
        }
        long signBit = 1L << (count - 1);
        return (value ^ signBit) - signBit;
    }

    private static void writeSevenBitsByte(Cursor cursor, long number) {
        long left = number;
        do {
            int octet = (int) (left & 0x7F);
            left >>>= 7;
            put(cursor, cursor.at, left == 0 ? octet : octet | 0x80);
            cursor.at++;
        } while (left != 0);
    }

    private static long readSevenBitsByte(Cursor cursor) {
        long gathered = 0;
        int shift = 0;
        int octet;
        do {
            refuseReadPastEnd(cursor, 1);
            octet = readOctetAt(cursor, cursor.at);
            cursor.at++;
            gathered |= (long) (octet & 0x7F) << shift;
            shift += 7;
        } while ((octet & 0x80) != 0);
        return gathered;
    }

    private static void writeVariableNumber(Cursor cursor, long number) {
        refuseNumberWithNoVariableForm(number);
        int howManyBytes = 1;
        while (Long.compareUnsigned(number, 1L << (7 * howManyBytes)) >= 0) {
            howManyBytes++;
        }
        int[] octets = new int[howManyBytes];
        long left = number;
        for (int at = howManyBytes - 1; at > 0; at--) {
            octets[at] = (int) (left & 0xFF);
            left >>>= 8;
        }
        octets[0] = (int) (left | (0x80 >> (howManyBytes - 1)));
        for (int octet : octets) {
            put(cursor, cursor.at, octet);
            cursor.at++;
        }
    }

    private static void refuseNumberWithNoVariableForm(long number) {
        if (number < 0) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, IntegerValue.of(number));
        }
    }

    private static long readVariableNumber(Cursor cursor) {
        refuseReadPastEnd(cursor, 1);
        int first = readOctetAt(cursor, cursor.at);
        int howManyBytes = 1;
        int marker = 0x80;
        while (marker != 0 && (first & marker) == 0) {
            marker >>= 1;
            howManyBytes++;
        }
        refuseReadPastEnd(cursor, howManyBytes);
        long gathered = first & (0xFF >> howManyBytes);
        for (int at = 1; at < howManyBytes; at++) {
            gathered = gathered << 8 | readOctetAt(cursor, cursor.at + at);
        }
        cursor.at += howManyBytes;
        return gathered;
    }

    private static void skipBits(Cursor cursor, long howManyBits) {
        long wholeBytes = (howManyBits & 0xFFFFFFFFL) / 8;
        if (wholeBytes > 0) {
            refuseSkipPastEnd(cursor, wholeBytes, howManyBits);
            cursor.at += (int) wholeBytes;
        }
        for (long left = howManyBits - wholeBytes * 8; left > 0; left--) {
            readNextBit(cursor);
        }
    }

    private static void refuseSkipPastEnd(
            Cursor cursor, long wanted, long asWritten) {

        if (cursor.at + wanted > cursor.octets.size()) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    IntegerValue.of(asWritten));
        }
    }

    private static long refuseWiderNumber(long number) {
        if (number > WIDEST_NARROW_ONE_TAKES
                || number < -WIDEST_NARROW_ONE_TAKES) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, IntegerValue.of(number));
        }
        return number & WIDEST_NARROW_ONE_TAKES;
    }

    private static void alignToByte(Cursor cursor) {
        if (cursor.bitsTaken > 0) {
            cursor.bitsTaken = 0;
            cursor.at++;
        }
    }

    private static Value readMsdosTime(Cursor cursor) {
        long packed = readWholeNumber(cursor, "ui16le");
        return TimeValue.ofNanoseconds(SECOND * (
                (packed >> 11 & 0x1F) * 3600
                        + (packed >> 5 & 0x3F) * 60
                        + (packed & 0x1F) * 2));
    }

    private static final long SECOND = 1_000_000_000L;

    private static Value readMsdosDate(Cursor cursor) {
        long packed = readWholeNumber(cursor, "ui16le");
        return DateValue.of((int) (packed >> 9 & 0x7F) + MSDOS_EPOCH_YEAR,
                (int) (packed >> 5 & 0x0F), (int) (packed & 0x1F));
    }

    private static final int MSDOS_EPOCH_YEAR = 1980;

    private static Value readMsdosDateTime(Cursor cursor) {
        TimeValue clock = (TimeValue) readMsdosTime(cursor);
        DateValue day = (DateValue) readMsdosDate(cursor);
        return DateValue.of(day.year(), day.month(), day.day(), clock);
    }

    private static int writeMoment(Cursor cursor, Script dialect,
                                   int step, String code, LongSupplier secondsSinceTheEpoch) {
        switch (code) {
            case "unixtime-now" ->
                    writeWholeNumber(cursor, "ui32", secondsSinceTheEpoch.getAsLong());
            case "unixtime-now-le" ->
                    writeWholeNumber(cursor, "ui32le", secondsSinceTheEpoch.getAsLong());
            case "msdos-time" -> writeMsdosTime(cursor, readItemAt(dialect, ++step, code));
            case "msdos-date" -> writeMsdosDate(cursor, readItemAt(dialect, ++step, code));
            default -> writeMsdosDateTime(cursor, readItemAt(dialect, ++step, code));
        }
        return step;
    }

    private static void writeMsdosTime(Cursor cursor, Value given) {
        long nanoseconds = given instanceof TimeValue(long nanoseconds1)
                ? nanoseconds1
                : readInstantIn(given).timeOfDay()
                        .map(TimeValue::nanoseconds).orElse(0L);
        long seconds = nanoseconds / SECOND;
        writeWholeNumber(cursor, "ui16le",
                seconds / 3600 << 11
                        | seconds / 60 % 60 << 5
                        | seconds % 60 / 2);
    }

    private static void writeMsdosDate(Cursor cursor, Value given) {
        DateValue day = readInstantIn(given);
        writeWholeNumber(cursor, "ui16le",
                (long) Math.floorMod(day.year() - MSDOS_EPOCH_YEAR,
                        YEARS_FIELD_COUNTS) << 9
                        | (long) day.month() << 5
                        | day.day());
    }

    private static final int YEARS_FIELD_COUNTS = 128;

    private static DateValue readInstantIn(Value given) {
        if (!(given instanceof DateValue day)) {
            throw refuse(given);
        }
        return day.asStoredInUtc();
    }

    private static void writeMsdosDateTime(Cursor cursor, Value given) {
        DateValue instant = readInstantIn(given);
        writeMsdosTime(cursor, instant);
        writeMsdosDate(cursor, instant);
    }

    private static boolean matches(Cursor cursor, BinaryValue wanted) {
        byte[] expected = wanted.octetsFromHere();
        if (cursor.at + expected.length > cursor.octets.size()) {
            return false;
        }
        for (int at = 0; at < expected.length; at++) {
            if (readOctetAt(cursor, cursor.at + at) != (expected[at] & 0xFF)) {
                return false;
            }
        }
        cursor.at += expected.length;
        return true;
    }

    private static Value readTuple(Cursor cursor, int parts) {
        refuseReadPastEnd(cursor, parts);
        int[] segments = new int[parts];
        for (int at = 0; at < parts; at++) {
            segments[at] = readOctetAt(cursor, cursor.at + at);
        }
        cursor.at += parts;
        return TupleValue.of(segments);
    }

    private static final int FIXED8_UNIT = 256;

    private static String textUpToItsNought(Cursor cursor) {
        StringBuilder text = new StringBuilder();
        while (cursor.at < cursor.octets.size()
                && readOctetAt(cursor, cursor.at) != 0) {
            text.appendCodePoint(readOctetAt(cursor, cursor.at));
            cursor.at++;
        }
        if (cursor.at < cursor.octets.size()) {
            cursor.at++;
        }
        return text.toString();
    }

    private static Value readBitset(Cursor cursor, int width) {
        refuseReadPastEnd(cursor, width);
        byte[] octets = new byte[width];
        for (int at = 0; at < width; at++) {
            octets[at] = (byte) readOctetAt(cursor, cursor.at + at);
        }
        cursor.at += width;
        return BitsetValue.of(octets);
    }

    private static long readWholeNumber(Cursor cursor, String code) {
        int width = readTheWidthOf(code);
        refuseReadPastEnd(cursor, width);
        long value = 0;
        for (int byteAt = 0; byteAt < width; byteAt++) {
            int octet = readOctetAt(cursor, cursor.at + byteAt);
            int shift = isMostSignificantFirst(code)
                    ? (width - 1 - byteAt) * 8
                    : byteAt * 8;
            value |= ((long) octet) << shift;
        }
        cursor.at += width;
        return isSigned(code) ? signExtended(value, width) : value;
    }

    private static long signExtended(long value, int width) {
        long topBit = 1L << ((width * 8) - 1);
        return (value & topBit) == 0 ? value : value - (topBit << 1);
    }

    private static void writeBytes(Cursor cursor, Value given) {
        for (byte octet : readOctetsCarriedBy(given)) {
            put(cursor, cursor.at, octet & 0xFF);
            cursor.at++;
        }
    }

    private static void writeBytesAfterTheirLength(
            Cursor cursor, String lengthCode, Value given) {
        byte[] octets = readOctetsCarriedBy(given);
        writeWholeNumber(cursor, lengthCode, octets.length);
        for (byte octet : octets) {
            put(cursor, cursor.at, octet & 0xFF);
            cursor.at++;
        }
    }

    private static byte[] readOctetsCarriedBy(Value given) {
        if (given instanceof BinaryValue bytes) {
            return bytes.octetsFromHere();
        }
        if (given instanceof AnyStringValue text && carriesItsOwnBytes(given)) {
            return text.text().getBytes(StandardCharsets.UTF_8);
        }
        throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(given));
    }

    private static void writeRandom(Cursor cursor, long howMany) {
        SecureRandom source = new SecureRandom();
        byte[] drawn = new byte[(int) howMany];
        source.nextBytes(drawn);
        for (byte octet : drawn) {
            put(cursor, cursor.at, octet & 0xFF);
            cursor.at++;
        }
    }

    private static void padTo(Cursor cursor, long position) {
        while (cursor.at < position) {
            put(cursor, cursor.at, 0);
            cursor.at++;
        }
    }

    private static long alignUp(int at, long boundary) {
        return boundary <= 0 ? at : ((at + boundary - 1) / boundary) * boundary;
    }

    private static void moveTo(Cursor cursor, long position) {
        if (position < 0) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    IntegerValue.of(position));
        }
        cursor.at = (int) position;
    }

    private static void put(Cursor cursor, int at, int octet) {
        while (cursor.octets.size() <= at) {
            cursor.octets.add(0);
        }
        cursor.octets.set(at, octet);
    }

    private static int readOctetAt(Cursor cursor, int at) {
        return at < cursor.octets.size() ? cursor.octets.get(at) : 0;
    }

    private static long readLengthPrefix(Cursor cursor) {
        refuseReadPastEnd(cursor, 1);
        int first = readOctetAt(cursor, cursor.at);
        if (first <= 128) {
            cursor.at++;
            return first;
        }
        int carrying = first & 0x7F;
        refuseReadPastEnd(cursor, carrying + 1);
        long counted = 0;
        for (int at = 1; at <= carrying; at++) {
            counted = counted << 8 | readOctetAt(cursor, cursor.at + at);
        }
        cursor.at += carrying + 1;
        return counted;
    }

    private static void refuseReadPastEnd(Cursor cursor, int wanted) {
        if (wanted < 0 || cursor.at + wanted > cursor.octets.size()) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    cursor.reading instanceof NoneValue
                            ? IntegerValue.of(cursor.at + wanted)
                            : cursor.reading);
        }
    }

    private static String readCodeAt(Script dialect, int step) {
        Value item = dialect.asWritten(step);
        if (!(item instanceof AnyWordValue word) || !knows(word.canonical())) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, item);
        }
        return word.canonical();
    }

    private static String findCodeAt(Script dialect, int step) {
        Value item = dialect.readValueAt(step);
        if (!(item instanceof AnyWordValue word)) {
            throw Raised.of(EvaluationFailure.DIALECT,
                    WordValue.of("bincode"), item);
        }
        String code = word.canonical();
        if (!knows(code)) {
            throw Raised.of(EvaluationFailure.DIALECT,
                    WordValue.of("bincode"), word);
        }
        return code;
    }

    private static Value readItemAt(Script dialect, int step, String code) {
        if (step >= dialect.size()) {
            throw refuse(code);
        }
        return dialect.readValueAt(step);
    }

    private static Value readValueAfter(Script dialect, int step, Value code) {
        if (step >= dialect.size()) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, code);
        }
        return dialect.readValueAt(step);
    }

    private static long readWholeNumberAfter(
            Script dialect, int step, Value code) {

        Value given = readValueAfter(dialect, step, code);
        if (given instanceof IntegerValue(long magnitude)) {
            return magnitude;
        }
        throw Raised.of(EvaluationFailure.INVALID_SPEC, dialect.asWritten(step));
    }

    private static long writeWholeNumber(Value given) {
        if (given instanceof IntegerValue(long magnitude)) {
            return magnitude;
        }
        throw refuse(given);
    }

    private static double writeAnyNumber(Value given) {
        if (given instanceof IntegerValue || given instanceof AnyDecimalValue) {
            return readAnyNumberOf(given);
        }
        throw refuse(given);
    }

    private static Raised refuse(String code) {
        return refuse(WordValue.of(code));
    }

    private static Raised refuse(Value given) {
        return Raised.of(EvaluationFailure.DIALECT, WordValue.of("bincode"), given);
    }
}

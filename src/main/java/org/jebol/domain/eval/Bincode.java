package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;

final class Bincode {

    private Bincode() {
    }

    private static int widthOf(String code) {
        return switch (baseOf(code)) {
            case "ui8", "si8" -> 1;
            case "ui16", "si16" -> 2;
            case "ui24", "si24" -> 3;
            case "ui32", "si32" -> 4;
            case "ui64", "si64" -> 8;
            default -> 0;
        };
    }

    private static int floatWidthOf(String code) {
        return switch (code) {
            case "float16", "f16", "f16be", "f16le" -> 2;
            case "float", "f32", "f32be", "f32le" -> 4;
            case "double", "f64", "f64be", "f64le" -> 8;
            default -> 0;
        };
    }

    private static boolean floatIsMostSignificantFirst(String code) {
        return code.endsWith("be");
    }

    private static String baseOf(String code) {
        if (code.endsWith("be") || code.endsWith("le")) {
            return code.substring(0, code.length() - 2);
        }
        return code;
    }

    private static boolean mostSignificantFirst(String code) {
        return !code.endsWith("le");
    }

    private static boolean isSigned(String code) {
        return baseOf(code).startsWith("si");
    }

    static boolean knows(String code) {
        return widthOf(code) > 0 || floatWidthOf(code) > 0
                || POSITIONS.contains(baseOf(code))
                || DATA.contains(baseOf(code))
                || VARIABLE_WIDTH.contains(code)
                || code.equals("skipbits")
                || BITS.contains(code)
                || MOMENTS.contains(code)
                || SHAPES.contains(code)
                || !lengthCodeOf(code).isEmpty();
    }

    private static final List<String> POSITIONS =
            List.of("at", "atz", "index", "indexz", "skip", "length", "length?");

    private static final List<String> DATA = List.of(
            "bytes", "octal-bytes", "string-bytes",
            "pad", "align", "random-bytes", "crop");

    private static final List<String> VARIABLE_WIDTH =
            List.of("encodedu32", "encodedu64", "vint");

    private static final long WIDEST_A_NARROW_ONE_TAKES = 0xFFFFFFFFL;

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

    static final class Cursor {

        private final List<Integer> octets;
        private int at;

        private int bitsTaken;

        private int cropped;

        private Value reading = NoneValue.none();

        Cursor(List<Integer> octets, int at) {
            this(octets, at, 0);
        }

        Cursor(List<Integer> octets, int at, int bitsTaken) {
            this.octets = octets;
            this.at = at;
            this.bitsTaken = bitsTaken;
        }

        List<Integer> octets() {
            return octets;
        }

        int at() {
            return at;
        }

        int bitsTaken() {
            return bitsTaken;
        }

        int cropped() {
            return cropped;
        }
    }

    record Script(List<Value> items, UnaryOperator<Value> lookedUp) {

        int size() {
            return items.size();
        }

        Value asWritten(int step) {
            return items.get(step);
        }

        Value valueAt(int step) {
            return lookedUp.apply(items.get(step));
        }
    }

    static void write(Cursor cursor, Script dialect,
            LongSupplier secondsSinceTheEpoch,
            BiConsumer<WordValue, Value> nameTheValue) {

        for (int step = 0; step < dialect.size(); step++) {
            if (dialect.asWritten(step) instanceof WordValue naming
                    && naming.datatype() == Datatype.SET_WORD) {
                nameTheValue.accept(naming, IntegerValue.of(cursor.at + 1));
                continue;
            }
            if (carriesItsOwnBytes(dialect.valueAt(step))) {
                writeBytes(cursor, dialect.valueAt(step));
                continue;
            }
            String code = codeAt(dialect, step);
            if (widthOf(code) > 0) {
                step++;
                writeWholeNumber(cursor, code,
                        wholeNumberWritten(itemAt(dialect, step, code)));
                continue;
            }
            if (floatWidthOf(code) > 0) {
                step++;
                writeFloat(cursor, code,
                        anyNumberWritten(itemAt(dialect, step, code)));
                continue;
            }
            step = writeOtherThanANumber(cursor, dialect, step, code,
                    secondsSinceTheEpoch);
        }
    }

    private static boolean carriesItsOwnBytes(Value item) {
        return item.datatype() == Datatype.BINARY
                || item.datatype() == Datatype.STRING
                || item.datatype() == Datatype.FILE
                || item.datatype() == Datatype.URL
                || item.datatype() == Datatype.EMAIL;
    }

    private static int writeOtherThanANumber(Cursor cursor, Script dialect,
            int step, String code, LongSupplier secondsSinceTheEpoch) {
        if (!lengthCodeOf(code).isEmpty()) {
            writeBytesAfterTheirLength(cursor, lengthCodeOf(code),
                    itemAt(dialect, ++step, code));
            return step;
        }
        if (MOMENTS.contains(code)) {
            return momentWritten(cursor, dialect, step, code, secondsSinceTheEpoch);
        }
        switch (baseOf(code)) {
            case "at" -> moveTo(cursor, wholeNumberWritten(itemAt(dialect, ++step, code)) - 1);
            case "atz" -> moveTo(cursor, wholeNumberWritten(itemAt(dialect, ++step, code)));
            case "bytes" -> writeBytes(cursor, itemAt(dialect, ++step, code));
            case "pad" -> padTo(cursor,
                    alignedUp(cursor.at, wholeNumberWritten(itemAt(dialect, ++step, code))));
            case "random-bytes" -> writeRandom(cursor,
                    wholeNumberWritten(itemAt(dialect, ++step, code)));
            case "encodedu32" -> writeSevenBitsAByte(cursor, refusingAWiderNumber(
                    wholeNumberWritten(itemAt(dialect, ++step, code))));
            case "encodedu64" -> writeSevenBitsAByte(cursor,
                    wholeNumberWritten(itemAt(dialect, ++step, code)));
            case "vint" -> writeAVariableNumber(cursor,
                    wholeNumberWritten(itemAt(dialect, ++step, code)));
            default -> throw refuse(dialect.asWritten(step));
        }
        return step;
    }

    static List<Value> read(Cursor cursor, Script dialect,
            BiConsumer<WordValue, Value> nameTheValue) {
        Produced read = new Produced(nameTheValue);
        for (int step = 0; step < dialect.size(); step++) {
            if (dialect.asWritten(step) instanceof WordValue naming
                    && naming.datatype() == Datatype.SET_WORD) {
                read.willName(naming);
                continue;
            }
            if (dialect.asWritten(step) instanceof BinaryValue wanted) {
                read.add(LogicValue.of(matched(cursor, wanted)));
                continue;
            }
            String code = codeReadAt(dialect, step);
            cursor.reading = dialect.asWritten(step);
            if (widthOf(code) > 0) {
                read.add(IntegerValue.of(readWholeNumber(cursor, code)));
                continue;
            }
            if (floatWidthOf(code) > 0) {
                read.add(readFloat(cursor, code));
                continue;
            }
            step = readOtherThanANumber(cursor, dialect, step, code, read);
        }
        return read.values();
    }

    private static final class Produced {

        private final List<Value> values = new ArrayList<>();

        private final BiConsumer<WordValue, Value> nameTheValue;

        private final List<WordValue> waiting = new ArrayList<>();

        private Produced(BiConsumer<WordValue, Value> nameTheValue) {
            this.nameTheValue = nameTheValue;
        }

        private void willName(WordValue word) {
            waiting.add(word);
        }

        private void add(Value value) {
            values.add(value);
            for (WordValue word : waiting) {
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
        if (!lengthCodeOf(code).isEmpty()) {
            read.add(bytesAfterTheirLength(cursor, lengthCodeOf(code)));
            return step;
        }
        Value command = dialect.asWritten(step);
        switch (baseOf(code)) {
            case "at" -> moveTo(cursor,
                    wholeNumberReadAfter(dialect, ++step, command) - 1);
            case "atz" -> moveTo(cursor,
                    wholeNumberReadAfter(dialect, ++step, command));
            case "skip" -> moveTo(cursor,
                    cursor.at + wholeNumberReadAfter(dialect, ++step, command));
            case "index" -> read.add(IntegerValue.of(cursor.at + 1));
            case "indexz" -> read.add(IntegerValue.of(cursor.at));
            case "length" -> read.add(IntegerValue.of(lengthPrefixRead(cursor)));
            case "length?" -> read.add(
                    IntegerValue.of(cursor.octets.size() - (long) cursor.at));
            case "pad" -> moveTo(cursor, alignedUp(cursor.at,
                    wholeNumberReadAfter(dialect, ++step, command)));
            case "ub" -> read.add(IntegerValue.of(bitsRead(cursor,
                    (int) wholeNumberReadAfter(dialect, ++step, command))));
            case "sb" -> read.add(IntegerValue.of(signedBitsRead(cursor,
                    (int) wholeNumberReadAfter(dialect, ++step, command))));
            case "fb" -> read.add(DecimalValue.of(signedBitsRead(cursor,
                    (int) wholeNumberReadAfter(dialect, ++step, command))
                    / A_WHOLE_FIXED_POINT_UNIT));
            case "encodedu32" -> read.add(IntegerValue.of(
                    sevenBitsAByteRead(cursor) & WIDEST_A_NARROW_ONE_TAKES));
            case "encodedu64" -> read.add(IntegerValue.of(sevenBitsAByteRead(cursor)));
            case "vint" -> read.add(IntegerValue.of(aVariableNumberRead(cursor)));
            case "skipbits" -> skipBits(cursor,
                    wholeNumberReadAfter(dialect, ++step, command));
            case "bit" -> read.add(LogicValue.of(nextBit(cursor) == 1));
            case "not-bit" -> read.add(LogicValue.of(nextBit(cursor) == 0));
            case "align" -> alignToAByte(cursor);
            case "msdos-time" -> read.add(msdosTimeRead(cursor));
            case "msdos-date" -> read.add(msdosDateRead(cursor));
            case "msdos-datetime" -> read.add(msdosDateTimeRead(cursor));
            case "crop" -> cropWhatHasBeenRead(cursor);
            case "tuple3" -> read.add(tupleRead(cursor, 3));
            case "tuple4" -> read.add(tupleRead(cursor, 4));
            case "fixed8" -> read.add(DecimalValue.of(
                    readWholeNumber(cursor, "ui16le") / (double) A_FIXED8_UNIT));
            case "fixed16" -> read.add(DecimalValue.of(
                    readWholeNumber(cursor, "ui32le") / A_WHOLE_FIXED_POINT_UNIT));
            case "string" -> read.add(StringValue.of(textUpToItsNought(cursor)));
            case "bitset8" -> read.add(bitsetRead(cursor, 1));
            case "bitset16" -> read.add(bitsetRead(cursor, 2));
            case "bitset32" -> read.add(bitsetRead(cursor, 4));
            case "bytes", "octal-bytes", "string-bytes" -> {
                boolean aCountFollows = step + 1 < dialect.size();
                read.add(runOfBytesRead(cursor, baseOf(code), aCountFollows
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
        if (!(dialect.valueAt(step) instanceof IntegerValue(long magnitude))) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, dialect.asWritten(step));
        }
        return (int) magnitude;
    }

    private static Value runOfBytesRead(Cursor cursor, String code, int wanted) {
        refuseAReadPastTheEnd(cursor, wanted);
        int from = cursor.at;
        cursor.at += wanted;
        if (code.equals("bytes")) {
            return binaryOf(cursor, from, wanted);
        }
        StringBuilder digits = new StringBuilder();
        for (int at = from; at < from + wanted && octetAt(cursor, at) != 0; at++) {
            digits.appendCodePoint(octetAt(cursor, at));
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

    private static Value bytesAfterTheirLength(Cursor cursor, String lengthCode) {
        int wanted = (int) readWholeNumber(cursor, lengthCode);
        refuseAReadPastTheEnd(cursor, wanted);
        Value taken = binaryOf(cursor, cursor.at, wanted);
        cursor.at += wanted;
        return taken;
    }

    private static Value binaryOf(Cursor cursor, int from, int length) {
        int[] octets = new int[Math.max(0, length)];
        for (int at = 0; at < octets.length; at++) {
            octets[at] = octetAt(cursor, from + at);
        }
        return BinaryValue.of(octets);
    }

    private static String lengthCodeOf(String code) {
        if (!code.endsWith("bytes") || code.equals("bytes")
                || code.equals("random-bytes")) {
            return "";
        }
        String prefix = code.substring(0, code.length() - "bytes".length());
        return widthOf(prefix) > 0 ? prefix : "";
    }

    private static void refuseANumberTooWide(String code, long value) {
        String base = baseOf(code);
        long ceiling = mostThatFitsIn(base);
        if (value > ceiling || value < leastThatFitsIn(base, ceiling)) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, IntegerValue.of(value));
        }
    }

    private static long mostThatFitsIn(String base) {
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

    private static long leastThatFitsIn(String base, long ceiling) {
        return base.startsWith("si") || base.equals("ui32")
                ? -ceiling
                : Long.MIN_VALUE;
    }

    private static void writeWholeNumber(Cursor cursor, String code, long value) {
        refuseANumberTooWide(code, value);
        int width = widthOf(code);
        for (int byteAt = 0; byteAt < width; byteAt++) {
            int shift = mostSignificantFirst(code)
                    ? (width - 1 - byteAt) * 8
                    : byteAt * 8;
            put(cursor, cursor.at + byteAt, (int) ((value >> shift) & 0xFF));
        }
        cursor.at += width;
    }

    private static void writeFloat(Cursor cursor, String code, double value) {
        int width = floatWidthOf(code);
        long bits = switch (width) {
            case 2 -> Float.floatToFloat16((float) value) & 0xFFFFL;
            case 4 -> Float.floatToIntBits((float) value) & 0xFFFFFFFFL;
            default -> Double.doubleToLongBits(value);
        };
        for (int byteAt = 0; byteAt < width; byteAt++) {
            int shift = floatIsMostSignificantFirst(code)
                    ? (width - 1 - byteAt) * 8
                    : byteAt * 8;
            put(cursor, cursor.at + byteAt, (int) ((bits >> shift) & 0xFF));
        }
        cursor.at += width;
    }

    private static Value readFloat(Cursor cursor, String code) {
        int width = floatWidthOf(code);
        refuseAReadPastTheEnd(cursor, width);
        long bits = 0;
        for (int byteAt = 0; byteAt < width; byteAt++) {
            int shift = floatIsMostSignificantFirst(code)
                    ? (width - 1 - byteAt) * 8
                    : byteAt * 8;
            bits |= ((long) octetAt(cursor, cursor.at + byteAt)) << shift;
        }
        cursor.at += width;
        return DecimalValue.of(switch (width) {
            case 2 -> Float.float16ToFloat((short) bits);
            case 4 -> Float.intBitsToFloat((int) bits);
            default -> Double.longBitsToDouble(bits);
        });
    }

    private static double anyNumberOf(Value given) {
        if (given instanceof IntegerValue(long magnitude)) {
            return magnitude;
        }
        if (given instanceof DecimalValue fraction) {
            return fraction.quantity();
        }
        throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(given));
    }

    private static final double A_WHOLE_FIXED_POINT_UNIT = 65536.0;

    private static int nextBit(Cursor cursor) {
        refuseAReadPastTheEnd(cursor, 1);
        int bit = (octetAt(cursor, cursor.at) >> (7 - cursor.bitsTaken)) & 1;
        cursor.bitsTaken++;
        if (cursor.bitsTaken == 8) {
            cursor.bitsTaken = 0;
            cursor.at++;
        }
        return bit;
    }

    private static long bitsRead(Cursor cursor, int count) {
        long value = 0;
        for (int at = 0; at < count; at++) {
            value = (value << 1) | nextBit(cursor);
        }
        return value;
    }

    private static long signedBitsRead(Cursor cursor, int count) {
        long value = bitsRead(cursor, count);
        if (count <= 0) {
            return value;
        }
        long signBit = 1L << (count - 1);
        return (value ^ signBit) - signBit;
    }

    private static void writeSevenBitsAByte(Cursor cursor, long number) {
        long left = number;
        do {
            int octet = (int) (left & 0x7F);
            left >>>= 7;
            put(cursor, cursor.at, left == 0 ? octet : octet | 0x80);
            cursor.at++;
        } while (left != 0);
    }

    private static long sevenBitsAByteRead(Cursor cursor) {
        long gathered = 0;
        int shift = 0;
        int octet;
        do {
            refuseAReadPastTheEnd(cursor, 1);
            octet = octetAt(cursor, cursor.at);
            cursor.at++;
            gathered |= (long) (octet & 0x7F) << shift;
            shift += 7;
        } while ((octet & 0x80) != 0);
        return gathered;
    }

    private static void writeAVariableNumber(Cursor cursor, long number) {
        refuseANumberWithNoVariableForm(number);
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

    private static void refuseANumberWithNoVariableForm(long number) {
        if (number < 0) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, IntegerValue.of(number));
        }
    }

    private static long aVariableNumberRead(Cursor cursor) {
        refuseAReadPastTheEnd(cursor, 1);
        int first = octetAt(cursor, cursor.at);
        int howManyBytes = 1;
        int marker = 0x80;
        while (marker != 0 && (first & marker) == 0) {
            marker >>= 1;
            howManyBytes++;
        }
        refuseAReadPastTheEnd(cursor, howManyBytes);
        long gathered = first & (0xFF >> howManyBytes);
        for (int at = 1; at < howManyBytes; at++) {
            gathered = gathered << 8 | octetAt(cursor, cursor.at + at);
        }
        cursor.at += howManyBytes;
        return gathered;
    }

    private static void skipBits(Cursor cursor, long howManyBits) {
        long wholeBytes = (howManyBits & 0xFFFFFFFFL) / 8;
        if (wholeBytes > 0) {
            refuseASkipPastTheEnd(cursor, wholeBytes, howManyBits);
            cursor.at += (int) wholeBytes;
        }
        for (long left = howManyBits - wholeBytes * 8; left > 0; left--) {
            nextBit(cursor);
        }
    }

    private static void refuseASkipPastTheEnd(
            Cursor cursor, long wanted, long asWritten) {

        if (cursor.at + wanted > cursor.octets.size()) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    IntegerValue.of(asWritten));
        }
    }

    private static long refusingAWiderNumber(long number) {
        if (number > WIDEST_A_NARROW_ONE_TAKES
                || number < -WIDEST_A_NARROW_ONE_TAKES) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, IntegerValue.of(number));
        }
        return number & WIDEST_A_NARROW_ONE_TAKES;
    }

    private static void alignToAByte(Cursor cursor) {
        if (cursor.bitsTaken > 0) {
            cursor.bitsTaken = 0;
            cursor.at++;
        }
    }

    private static Value msdosTimeRead(Cursor cursor) {
        long packed = readWholeNumber(cursor, "ui16le");
        return TimeValue.ofNanoseconds(A_SECOND * (
                (packed >> 11 & 0x1F) * 3600
                        + (packed >> 5 & 0x3F) * 60
                        + (packed & 0x1F) * 2));
    }

    private static final long A_SECOND = 1_000_000_000L;

    private static Value msdosDateRead(Cursor cursor) {
        long packed = readWholeNumber(cursor, "ui16le");
        return DateValue.of((int) (packed >> 9 & 0x7F) + MSDOS_EPOCH_YEAR,
                (int) (packed >> 5 & 0x0F), (int) (packed & 0x1F));
    }

    private static final int MSDOS_EPOCH_YEAR = 1980;

    private static Value msdosDateTimeRead(Cursor cursor) {
        TimeValue clock = (TimeValue) msdosTimeRead(cursor);
        DateValue day = (DateValue) msdosDateRead(cursor);
        return DateValue.of(day.year(), day.month(), day.day(), clock);
    }

    private static int momentWritten(Cursor cursor, Script dialect,
            int step, String code, LongSupplier secondsSinceTheEpoch) {
        switch (code) {
            case "unixtime-now" ->
                    writeWholeNumber(cursor, "ui32", secondsSinceTheEpoch.getAsLong());
            case "unixtime-now-le" ->
                    writeWholeNumber(cursor, "ui32le", secondsSinceTheEpoch.getAsLong());
            case "msdos-time" -> msdosTimeWritten(cursor, itemAt(dialect, ++step, code));
            case "msdos-date" -> msdosDateWritten(cursor, itemAt(dialect, ++step, code));
            default -> msdosDateTimeWritten(cursor, itemAt(dialect, ++step, code));
        }
        return step;
    }

    private static void msdosTimeWritten(Cursor cursor, Value given) {
        long nanoseconds = given instanceof TimeValue(long nanoseconds1)
                ? nanoseconds1
                : theInstantIn(given).timeOfDay()
                        .map(TimeValue::nanoseconds).orElse(0L);
        long seconds = nanoseconds / A_SECOND;
        writeWholeNumber(cursor, "ui16le",
                seconds / 3600 << 11
                        | seconds / 60 % 60 << 5
                        | seconds % 60 / 2);
    }

    private static void msdosDateWritten(Cursor cursor, Value given) {
        DateValue day = theInstantIn(given);
        writeWholeNumber(cursor, "ui16le",
                (long) Math.floorMod(day.year() - MSDOS_EPOCH_YEAR,
                        YEARS_THE_FIELD_COUNTS) << 9
                        | (long) day.month() << 5
                        | day.day());
    }

    private static final int YEARS_THE_FIELD_COUNTS = 128;

    private static DateValue theInstantIn(Value given) {
        if (!(given instanceof DateValue day)) {
            throw refuse(given);
        }
        return day.asStoredInUtc();
    }

    private static void msdosDateTimeWritten(Cursor cursor, Value given) {
        DateValue instant = theInstantIn(given);
        msdosTimeWritten(cursor, instant);
        msdosDateWritten(cursor, instant);
    }

    private static boolean matched(Cursor cursor, BinaryValue wanted) {
        byte[] expected = wanted.octetsFromHere();
        if (cursor.at + expected.length > cursor.octets.size()) {
            return false;
        }
        for (int at = 0; at < expected.length; at++) {
            if (octetAt(cursor, cursor.at + at) != (expected[at] & 0xFF)) {
                return false;
            }
        }
        cursor.at += expected.length;
        return true;
    }

    private static Value tupleRead(Cursor cursor, int parts) {
        refuseAReadPastTheEnd(cursor, parts);
        int[] segments = new int[parts];
        for (int at = 0; at < parts; at++) {
            segments[at] = octetAt(cursor, cursor.at + at);
        }
        cursor.at += parts;
        return TupleValue.of(segments);
    }

    private static final int A_FIXED8_UNIT = 256;

    private static String textUpToItsNought(Cursor cursor) {
        StringBuilder text = new StringBuilder();
        while (cursor.at < cursor.octets.size()
                && octetAt(cursor, cursor.at) != 0) {
            text.appendCodePoint(octetAt(cursor, cursor.at));
            cursor.at++;
        }
        if (cursor.at < cursor.octets.size()) {
            cursor.at++;
        }
        return text.toString();
    }

    private static Value bitsetRead(Cursor cursor, int width) {
        refuseAReadPastTheEnd(cursor, width);
        byte[] octets = new byte[width];
        for (int at = 0; at < width; at++) {
            octets[at] = (byte) octetAt(cursor, cursor.at + at);
        }
        cursor.at += width;
        return BitsetValue.of(octets);
    }

    private static long readWholeNumber(Cursor cursor, String code) {
        int width = widthOf(code);
        refuseAReadPastTheEnd(cursor, width);
        long value = 0;
        for (int byteAt = 0; byteAt < width; byteAt++) {
            int octet = octetAt(cursor, cursor.at + byteAt);
            int shift = mostSignificantFirst(code)
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
        for (byte octet : octetsCarriedBy(given)) {
            put(cursor, cursor.at, octet & 0xFF);
            cursor.at++;
        }
    }

    private static void writeBytesAfterTheirLength(
            Cursor cursor, String lengthCode, Value given) {
        byte[] octets = octetsCarriedBy(given);
        writeWholeNumber(cursor, lengthCode, octets.length);
        for (byte octet : octets) {
            put(cursor, cursor.at, octet & 0xFF);
            cursor.at++;
        }
    }

    private static byte[] octetsCarriedBy(Value given) {
        if (given instanceof BinaryValue bytes) {
            return bytes.octetsFromHere();
        }
        if (given instanceof StringValue text && carriesItsOwnBytes(given)) {
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

    private static long alignedUp(int at, long boundary) {
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

    private static int octetAt(Cursor cursor, int at) {
        return at < cursor.octets.size() ? cursor.octets.get(at) : 0;
    }

    private static long lengthPrefixRead(Cursor cursor) {
        refuseAReadPastTheEnd(cursor, 1);
        int first = octetAt(cursor, cursor.at);
        if (first <= 128) {
            cursor.at++;
            return first;
        }
        int carrying = first & 0x7F;
        refuseAReadPastTheEnd(cursor, carrying + 1);
        long counted = 0;
        for (int at = 1; at <= carrying; at++) {
            counted = counted << 8 | octetAt(cursor, cursor.at + at);
        }
        cursor.at += carrying + 1;
        return counted;
    }

    private static void refuseAReadPastTheEnd(Cursor cursor, int wanted) {
        if (wanted < 0 || cursor.at + wanted > cursor.octets.size()) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    cursor.reading instanceof NoneValue
                            ? IntegerValue.of(cursor.at + wanted)
                            : cursor.reading);
        }
    }

    private static String codeReadAt(Script dialect, int step) {
        Value item = dialect.asWritten(step);
        if (!(item instanceof WordValue word) || !knows(word.canonical())) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, item);
        }
        return word.canonical();
    }

    private static String codeAt(Script dialect, int step) {
        Value item = dialect.valueAt(step);
        if (!(item instanceof WordValue word)) {
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

    private static Value itemAt(Script dialect, int step, String code) {
        if (step >= dialect.size()) {
            throw refuse(code);
        }
        return dialect.valueAt(step);
    }

    private static Value valueReadAfter(Script dialect, int step, Value code) {
        if (step >= dialect.size()) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, code);
        }
        return dialect.valueAt(step);
    }

    private static long wholeNumberReadAfter(
            Script dialect, int step, Value code) {

        Value given = valueReadAfter(dialect, step, code);
        if (given instanceof IntegerValue(long magnitude)) {
            return magnitude;
        }
        throw Raised.of(EvaluationFailure.INVALID_SPEC, dialect.asWritten(step));
    }

    private static long wholeNumberWritten(Value given) {
        if (given instanceof IntegerValue(long magnitude)) {
            return magnitude;
        }
        throw refuse(given);
    }

    private static double anyNumberWritten(Value given) {
        if (given instanceof IntegerValue || given instanceof DecimalValue) {
            return anyNumberOf(given);
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

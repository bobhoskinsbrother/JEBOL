package org.jebol.domain.eval;

import java.util.ArrayList;
import java.util.List;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

/**
 * The binary dialect: a little language for laying numbers into bytes and
 * reading them back out.
 *
 * <p>{@code u-bincode.c}. A protocol is a sequence of fields of stated widths,
 * and writing one by hand means shifting and masking at every field. The
 * dialect says the widths instead: {@code [UI8 5 UI16 300]} writes one byte
 * then two, and {@code [UI8 UI16]} reads them back. That is why
 * {@code prot-tls.reb} is written on top of it -- TLS is nothing but framed
 * fields of stated widths.
 *
 * <p>Big-endian by default, because that is the order every wire protocol
 * uses. A code may say otherwise: {@code UI16LE} is the same field the other
 * way round.
 *
 * <p>The C knows eighty-one codes and this knows the twenty-three that carry
 * whole numbers, position and raw bytes. A code outside that set raises rather
 * than being skipped, which is the one design decision here worth defending: a
 * dialect that silently ignores what it does not understand writes a message
 * of the wrong length, and the reader at the far end is left to discover it.
 */
final class Bincode {

    private Bincode() {
    }

    /** How wide each unsigned or signed code is, in bytes. */
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

    /** A code without its endian suffix. */
    private static String baseOf(String code) {
        if (code.endsWith("be") || code.endsWith("le")) {
            return code.substring(0, code.length() - 2);
        }
        return code;
    }

    /**
     * Whether a code writes its bytes most significant first.
     *
     * <p>The default, because a protocol on a wire is written that way. Only
     * an explicit LE suffix reverses it.
     */
    private static boolean mostSignificantFirst(String code) {
        return !code.endsWith("le");
    }

    private static boolean isSigned(String code) {
        return baseOf(code).startsWith("si");
    }

    /** What the dialect can do, so an unknown code can be refused by name. */
    static boolean knows(String code) {
        return widthOf(code) > 0 || POSITIONS.contains(baseOf(code))
                || DATA.contains(baseOf(code));
    }

    private static final List<String> POSITIONS =
            List.of("at", "atz", "index", "indexz", "skip", "length");

    private static final List<String> DATA =
            List.of("bytes", "pad", "align", "random-bytes");

    /**
     * Where a write or a read has got to, and the bytes it is working on.
     *
     * <p>A cursor rather than a return value, because the dialect walks a
     * block of codes and each one moves the position the next starts from.
     */
    static final class Cursor {

        private final List<Integer> octets;
        private int at;

        Cursor(List<Integer> octets, int at) {
            this.octets = octets;
            this.at = at;
        }

        List<Integer> octets() {
            return octets;
        }

        int at() {
            return at;
        }
    }

    /**
     * Runs a write dialect, laying each value into the bytes at the cursor.
     *
     * <p>A code taking a value reads the next item of the block as that
     * value; the position codes take a number and move instead.
     */
    static void write(Cursor cursor, List<Value> dialect) {
        for (int step = 0; step < dialect.size(); step++) {
            String code = codeAt(dialect, step);
            if (widthOf(code) > 0) {
                step++;
                writeWholeNumber(cursor, code,
                        wholeNumberOf(itemAt(dialect, step, code)));
                continue;
            }
            step = writeOtherThanANumber(cursor, dialect, step, code);
        }
    }

    private static int writeOtherThanANumber(
            Cursor cursor, List<Value> dialect, int step, String code) {
        switch (baseOf(code)) {
            case "at" -> moveTo(cursor, wholeNumberOf(itemAt(dialect, ++step, code)) - 1);
            case "atz" -> moveTo(cursor, wholeNumberOf(itemAt(dialect, ++step, code)));
            case "skip" -> moveTo(cursor,
                    cursor.at + wholeNumberOf(itemAt(dialect, ++step, code)));
            case "bytes" -> writeBytes(cursor, itemAt(dialect, ++step, code));
            case "pad" -> padTo(cursor, wholeNumberOf(itemAt(dialect, ++step, code)));
            case "align" -> padTo(cursor, alignedUp(cursor.at,
                    wholeNumberOf(itemAt(dialect, ++step, code))));
            case "random-bytes" -> writeRandom(cursor,
                    wholeNumberOf(itemAt(dialect, ++step, code)));
            default -> throw refuse(code);
        }
        return step;
    }

    /**
     * Runs a read dialect, answering one value per code that produces one.
     *
     * <p>The position codes produce nothing, which is why the answer is
     * gathered rather than being one value per code.
     */
    static List<Long> read(Cursor cursor, List<Value> dialect) {
        List<Long> read = new ArrayList<>();
        for (int step = 0; step < dialect.size(); step++) {
            String code = codeAt(dialect, step);
            if (widthOf(code) > 0) {
                read.add(readWholeNumber(cursor, code));
                continue;
            }
            step = readOtherThanANumber(cursor, dialect, step, code, read);
        }
        return read;
    }

    private static int readOtherThanANumber(Cursor cursor, List<Value> dialect,
            int step, String code, List<Long> read) {
        switch (baseOf(code)) {
            case "at" -> moveTo(cursor, wholeNumberOf(itemAt(dialect, ++step, code)) - 1);
            case "atz" -> moveTo(cursor, wholeNumberOf(itemAt(dialect, ++step, code)));
            case "skip" -> moveTo(cursor,
                    cursor.at + wholeNumberOf(itemAt(dialect, ++step, code)));
            case "index" -> read.add((long) cursor.at + 1);
            case "indexz" -> read.add((long) cursor.at);
            case "length" -> read.add((long) cursor.octets.size() - cursor.at);
            default -> throw refuse(code);
        }
        return step;
    }

    private static void writeWholeNumber(Cursor cursor, String code, long value) {
        int width = widthOf(code);
        for (int byteAt = 0; byteAt < width; byteAt++) {
            int shift = mostSignificantFirst(code)
                    ? (width - 1 - byteAt) * 8
                    : byteAt * 8;
            put(cursor, cursor.at + byteAt, (int) ((value >> shift) & 0xFF));
        }
        cursor.at += width;
    }

    private static long readWholeNumber(Cursor cursor, String code) {
        int width = widthOf(code);
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

    /**
     * A signed field read back as a negative number when its top bit is set.
     *
     * <p>Without this an SI8 of -1 reads as 255, which is the same eight bits
     * and a different number.
     */
    private static long signExtended(long value, int width) {
        long topBit = 1L << ((width * 8) - 1);
        return (value & topBit) == 0 ? value : value - (topBit << 1);
    }

    private static void writeBytes(Cursor cursor, Value given) {
        if (!(given instanceof BinaryValue bytes)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(given));
        }
        for (byte octet : bytes.octetsFromHere()) {
            put(cursor, cursor.at, octet & 0xFF);
            cursor.at++;
        }
    }

    private static void writeRandom(Cursor cursor, long howMany) {
        java.security.SecureRandom source = new java.security.SecureRandom();
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
        cursor.at = (int) Math.max(0, position);
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

    private static String codeAt(List<Value> dialect, int step) {
        Value item = dialect.get(step);
        if (!(item instanceof WordValue word)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(item));
        }
        String code = word.canonical();
        if (!knows(code)) {
            throw refuse(code);
        }
        return code;
    }

    private static Value itemAt(List<Value> dialect, int step, String code) {
        if (step >= dialect.size()) {
            throw Raised.of(EvaluationFailure.MISSING_ARG, code);
        }
        return dialect.get(step);
    }

    private static long wholeNumberOf(Value given) {
        if (given instanceof IntegerValue number) {
            return number.magnitude();
        }
        throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(given));
    }

    /**
     * A code this dialect has not got, refused by name.
     *
     * <p>Rather than skipped. A dialect that ignores what it does not
     * understand writes a message of the wrong length and leaves the reader
     * at the far end to discover it, which is the worst way for a protocol to
     * fail.
     */
    private static Raised refuse(String code) {
        return Raised.of(EvaluationFailure.FEATURE_NA,
                "the binary dialect code " + code);
    }
}

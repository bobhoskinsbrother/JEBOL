package org.jebol.domain.eval;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.StringValue;
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
                || DATA.contains(baseOf(code))
                || !lengthCodeOf(code).isEmpty();
    }

    private static final List<String> POSITIONS =
            List.of("at", "atz", "index", "indexz", "skip", "length", "length?");

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
            if (carriesItsOwnBytes(dialect.get(step))) {
                writeBytes(cursor, dialect.get(step));
                continue;
            }
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

    /**
     * Whether a value laid in the dialect on its own means its own bytes.
     *
     * <p>Five types do, and the C lists them together in one fall-through:
     * {@code REB_BINARY}, {@code REB_STRING}, {@code REB_FILE}, {@code REB_URL}
     * and {@code REB_EMAIL}, all reaching one {@code memcpy}. So
     * {@code binary/write b [ui8 1 #{FFFF} ui8 2]} puts the two bytes between
     * the two, which is how a protocol writes a payload it already holds
     * without naming a width for it.
     *
     * <p>A tag and an issue are not among them although they are strings, and
     * neither is a char or a number. Reading the list as "any string" would
     * take four types the C refuses.
     */
    private static boolean carriesItsOwnBytes(Value item) {
        return item.datatype() == Datatype.BINARY
                || item.datatype() == Datatype.STRING
                || item.datatype() == Datatype.FILE
                || item.datatype() == Datatype.URL
                || item.datatype() == Datatype.EMAIL;
    }

    private static int writeOtherThanANumber(
            Cursor cursor, List<Value> dialect, int step, String code) {
        if (!lengthCodeOf(code).isEmpty()) {
            writeBytesAfterTheirLength(cursor, lengthCodeOf(code),
                    itemAt(dialect, ++step, code));
            return step;
        }
        switch (baseOf(code)) {
            case "at" -> moveTo(cursor, wholeNumberOf(itemAt(dialect, ++step, code)) - 1);
            case "atz" -> moveTo(cursor, wholeNumberOf(itemAt(dialect, ++step, code)));
            case "bytes" -> writeBytes(cursor, itemAt(dialect, ++step, code));
            case "pad" -> padTo(cursor,
                    alignedUp(cursor.at, wholeNumberOf(itemAt(dialect, ++step, code))));
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
    static List<Value> read(Cursor cursor, List<Value> dialect,
            BiConsumer<WordValue, Value> named) {
        Produced read = new Produced(named);
        for (int step = 0; step < dialect.size(); step++) {
            if (dialect.get(step) instanceof WordValue naming
                    && naming.datatype() == Datatype.SET_WORD) {
                read.willName(naming);
                continue;
            }
            String code = codeAt(dialect, step);
            if (widthOf(code) > 0) {
                read.add(IntegerValue.of(readWholeNumber(cursor, code)));
                continue;
            }
            step = readOtherThanANumber(cursor, dialect, step, code, read);
        }
        return read.values();
    }

    /**
     * What a read has produced, and the word waiting for the next one.
     *
     * <p>A set-word in the read dialect takes the next value the read
     * produces, which is not the same as the next code: {@code [x: AT 1 UI8]}
     * puts the byte in {@code x}, because AT moves the cursor and produces
     * nothing to take. A set-word with nothing produced after it leaves its
     * word exactly as it was.
     *
     * <p>The value goes into the answer as well as into the word. The
     * set-word is a tap on the way past rather than a diversion, which is what
     * lets a caller read a length into a word and keep reading in the same
     * call -- the shape every length-prefixed protocol wants.
     */
    private static final class Produced {

        private final List<Value> values = new ArrayList<>();

        private final BiConsumer<WordValue, Value> named;

        private WordValue waiting;

        private Produced(BiConsumer<WordValue, Value> named) {
            this.named = named;
        }

        private void willName(WordValue word) {
            waiting = word;
        }

        private void add(Value value) {
            values.add(value);
            if (waiting != null) {
                named.accept(waiting, value);
                waiting = null;
            }
        }

        private List<Value> values() {
            return values;
        }
    }

    private static int readOtherThanANumber(Cursor cursor, List<Value> dialect,
            int step, String code, Produced read) {
        if (!lengthCodeOf(code).isEmpty()) {
            read.add(bytesAfterTheirLength(cursor, lengthCodeOf(code)));
            return step;
        }
        Value named = dialect.get(step);
        switch (baseOf(code)) {
            case "at" -> moveTo(cursor,
                    wholeNumberOf(valueReadAfter(dialect, ++step, named)) - 1);
            case "atz" -> moveTo(cursor,
                    wholeNumberOf(valueReadAfter(dialect, ++step, named)));
            case "skip" -> moveTo(cursor,
                    cursor.at + wholeNumberOf(valueReadAfter(dialect, ++step, named)));
            case "index" -> read.add(IntegerValue.of(cursor.at + 1));
            case "indexz" -> read.add(IntegerValue.of(cursor.at));
            case "length", "length?" -> read.add(
                    IntegerValue.of(cursor.octets.size() - (long) cursor.at));
            case "pad" -> moveTo(cursor, alignedUp(cursor.at,
                    wholeNumberOf(valueReadAfter(dialect, ++step, named))));
            case "bytes" -> read.add(bytesToTheEnd(cursor));
            default -> throw refuse(code);
        }
        return step;
    }

    /**
     * Everything left, which is what a bare BYTES reads.
     *
     * <p>No length to give it, so it takes the rest: {@code binary/read b
     * 'bytes} after a run of writes is how a caller gets back what they wrote
     * without counting it.
     *
     * <p>And it consumes them. Reading twice running gives the bytes and then
     * nothing, not the same bytes twice, because the read cursor ends up at
     * the tail like every other read leaves it past what it took.
     */
    private static Value bytesToTheEnd(Cursor cursor) {
        Value taken = binaryOf(cursor, cursor.at, cursor.octets.size() - cursor.at);
        cursor.at = cursor.octets.size();
        return taken;
    }

    /**
     * A run of bytes with its own length written in front of it.
     *
     * <p>{@code UI8BYTES} is one byte of length then that many bytes,
     * {@code UI16LEBYTES} is a little-endian pair then the bytes, and so on
     * through the four widths and both orders. A length-prefixed field is the
     * commonest shape in a binary protocol, which is why the C spells out ten
     * of these rather than making a caller write the length itself.
     */
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

    /**
     * The width code in front of a length-prefixed BYTES code, if it is one.
     *
     * <p>{@code ui16lebytes} is {@code ui16le} and then the bytes it counts.
     * Plain {@code bytes} has no length in front of it and neither does
     * {@code random-bytes}, so both answer nothing and are handled elsewhere.
     */
    private static String lengthCodeOf(String code) {
        if (!code.endsWith("bytes") || code.equals("bytes")
                || code.equals("random-bytes")) {
            return "";
        }
        String prefix = code.substring(0, code.length() - "bytes".length());
        return widthOf(prefix) > 0 ? prefix : "";
    }

    /**
     * Refuses a number too big for the field it was going to be written into.
     *
     * <p>A field of a stated width silently losing its top bits is the worst
     * failure a protocol can have, because the message goes out well-formed
     * and wrong. So the C checks before writing, and the bounds are not the
     * ones two's complement would suggest.
     *
     * <p>A signed field is symmetric: {@code ASSERT_SI_RANGE(next, 0x7F)}
     * refuses anything outside -127 to 127, so {@code SI8 -128} is an error
     * although a byte holds it. Rebol's own suite asserts exactly that, for
     * all four widths.
     *
     * <p>An unsigned field only has a ceiling. {@code ASSERT_UI_RANGE} is a
     * signed comparison against the maximum, so a negative passes straight
     * through and is written as its two's complement -- which is why
     * {@code UI8 -1} is 255 rather than a refusal.
     *
     * <p>The 64-bit codes are checked by neither, having no room left to
     * overflow into.
     */
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

    /**
     * The least a field may hold, which the two families disagree about.
     *
     * <p>A signed field is symmetric about nought. The narrow unsigned fields
     * have no floor at all, because their check is a signed comparison against
     * the ceiling and nothing else, so any negative is taken and written as
     * its two's complement. Only {@code UI32} has one, from the second half of
     * {@code ASSERT_U32_RANGE}, and it is the mirror of its ceiling rather
     * than anything a 32-bit word would suggest.
     */
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
        for (byte octet : octetsCarriedBy(given)) {
            put(cursor, cursor.at, octet & 0xFF);
            cursor.at++;
        }
    }

    /**
     * Writes how many bytes are coming, then the bytes.
     *
     * <p>The length goes through the same width code that will read it back,
     * so the pair stays in step: {@code UI8BYTES #{CAFE}} is {@code #{02CAFE}}
     * and {@code UI16LEBYTES #{CAFE}} is {@code #{0200CAFE}}.
     */
    private static void writeBytesAfterTheirLength(
            Cursor cursor, String lengthCode, Value given) {
        byte[] octets = octetsCarriedBy(given);
        writeWholeNumber(cursor, lengthCode, octets.length);
        for (byte octet : octets) {
            put(cursor, cursor.at, octet & 0xFF);
            cursor.at++;
        }
    }

    /** The bytes a binary holds, or a string's as UTF-8. */
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

    /**
     * Moves the cursor, refusing to put it before the head.
     *
     * <p>{@code ASSERT_INDEX_RANGE} raises {@code out-of-range} rather than
     * settling for the head, and the distinction matters because AT counts
     * from one: {@code AT 0} is a caller who has muddled the two conventions,
     * and clamping it to the head writes their bytes in the wrong place
     * without saying so. Past the end is not an error -- the series grows to
     * meet it.
     */
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

    /**
     * Refuses a read that would run off the end of the bytes.
     *
     * <p>{@code ASSERT_READ_SIZE} raises {@code out-of-range} rather than
     * padding with noughts, because a field that is not all there is not the
     * number it would look like: a truncated message must be a failure at the
     * point of reading, not a small number the caller then acts on.
     *
     * <p>Reading up to the tail exactly is fine, and leaves the cursor there.
     * Past it was a raw Java exception escaping the interpreter, which is the
     * one thing a script can neither catch nor see coming.
     *
     * <p>Checked on the length-prefixed runs as well as the numbers, which is
     * a place a real 3.22.1 does not check and where it therefore reads
     * whatever the allocator left after the tail:
     * {@code binary/read #\{02CA} 'UI8BYTES} answers {@code #\{CA00}} there
     * and raises here. The C's own {@code ep} is the tail rather than the
     * capacity, so the check is the intent and the missing one is the slip --
     * and a byte that was never in the message is worse than an error.
     */
    private static void refuseAReadPastTheEnd(Cursor cursor, int wanted) {
        if (cursor.at + wanted > cursor.octets.size()) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    IntegerValue.of(cursor.at + wanted));
        }
    }

    /**
     * The code named at this step, which has to be a word by then.
     *
     * <p>Anything else is the dialect being misused rather than a bad
     * argument, and the C says which: {@code Trap_Word(RE_DIALECT,
     * SYM_BINCODE, value)}, whose message reads "incorrect bincode dialect
     * usage at:". A char, a number, a tag or a block laid in on its own is
     * this error, where a binary or a string would have been its own bytes.
     */
    private static String codeAt(List<Value> dialect, int step) {
        Value item = dialect.get(step);
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

    /**
     * The value a code takes, which has to be there.
     *
     * <p>A code with nothing after it is the dialect being misused rather
     * than a call missing an argument: BINARY got all the arguments it
     * declares, and it is the block that stops short. So {@code [UI8]} is
     * {@code dialect}, the same error as a code that means nothing, which is
     * the C's {@code error_next_value} landing in the same place.
     */
    private static Value itemAt(List<Value> dialect, int step, String code) {
        if (step >= dialect.size()) {
            throw refuse(code);
        }
        return dialect.get(step);
    }

    /**
     * The value a read code takes, refused differently from a write's.
     *
     * <p>The two sides land in different places in the C. A write that runs
     * out of block reaches {@code goto error} and comes back as
     * {@code dialect}; a read reaches {@code error_next_value} and comes back
     * as {@code invalid-spec} naming the code that was left hanging. Rebol's
     * own suite pins the read side three times over, checking that
     * {@code e/arg1} is the word itself -- {@code AT}, {@code ATz},
     * {@code SKIP}, each as it was written rather than folded.
     *
     * <p>One error for both looked tidier and made those three fail.
     */
    private static Value valueReadAfter(List<Value> dialect, int step, Value code) {
        if (step >= dialect.size()) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, code);
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
     *
     * <p>The same error as a value it has no meaning for, because the C makes
     * no distinction: an unknown code falls off the end of its switch to the
     * same {@code goto error} a bad type reaches, and both come out as
     * {@code dialect}. Answering {@code feature-na} instead said JEBOL had not
     * got round to the code, where the truth is that no REBOL has it.
     */
    private static Raised refuse(String code) {
        return Raised.of(EvaluationFailure.DIALECT,
                WordValue.of("bincode"), WordValue.of(code));
    }
}
